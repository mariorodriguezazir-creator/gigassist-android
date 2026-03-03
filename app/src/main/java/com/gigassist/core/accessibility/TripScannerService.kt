package com.gigassist.core.accessibility

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import com.gigassist.core.calculator.TripEvaluator
import com.gigassist.core.parser.TripOfferParser
import com.gigassist.core.parser.UberTripOfferParser
import com.gigassist.core.parser.extractAllTexts
import com.gigassist.domain.model.DistanceUnit
import com.gigassist.domain.model.DriverSettings
import com.gigassist.domain.model.TripEvaluationUiModel
import com.gigassist.domain.model.TripEvaluatorInput
import com.gigassist.domain.model.TripOfferRawData
import com.gigassist.domain.model.TripRecord
import com.gigassist.domain.repository.SettingsRepository
import com.gigassist.domain.repository.ShiftRepository
import com.gigassist.domain.repository.TripRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Servicio de accesibilidad dedicado EXCLUSIVAMENTE a Uber Driver.
 *
 * IMPORTANTE: El flag packageNames en el XML solo controla qué eventos
 * DISPARAN onAccessibilityEvent. Pero la propiedad `windows` devuelve
 * TODAS las ventanas visibles (notificaciones, barra de estado, MIUI, etc).
 * Por eso DEBEMOS filtrar ventanas por packageName del nodo raíz.
 */
@AndroidEntryPoint
class TripScannerService : AccessibilityService() {

    @Inject lateinit var tripEvaluator: TripEvaluator
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var tripRepository: TripRepository
    @Inject lateinit var shiftRepository: ShiftRepository

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val uberParser: TripOfferParser = UberTripOfferParser()

    private val _evaluationFlow = MutableSharedFlow<TripEvaluationUiModel>(replay = 1)
    val evaluationFlow: SharedFlow<TripEvaluationUiModel> = _evaluationFlow.asSharedFlow()

    /** Debounce: último texto procesado para evitar spam */
    private var lastProcessedText: String = ""
    private var lastProcessedTime: Long = 0L

    companion object {
        private const val UBER_PACKAGE = "com.ubercab.driver"
        private const val DEBOUNCE_MS = 1000L
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val evt = event ?: return

        // 1. SOLO extraer texto de ventanas que pertenecen a Uber
        val uberText = StringBuilder()
        for (window in windows) {
            val root = window.root ?: continue
            val pkg = root.packageName?.toString()

            if (pkg != UBER_PACKAGE) {
                root.recycle()
                continue
            }

            // Log del tipo de ventana para debug
            val windowType = when (window.type) {
                AccessibilityWindowInfo.TYPE_APPLICATION -> "APP"
                AccessibilityWindowInfo.TYPE_SYSTEM -> "SYSTEM"
                AccessibilityWindowInfo.TYPE_INPUT_METHOD -> "INPUT"
                AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> "OVERLAY"
                else -> "TYPE_${window.type}"
            }
            Log.d("GigScanner", "Ventana Uber encontrada: tipo=$windowType, layer=${window.layer}")

            val windowTexts = extractAllTexts(root)
            uberText.append(windowTexts.joinToString(" ")).append(" ")
            root.recycle()
        }

        val fullText = uberText.toString().trim()

        // 2. Ignorar si no hay texto de Uber
        if (fullText.isBlank()) return

        // 3. Debounce — no procesar el mismo texto repetidamente
        val now = System.currentTimeMillis()
        if (fullText == lastProcessedText && (now - lastProcessedTime) < DEBOUNCE_MS) return
        lastProcessedText = fullText
        lastProcessedTime = now

        // 4. Parsear
        val rawData = uberParser.parse(fullText) ?: return

        Timber.d("Uber offer parsed: fare=${rawData.fare}, dist=${rawData.distanceKm}, dur=${rawData.durationMin}")

        serviceScope.launch {
            processTrip(rawData)
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
                platform = "Uber",
                evaluationResult = rates.evaluationResult
            )
            tripRepository.saveTripRecord(record)
            Timber.d("Uber trip saved: ${rates.evaluationResult} - ${rates.ratePerHour}/h")

            val uiModel = TripEvaluationUiModel(
                fare = rawData.fare,
                ratePerHour = rates.ratePerHour,
                ratePerDistanceUnit = rates.ratePerDistanceUnit,
                estimatedNetFare = rates.estimatedNetFare,
                evaluationResult = rates.evaluationResult,
                platform = "Uber",
                distanceKm = rawData.distanceKm,
                durationMin = rawData.durationMin,
                currencySymbol = settings.countryCode.currencySymbol,
                distanceUnitLabel = if (settings.distanceUnit == DistanceUnit.KM) "km" else "mi"
            )
            _evaluationFlow.emit(uiModel)
        } catch (e: Exception) {
            Timber.e(e, "Error processing Uber trip")
        }
    }

    override fun onInterrupt() {
        Timber.d("TripScannerService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Timber.d("TripScannerService destroyed")
    }
}
