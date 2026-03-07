package com.gigassist.core.capture

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.gigassist.R
import com.gigassist.core.calculator.TripEvaluator
import com.gigassist.core.parser.DiDiTripOfferParser
import com.gigassist.core.parser.UberTripOfferParser
import com.gigassist.domain.model.DistanceUnit
import com.gigassist.domain.model.DriverSettings
import com.gigassist.domain.model.TripEvaluationUiModel
import com.gigassist.domain.model.TripEvaluatorInput
import com.gigassist.domain.model.TripOfferRawData
import com.gigassist.domain.model.TripRecord
import com.gigassist.domain.repository.SettingsRepository
import com.gigassist.domain.repository.ShiftRepository
import com.gigassist.domain.repository.TripRepository
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Servicio de captura de pantalla usando MediaProjection + ML Kit OCR.
 *
 * Reemplaza al AccessibilityService que no podía leer el popup
 * de ofertas de Uber (React Native canvas + MIUI bloquea eventos).
 *
 * Cada 500ms captura la pantalla y ejecuta OCR on-device.
 * Si detecta texto de oferta de Uber o DiDi, lo parsea y evalúa.
 */
class ScreenCaptureService : Service() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface CaptureEntryPoint {
        fun tripEvaluator(): TripEvaluator
        fun settingsRepository(): SettingsRepository
        fun tripRepository(): TripRepository
        fun shiftRepository(): ShiftRepository
    }

    private lateinit var tripEvaluator: TripEvaluator
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var tripRepository: TripRepository
    private lateinit var shiftRepository: ShiftRepository

    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private val handler = Handler(Looper.getMainLooper())
    private val textRecognizer = TextRecognition.getClient(
        TextRecognizerOptions.DEFAULT_OPTIONS
    )
    private var lastText = ""
    private var lastProcessedTime = 0L
    private var lastProcessedTrip: com.gigassist.domain.model.TripOfferRawData? = null
    private var lastTripProcessedTime = 0L
    private var isCapturing = false

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val uberParser = UberTripOfferParser()
    private val didiParser = DiDiTripOfferParser()

    private val _evaluationFlow = MutableSharedFlow<TripEvaluationUiModel>(replay = 1)
    val evaluationFlow: SharedFlow<TripEvaluationUiModel> = _evaluationFlow.asSharedFlow()

    companion object {
        private const val TAG = "GigCapture"
        private const val CHANNEL_ID = "gig_capture_channel"
        private const val NOTIFICATION_ID = 1001
        private const val CAPTURE_INTERVAL_MS = 500L
        private const val DEBOUNCE_MS = 2000L
        private const val TRIP_NOTIFICATION_ID = 1002
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate — inicializando Hilt")
        try {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                CaptureEntryPoint::class.java
            )
            tripEvaluator = entryPoint.tripEvaluator()
            settingsRepository = entryPoint.settingsRepository()
            tripRepository = entryPoint.tripRepository()
            shiftRepository = entryPoint.shiftRepository()
            Log.d(TAG, "✅ Dependencias inyectadas")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error inyectando dependencias", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("proj_code", Activity.RESULT_CANCELED)
            ?: return START_NOT_STICKY

        @Suppress("DEPRECATION")
        val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("proj_data", Intent::class.java)
        } else {
            intent.getParcelableExtra("proj_data")
        }

        if (data == null || resultCode != Activity.RESULT_OK) {
            Log.e(TAG, "❌ No se recibieron datos de MediaProjection")
            stopSelf()
            return START_NOT_STICKY
        }

        // Crear notificación para foreground service
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        // Iniciar MediaProjection
        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mgr.getMediaProjection(resultCode, data)

        // Android 14+ REQUIERE registrar callback ANTES de createVirtualDisplay
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.d(TAG, "MediaProjection detenida por el sistema")
                    isCapturing = false
                    handler.removeCallbacksAndMessages(null)
                    virtualDisplay?.release()
                    imageReader?.close()
                }
            }, handler)
        }

        val metrics = resources.displayMetrics
        imageReader = ImageReader.newInstance(
            metrics.widthPixels,
            metrics.heightPixels,
            PixelFormat.RGBA_8888,
            2
        )

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "GigCapture",
            metrics.widthPixels,
            metrics.heightPixels,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null, null
        )

        isCapturing = true
        scheduleCapture()
        Log.d(TAG, "✅ Captura iniciada — ${metrics.widthPixels}x${metrics.heightPixels}")

        return START_STICKY
    }

    private fun scheduleCapture() {
        if (!isCapturing) return
        handler.postDelayed({
            captureAndProcess()
            scheduleCapture()
        }, CAPTURE_INTERVAL_MS)
    }

    private fun captureAndProcess() {
        val image = imageReader?.acquireLatestImage() ?: return
        val bitmap = try {
            imageToBitmap(image)
        } catch (e: Exception) {
            Log.e(TAG, "Error convirtiendo imagen", e)
            image.close()
            return
        }
        image.close()

        val input = InputImage.fromBitmap(bitmap, 0)
        textRecognizer.process(input)
            .addOnSuccessListener { visionText ->
                val text = visionText.text.trim()
                bitmap.recycle()

                // Ignorar texto muy corto o idéntico al anterior
                if (text.length < 15) return@addOnSuccessListener
                if (text == lastText) return@addOnSuccessListener

                val now = System.currentTimeMillis()
                if (now - lastProcessedTime < DEBOUNCE_MS) return@addOnSuccessListener
                lastText = text
                lastProcessedTime = now

                Log.d(TAG, "=== OCR TEXT ===\n${text.take(300)}")

                processText(text)
            }
            .addOnFailureListener { e ->
                bitmap.recycle()
                Log.e(TAG, "Error OCR", e)
            }
    }

    private fun processText(text: String) {
        // Intentar primero con el parser de Uber
        val uberOffer = uberParser.parse(text)
        if (uberOffer != null) {
            Log.d(TAG, "🎉 UBER OFERTA VÍA OCR: fare=${uberOffer.fare}, dist=${uberOffer.distanceKm}, dur=${uberOffer.durationMin}")
            serviceScope.launch { processTrip(uberOffer, "Uber") }
            return
        }

        // Intentar con el parser de DiDi
        val didiOffer = didiParser.parse(text)
        if (didiOffer != null) {
            Log.d(TAG, "🎉 DIDI OFERTA VÍA OCR: fare=${didiOffer.fare}, dist=${didiOffer.distanceKm}, dur=${didiOffer.durationMin}")
            serviceScope.launch { processTrip(didiOffer, "DiDi") }
            return
        }
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width
        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        return bitmap
    }

    private suspend fun processTrip(rawData: TripOfferRawData, platform: String) {
        val now = System.currentTimeMillis()
        if (lastProcessedTrip != null &&
            lastProcessedTrip?.fare == rawData.fare &&
            lastProcessedTrip?.distanceKm == rawData.distanceKm &&
            lastProcessedTrip?.durationMin == rawData.durationMin &&
            now - lastTripProcessedTime < 45000L
        ) {
            // Ignorar duplicados leídos en múltiples cuadros
            Timber.d("Ignorando viaje duplicado ($platform): fare=${rawData.fare}")
            return
        }

        lastProcessedTrip = rawData
        lastTripProcessedTime = now

        try {
            val settings = settingsRepository.getSettings() ?: DriverSettings()

            val input = TripEvaluatorInput(
                fare = rawData.fare,
                distanceKm = rawData.distanceKm,
                durationMin = rawData.durationMin,
                settings = settings,
                airportConfig = settings.airportConfig
            )
            val rates = tripEvaluator.evaluate(input)

            val activeShift = shiftRepository.getActiveShift()
            val shiftId = activeShift?.id ?: 0L

            val record = TripRecord(
                shiftId = shiftId,
                fare = rawData.fare,
                distanceKm = rawData.distanceKm,
                durationMin = rawData.durationMin,
                platform = platform,
                evaluationResult = rates.evaluationResult
            )
            tripRepository.saveTripRecord(record)
            Timber.d("$platform trip (OCR) saved: ${rates.evaluationResult} - ${rates.ratePerHour}/h")

            // Mostrar notificación al usuario
            val symbol = settings.countryCode.currencySymbol
            val evalLabel = when (rates.evaluationResult) {
                com.gigassist.domain.model.EvaluationResult.GOOD -> "✅ BUENO"
                com.gigassist.domain.model.EvaluationResult.FAIR -> "⚠️ REGULAR"
                com.gigassist.domain.model.EvaluationResult.POOR -> "❌ POBRE"
            }
            showTripNotification(
                platform = platform,
                fare = "$symbol${String.format("%.0f", rawData.fare)}",
                distance = "${rawData.distanceKm} km",
                duration = "${rawData.durationMin} min",
                ratePerHour = "$symbol${String.format("%.0f", rates.ratePerHour)}/h",
                evaluation = evalLabel
            )

            val uiModel = TripEvaluationUiModel(
                fare = rawData.fare,
                ratePerHour = rates.ratePerHour,
                ratePerDistanceUnit = rates.ratePerDistanceUnit,
                estimatedNetFare = rates.estimatedNetFare,
                evaluationResult = rates.evaluationResult,
                platform = platform,
                distanceKm = rawData.distanceKm,
                durationMin = rawData.durationMin,
                currencySymbol = settings.countryCode.currencySymbol,
                distanceUnitLabel = if (settings.distanceUnit == DistanceUnit.KM) "km" else "mi"
            )
            _evaluationFlow.emit(uiModel)
        } catch (e: Exception) {
            Timber.e(e, "Error processing $platform trip from OCR")
        }
    }

    private fun showTripNotification(
        platform: String,
        fare: String,
        distance: String,
        duration: String,
        ratePerHour: String,
        evaluation: String
    ) {
        // Canal de alta prioridad para ofertas
        val tripChannelId = "gig_trip_channel"
        val tripChannel = NotificationChannel(
            tripChannelId,
            "Ofertas de viaje",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notificaciones de ofertas de viaje detectadas"
            enableVibration(true)
        }
        val mgr = getSystemService(NotificationManager::class.java)
        mgr.createNotificationChannel(tripChannel)

        val notification = NotificationCompat.Builder(this, tripChannelId)
            .setContentTitle("$evaluation — $platform $fare")
            .setContentText("$distance • $duration • $ratePerHour")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()

        mgr.notify(TRIP_NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "GigAssist Captura",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Monitorea ofertas de viaje via captura de pantalla"
        }
        val mgr = getSystemService(NotificationManager::class.java)
        mgr.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GigAssist activo")
            .setContentText("Monitoreando ofertas de viaje")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isCapturing = false
        handler.removeCallbacksAndMessages(null)
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        textRecognizer.close()
        serviceScope.cancel()
        Log.d(TAG, "ScreenCaptureService destroyed")
        super.onDestroy()
    }
}
