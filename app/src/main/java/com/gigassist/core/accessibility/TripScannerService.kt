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
 * Servicio de accesibilidad dedicado a Uber Driver.
 *
 * Estrategia: Leer TODAS las ventanas visibles sin filtrar por packageName.
 * ¿Por qué? El popup de oferta de Uber puede aparecer como:
 * - Una ventana TYPE_APPLICATION con packageName "com.ubercab.driver"
 * - Una ventana overlay sin packageName
 * - Una ventana de sistema con packageName null
 *
 * El filtro por packageName se hace en el XML (solo recibimos eventos de Uber),
 * pero al leer ventanas necesitamos capturar TODAS las que tengan el popup.
 * El parser ya filtra por contenido (requiere "viaje", "aceptar", "km").
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
        private const val DEBOUNCE_MS = 2000L
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("GigScanner", "✅ TripScannerService CONECTADO — listening for Uber events")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val evt = event ?: return
        val eventPkg = evt.packageName?.toString() ?: "null"
        val eventType = when (evt.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "STATE_CHANGED"
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "CONTENT_CHANGED"
            else -> "TYPE_${evt.eventType}"
        }

        // Log cada evento para diagnóstico
        Log.d("GigScanner", "=== EVENTO de $eventPkg ($eventType) ===")

        // Leer TODAS las ventanas — no filtrar por packageName
        // porque el popup de Uber puede aparecer como ventana sin packageName
        val allText = StringBuilder()
        val windowCount = windows.size

        for (window in windows) {
            val root = window.root
            if (root == null) {
                Log.d("GigScanner", "  ventana sin root: tipo=${windowTypeName(window.type)}")
                continue
            }

            val pkg = root.packageName?.toString() ?: "null"
            val wType = windowTypeName(window.type)

            // Solo leer ventanas de Uber o ventanas sin paquete (podrían ser popups)
            if (pkg == UBER_PACKAGE || pkg == "null") {
                Log.d("GigScanner", "  ✓ LEYENDO ventana: pkg=$pkg, tipo=$wType, layer=${window.layer}")
                val windowTexts = extractAllTexts(root)
                allText.append(windowTexts.joinToString(" ")).append(" ")
            } else {
                Log.d("GigScanner", "  ✗ SKIP ventana: pkg=$pkg, tipo=$wType")
            }
            root.recycle()
        }

        val fullText = allText.toString().trim()

        // Si no hay texto, loguear y salir
        if (fullText.isBlank()) {
            Log.d("GigScanner", "  → texto vacío después de $windowCount ventanas")
            return
        }

        // Debounce — no procesar el mismo texto repetidamente
        val now = System.currentTimeMillis()
        if (fullText == lastProcessedText && (now - lastProcessedTime) < DEBOUNCE_MS) return
        lastProcessedText = fullText
        lastProcessedTime = now

        // Parsear — el parser filtra por contenido ("viaje", "aceptar", "km")
        val rawData = uberParser.parse(fullText) ?: return

        Log.d("GigScanner", "🎉 ¡OFERTA DETECTADA! fare=${rawData.fare}, dist=${rawData.distanceKm}, dur=${rawData.durationMin}")
        Timber.d("Uber offer parsed: fare=${rawData.fare}, dist=${rawData.distanceKm}, dur=${rawData.durationMin}")

        serviceScope.launch {
            processTrip(rawData)
        }
    }

    private fun windowTypeName(type: Int): String = when (type) {
        AccessibilityWindowInfo.TYPE_APPLICATION -> "APP"
        AccessibilityWindowInfo.TYPE_SYSTEM -> "SYSTEM"
        AccessibilityWindowInfo.TYPE_INPUT_METHOD -> "INPUT"
        AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> "OVERLAY"
        else -> "TYPE_$type"
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
