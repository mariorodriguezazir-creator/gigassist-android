package com.gigassist.core.accessibility

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.gigassist.core.calculator.TripEvaluator
import com.gigassist.core.parser.TripOfferParser
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
 * Uber usa React Native que renderiza vía canvas — window.root
 * NO contiene texto accesible. La solución es:
 * 1. Usar event.source para obtener el nodo que cambió
 * 2. Navegar al nodo raíz desde event.source
 * 3. Extraer TODO el texto recursivamente desde ahí
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

    /** Debounce */
    private var lastProcessedText: String = ""
    private var lastProcessedTime: Long = 0L

    companion object {
        private const val UBER_PACKAGE = "com.ubercab.driver"
        private const val DEBOUNCE_MS = 2000L
        private const val TAG = "GigScanner"
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "✅ TripScannerService CONECTADO — listening for Uber events")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val evt = event ?: return
        val eventPkg = evt.packageName?.toString() ?: "null"

        // Doble check: solo Uber
        if (eventPkg != UBER_PACKAGE) return

        val eventType = when (evt.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "STATE_CHANGED"
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "CONTENT_CHANGED"
            else -> "TYPE_${evt.eventType}"
        }

        // === ESTRATEGIA 1: Usar event.source (el nodo que realmente cambió) ===
        val source = evt.source
        if (source != null) {
            // Navegar al root del nodo source
            val root = findRoot(source)
            val texts = extractAllTextsDeep(root)
            val textFromSource = texts.joinToString(" ").trim()

            if (textFromSource.isNotBlank()) {
                Log.d(TAG, "=== EVENT_SOURCE ($eventType) texto=${textFromSource.take(200)}...")
                processText(textFromSource)
            }
            root.recycle()
        }

        // === ESTRATEGIA 2: Leer texto directamente del evento ===
        val eventText = evt.text?.joinToString(" ")?.trim() ?: ""
        if (eventText.isNotBlank()) {
            Log.d(TAG, "=== EVENT_TEXT ($eventType) texto=${eventText.take(200)}...")
            processText(eventText)
        }

        // === ESTRATEGIA 3: Leer ventanas de Uber (fallback) ===
        for (window in windows) {
            val wRoot = window.root ?: continue
            val pkg = wRoot.packageName?.toString()
            if (pkg != UBER_PACKAGE) {
                wRoot.recycle()
                continue
            }
            val windowTexts = extractAllTextsDeep(wRoot)
            val windowText = windowTexts.joinToString(" ").trim()
            wRoot.recycle()

            if (windowText.isNotBlank()) {
                Log.d(TAG, "=== WINDOW ($eventType) texto=${windowText.take(200)}...")
                processText(windowText)
            }
        }
    }

    /**
     * Navega hacia arriba hasta encontrar el nodo raíz.
     */
    private fun findRoot(node: AccessibilityNodeInfo): AccessibilityNodeInfo {
        var current = node
        var parent = current.parent
        while (parent != null) {
            if (current != node) {
                // No reciclar el nodo original, solo los intermedios
                // Actually, no reciclar nada aquí, lo hacemos al final
            }
            current = parent
            parent = current.parent
        }
        return current
    }

    /**
     * Extrae TODOS los textos del árbol de accesibilidad de forma profunda.
     * Lee: text, contentDescription, hintText, y el texto de los nodos hijos.
     */
    private fun extractAllTextsDeep(node: AccessibilityNodeInfo): List<String> {
        val texts = mutableListOf<String>()

        // Leer texto del nodo actual
        node.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { texts.add(it) }
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { texts.add(it) }

        // hintText (API 26+)
        try {
            node.hintText?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { texts.add(it) }
        } catch (_: Exception) { }

        // Recorrer hijos
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            texts.addAll(extractAllTextsDeep(child))
            child.recycle()
        }
        return texts
    }

    private fun processText(fullText: String) {
        // Debounce
        val now = System.currentTimeMillis()
        if (fullText == lastProcessedText && (now - lastProcessedTime) < DEBOUNCE_MS) return
        lastProcessedText = fullText
        lastProcessedTime = now

        // Parsear
        val rawData = uberParser.parse(fullText) ?: return

        Log.d(TAG, "🎉 ¡OFERTA DETECTADA! fare=${rawData.fare}, dist=${rawData.distanceKm}, dur=${rawData.durationMin}")
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
