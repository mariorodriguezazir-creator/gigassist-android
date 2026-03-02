package com.gigassist.core.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.gigassist.core.parser.DiDiTripOfferParser
import com.gigassist.core.parser.TripOfferParser
import com.gigassist.core.parser.UberTripOfferParser
import com.gigassist.domain.model.TripOfferRawData
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
 * Servicio de accesibilidad que detecta ofertas de viaje
 * en Uber Driver y DiDi Driver.
 *
 * RESTRICCIÓN CRÍTICA: Solo lectura del árbol de accesibilidad.
 * Cero acciones automatizadas. No modifica ni interactúa con
 * la UI de Uber/DiDi.
 *
 * Flujo:
 * 1. Detecta evento de cambio de ventana/contenido en Uber o DiDi.
 * 2. Obtiene el nodo raíz del árbol de accesibilidad.
 * 3. Delega el parsing al parser correspondiente (UberParser/DiDiParser).
 * 4. Emite TripOfferRawData a través de SharedFlow para que
 *    TripEvaluator lo procese y el overlay lo muestre.
 */
class TripScannerService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _tripOfferFlow = MutableSharedFlow<TripOfferRawData?>(
        replay = 1,
        extraBufferCapacity = 1
    )
    val tripOfferFlow: SharedFlow<TripOfferRawData?> = _tripOfferFlow.asSharedFlow()

    private val uberParser: TripOfferParser = UberTripOfferParser()
    private val diDiParser: TripOfferParser = DiDiTripOfferParser()

    override fun onServiceConnected() {
        super.onServiceConnected()
        Timber.i("TripScannerService conectado — monitoreando Uber y DiDi")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return

        // Solo procesar eventos de paquetes monitoreados
        if (packageName != UBER_PACKAGE && packageName != DIDI_PACKAGE) return

        // Solo procesar tipos de evento relevantes
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return

        val rootNode = rootInActiveWindow ?: return

        val parser = getParserForPackage(packageName)
        val rawData = parser.parse(rootNode)

        serviceScope.launch {
            _tripOfferFlow.emit(rawData)
        }
    }

    override fun onInterrupt() {
        Timber.w("TripScannerService interrumpido")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Timber.i("TripScannerService destruido")
    }

    /**
     * Devuelve el parser correspondiente según el paquete de la app detectada.
     */
    private fun getParserForPackage(packageName: String): TripOfferParser {
        return when (packageName) {
            UBER_PACKAGE -> uberParser
            DIDI_PACKAGE -> diDiParser
            else -> uberParser // Fallback, no debería ocurrir dado el filtro previo
        }
    }

    companion object {
        /** Paquete de Uber Driver */
        const val UBER_PACKAGE = "com.ubercab.driver"

        /**
         * Paquete de DiDi Driver.
         * Nota: verificar en dispositivo real el paquete exacto de DiDi
         * en RD. Puede variar por región. Actualizar esta constante
         * si es necesario.
         */
        const val DIDI_PACKAGE = "com.xiaoju.globalapp"
    }
}
