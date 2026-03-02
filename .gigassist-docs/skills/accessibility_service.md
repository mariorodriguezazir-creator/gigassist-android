# SKILL: TripScannerService — Android AccessibilityService

## Contexto
- Proyecto: GigAssist
- Propósito: Leer ofertas de viaje de Uber y DiDi en tiempo real.
- Restricción CRÍTICA: Solo lectura. Cero acciones automatizadas.

---

## Estructura de la clase

class TripScannerService : AccessibilityService() {

    private val _tripOfferFlow = MutableSharedFlow<TripOfferRawData?>()
    val tripOfferFlow: SharedFlow<TripOfferRawData?> = _tripOfferFlow

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg != UBER_PACKAGE && pkg != DIDI_PACKAGE) return

        if (event.eventType == TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == TYPE_WINDOW_CONTENT_CHANGED) {
            val root = rootInActiveWindow ?: return
            val parser = getParser(pkg)
            val rawData = parser.parse(root)
            launch { _tripOfferFlow.emit(rawData) }
        }
    }

    override fun onInterrupt() {}
}

---

## Constantes de paquetes

const val UBER_PACKAGE = "com.ubercab.driver"
const val DIDI_PACKAGE = "com.xiaoju.globalapp"

Nota: verificar en dispositivo real el paquete exacto de DiDi
en RD. Puede variar por región. Actualizar esta constante
si es necesario.

---

## Interfaz del parser

interface TripOfferParser {
    fun parse(rootNode: AccessibilityNodeInfo): TripOfferRawData?
}

---

## Implementaciones de parsers

class UberTripOfferParser : TripOfferParser {
    override fun parse(root: AccessibilityNodeInfo): TripOfferRawData? {
        // Extraer todos los textos de nodos hoja
        val texts = extractLeafTexts(root)
        val fare = extractFare(texts)
        val distanceKm = extractDistance(texts)
        val durationMin = extractDuration(texts)
        if (fare == null || distanceKm == null || durationMin == null)
            return null
        return TripOfferRawData(fare, distanceKm, durationMin, "UBER")
    }
}

class DiDiTripOfferParser : TripOfferParser {
    // Misma lógica, ajustar regex según textos reales de DiDi
}

---

## Regex de extracción

// Tarifa: captura número después de símbolo de moneda
// Ejemplos: "RD$ 450", "$ 185.00", "COP 15000"
val fareRegex = Regex("""(?:RD\$|COP|MXN|ARS|PEN|CLP|\$)\s?(\d+[\.,]?\d*)""")

// Distancia: captura número antes de km o mi
// Ejemplos: "12 km", "12.5 km", "7.8 mi"
val distanceRegex = Regex("""(\d+[\.,]?\d*)\s?(?:km|mi|millas)""")

// Duración: captura número antes de min o h
// Ejemplos: "18 min", "1 h 08", "45 minutos"
val durationRegex = Regex("""(\d+)\s?(?:min|mins|minutos)""")

---

## Función auxiliar de extracción de textos

fun extractLeafTexts(node: AccessibilityNodeInfo): List<String> {
    val texts = mutableListOf<String>()
    if (node.childCount == 0) {
        node.text?.toString()?.let { texts.add(it) }
    } else {
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { texts.addAll(extractLeafTexts(it)) }
        }
    }
    return texts
}

---

## Privacidad
- NO loggear nombres de pasajeros.
- NO loggear direcciones exactas.
- Solo loggear: tarifa extraída, distancia, duración (para debug).
- En release: desactivar todos los logs de parsing.

---

## Testing del parser
- Crear fixtures de texto reales capturados en dispositivo físico.
- Probar con al menos 10 ejemplos reales de Uber.
- Probar con al menos 10 ejemplos reales de DiDi.
- Documentar los textos reales en test/resources/fixtures/.
