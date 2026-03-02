package com.gigassist.core.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.gigassist.core.calculator.TripEvaluator
import com.gigassist.core.parser.DiDiTripOfferParser
import com.gigassist.core.parser.TripOfferParser
import com.gigassist.core.parser.UberTripOfferParser
import com.gigassist.domain.model.DistanceUnit
import com.gigassist.domain.model.DriverSettings
import com.gigassist.domain.model.EvaluationResult
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
 * Servicio de accesibilidad que detecta ofertas de viaje de Uber y DiDi.
 * Solo lee datos del árbol de accesibilidad, NO ejecuta acciones automatizadas.
 */
@AndroidEntryPoint
class TripScannerService : AccessibilityService() {

    @Inject lateinit var tripEvaluator: TripEvaluator
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var tripRepository: TripRepository
    @Inject lateinit var shiftRepository: ShiftRepository

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val uberParser: TripOfferParser = UberTripOfferParser()
    private val didiParser: TripOfferParser = DiDiTripOfferParser()

    private val _evaluationFlow = MutableSharedFlow<TripEvaluationUiModel>(replay = 1)
    val evaluationFlow: SharedFlow<TripEvaluationUiModel> = _evaluationFlow.asSharedFlow()

    companion object {
        private const val UBER_PACKAGE = "com.ubercab.driver"
        private const val DIDI_PACKAGE = "com.didiglobal.driver"
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val evt = event ?: return
        val packageName = evt.packageName?.toString() ?: return

        val parser: TripOfferParser
        val platform: String
        when (packageName) {
            UBER_PACKAGE -> { parser = uberParser; platform = "Uber" }
            DIDI_PACKAGE -> { parser = didiParser; platform = "DiDi" }
            else -> return
        }

        val allWindows = windows
        val texts = java.lang.StringBuilder()
        for (window in allWindows) {
            val root = window.root ?: continue
            val windowTexts = com.gigassist.core.parser.extractAllTexts(root)
            texts.append(windowTexts.joinToString(" ")).append(" ")
            root.recycle()
        }
        val fullText = texts.toString()
        val rawData = parser.parse(fullText) ?: return

        Timber.d("Trip offer parsed from $platform: fare=${rawData.fare}, dist=${rawData.distanceKm}, dur=${rawData.durationMin}")

        serviceScope.launch {
            processTrip(rawData, platform)
        }
    }

    private suspend fun processTrip(rawData: TripOfferRawData, platform: String) {
        try {
            // 1. Get driver settings
            val settings = settingsRepository.getSettings() ?: DriverSettings()

            // 2. Evaluate trip
            val input = TripEvaluatorInput(
                fare = rawData.fare,
                distanceKm = rawData.distanceKm,
                durationMin = rawData.durationMin,
                settings = settings,
                airportConfig = settings.airportConfig
            )
            val rates = tripEvaluator.evaluate(input)

            // 3. Get active shift
            val activeShift = shiftRepository.getActiveShift()
            val shiftId = activeShift?.id ?: 0L

            // 4. Save trip record
            val record = TripRecord(
                shiftId = shiftId,
                fare = rawData.fare,
                distanceKm = rawData.distanceKm,
                durationMin = rawData.durationMin,
                platform = platform,
                evaluationResult = rates.evaluationResult
            )
            tripRepository.saveTripRecord(record)
            Timber.d("Trip saved: ${rates.evaluationResult} - ${rates.ratePerHour}/h")

            // 5. Emit evaluation for overlay
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
            Timber.e(e, "Error processing trip")
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
