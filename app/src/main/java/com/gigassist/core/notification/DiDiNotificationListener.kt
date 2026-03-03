package com.gigassist.core.notification

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.gigassist.core.calculator.TripEvaluator
import com.gigassist.core.parser.DiDiTripOfferParser
import com.gigassist.domain.model.DistanceUnit
import com.gigassist.domain.model.DriverSettings
import com.gigassist.domain.model.TripEvaluationUiModel
import com.gigassist.domain.model.TripEvaluatorInput
import com.gigassist.domain.model.TripOfferRawData
import com.gigassist.domain.model.TripRecord
import com.gigassist.domain.repository.SettingsRepository
import com.gigassist.domain.repository.ShiftRepository
import com.gigassist.domain.repository.TripRepository
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
 * NotificationListenerService para capturar ofertas de DiDi.
 *
 * NO usa @AndroidEntryPoint porque NotificationListenerService
 * es instanciado directamente por el sistema y Hilt no soporta
 * inyección automática en este tipo de servicio.
 * Usamos EntryPointAccessors para inyección manual.
 */
class DiDiNotificationListener : NotificationListenerService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DiDiListenerEntryPoint {
        fun tripEvaluator(): TripEvaluator
        fun settingsRepository(): SettingsRepository
        fun tripRepository(): TripRepository
        fun shiftRepository(): ShiftRepository
    }

    private lateinit var tripEvaluator: TripEvaluator
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var tripRepository: TripRepository
    private lateinit var shiftRepository: ShiftRepository

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val didiParser = DiDiTripOfferParser()

    private val _evaluationFlow = MutableSharedFlow<TripEvaluationUiModel>(replay = 1)
    val evaluationFlow: SharedFlow<TripEvaluationUiModel> = _evaluationFlow.asSharedFlow()

    companion object {
        private const val DIDI_PACKAGE = "com.didiglobal.driver"
        private const val TAG = "DiDiNotif"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate — inicializando inyección manual de Hilt")
        try {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                DiDiListenerEntryPoint::class.java
            )
            tripEvaluator = entryPoint.tripEvaluator()
            settingsRepository = entryPoint.settingsRepository()
            tripRepository = entryPoint.tripRepository()
            shiftRepository = entryPoint.shiftRepository()
            Log.d(TAG, "✅ Dependencias inyectadas correctamente")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error inyectando dependencias", e)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "✅ NotificationListener CONECTADO — escuchando notificaciones")
        try {
            val active = activeNotifications
            Log.d(TAG, "Notificaciones activas: ${active?.size ?: 0}")
            active?.take(5)?.forEach { sbn ->
                Log.d(TAG, "  → ${sbn.packageName}: ${sbn.notification.extras?.getString(Notification.EXTRA_TITLE)}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error leyendo notificaciones activas", e)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "❌ NotificationListener DESCONECTADO")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn ?: return

        // Log TODAS las notificaciones para debug
        Log.d(TAG, "Notif recibida: pkg=${notification.packageName}")

        if (notification.packageName != DIDI_PACKAGE) return

        val extras = notification.notification.extras ?: return
        val title   = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text    = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""
        val infoText = extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString() ?: ""

        val full = listOf(title, text, bigText, subText, infoText)
            .filter { it.isNotBlank() }
            .joinToString(" ")

        Log.d(TAG, "=== DiDi NOTIF ===")
        Log.d(TAG, "  title:   $title")
        Log.d(TAG, "  text:    $text")
        Log.d(TAG, "  bigText: $bigText")
        Log.d(TAG, "  FULL:    $full")

        if (full.isBlank()) return

        val offer = didiParser.parse(full)
        if (offer != null) {
            Log.d(TAG, "✅ DiDi OFERTA: fare=${offer.fare}, dist=${offer.distanceKm}, dur=${offer.durationMin}")
            Timber.d("DiDi offer from notification: fare=${offer.fare}, dist=${offer.distanceKm}, dur=${offer.durationMin}")

            serviceScope.launch {
                processTrip(offer)
            }
        } else {
            Log.d(TAG, "ℹ️ Parser no encontró oferta en esta notificación")
        }
    }

    private suspend fun processTrip(rawData: TripOfferRawData) {
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
                platform = "DiDi",
                evaluationResult = rates.evaluationResult
            )
            tripRepository.saveTripRecord(record)
            Timber.d("DiDi trip saved: ${rates.evaluationResult} - ${rates.ratePerHour}/h")

            val uiModel = TripEvaluationUiModel(
                fare = rawData.fare,
                ratePerHour = rates.ratePerHour,
                ratePerDistanceUnit = rates.ratePerDistanceUnit,
                estimatedNetFare = rates.estimatedNetFare,
                evaluationResult = rates.evaluationResult,
                platform = "DiDi",
                distanceKm = rawData.distanceKm,
                durationMin = rawData.durationMin,
                currencySymbol = settings.countryCode.currencySymbol,
                distanceUnitLabel = if (settings.distanceUnit == DistanceUnit.KM) "km" else "mi"
            )
            _evaluationFlow.emit(uiModel)
        } catch (e: Exception) {
            Timber.e(e, "Error processing DiDi trip from notification")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // No action needed
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Timber.d("DiDiNotificationListener destroyed")
    }
}
