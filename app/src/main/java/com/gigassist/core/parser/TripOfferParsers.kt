package com.gigassist.core.parser

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.gigassist.domain.model.TripOfferRawData
import timber.log.Timber
import javax.inject.Inject

/**
 * Parser de ofertas de viaje para Uber Driver.
 *
 * Formato real capturado por OCR en RD (Popup):
 *   "DOP107.82"                         ← tarifa (OCR puede leer "DOPl07" con L)
 *   "+DOP16.87 por inicio de viaje"     ← bonus, se ignora
 *   "A3 min (0.6 km)"                   ← distancia de recogida
 *   "Viaje: 12 min (3.5 km)"            ← distancia/duración del viaje
 *
 * NOTA: "Aceptar" NO siempre aparece en el OCR (botón cortado).
 * El filtro solo requiere: DOP + "Viaje:" + "km"
 */
class UberTripOfferParser @Inject constructor() : TripOfferParser {

    override fun parse(fullText: String): TripOfferRawData? {
        if (!isRealUberOffer(fullText)) {
            return null
        }

        Log.d(TAG, "🔍 Parsing Uber: ${fullText.take(200)}")

        // Tarifa principal — "DOP" seguido de número, SIN "+" delante
        // OCR a veces lee "l" en vez de "1", normalizar
        val normalized = fullText
            .replace(Regex("""DOP[lI]"""), "DOP1")  // OCR: l/I → 1
            .replace(Regex("""DOPo""", RegexOption.IGNORE_CASE), "DOP0")  // OCR: o → 0

        val fareRegex = Regex("""(?<!\+)DOP\s*(\d+(?:[.,]\d{1,2})?)""")
        val fare = fareRegex.find(normalized)?.groupValues?.get(1)
            ?.replace(",", ".")?.toDoubleOrNull()

        // Distancia del viaje (la que tiene "Viaje" delante)
        val distRegex = Regex("""[Vv]iaje[^\d]*?(\d+(?:[.,]\d+)?)\s*km""", RegexOption.IGNORE_CASE)
        val dist = distRegex.find(fullText)?.groupValues?.get(1)
            ?.replace(",", ".")?.toDoubleOrNull()
            // Fallback: buscar el patrón "N min (N.N km)" más grande
            ?: findTripDistance(fullText)

        // Duración del viaje
        val durRegex = Regex("""[Vv]iaje[^\d]*?(\d+)\s*min""", RegexOption.IGNORE_CASE)
        val dur = durRegex.find(fullText)?.groupValues?.get(1)?.toIntOrNull()
            // Fallback: buscar el segundo "N min" (el primero es recogida)
            ?: findTripDuration(fullText)

        Log.d(TAG, "fare=$fare | dist=$dist | dur=$dur")

        if (fare == null || dist == null || dur == null) {
            Log.d(TAG, "SKIP — faltan datos. fare=$fare, dist=$dist, dur=$dur")
            return null
        }

        Log.d(TAG, "🎉 OFERTA UBER: fare=$fare, dist=$dist km, dur=$dur min")
        return TripOfferRawData(fare, dist, dur, PLATFORM_UBER)
    }

    /**
     * Filtro mínimo para OCR — NO requiere "Aceptar" porque
     * el OCR frecuentemente corta el botón.
     */
    private fun isRealUberOffer(text: String): Boolean {
        val hasDOP = text.contains("DOP", ignoreCase = true)
        val hasViaje = text.contains("viaje", ignoreCase = true)
        val hasKm = text.contains("km", ignoreCase = true)
        return hasDOP && hasViaje && hasKm
    }

    /**
     * Fallback para distancia: buscar patrón "N.N km)" con el mayor valor
     * ya que la distancia del viaje > distancia de recogida.
     */
    private fun findTripDistance(text: String): Double? {
        val allDistances = Regex("""(\d+(?:[.,]\d+)?)\s*km""", RegexOption.IGNORE_CASE)
            .findAll(text)
            .mapNotNull { it.groupValues[1].replace(",", ".").toDoubleOrNull() }
            .toList()
        // La distancia más grande es la del viaje
        return allDistances.maxOrNull()
    }

    /**
     * Fallback para duración: buscar "N min" — la segunda ocurrencia
     * suele ser la duración del viaje (la primera es recogida).
     */
    private fun findTripDuration(text: String): Int? {
        val allDurations = Regex("""(\d+)\s*min""", RegexOption.IGNORE_CASE)
            .findAll(text)
            .mapNotNull { it.groupValues[1].toIntOrNull() }
            .toList()
        // Si hay 2+, la segunda es la duración del viaje
        return if (allDurations.size >= 2) allDurations[1]
               else allDurations.firstOrNull()
    }

    companion object {
        const val PLATFORM_UBER = "Uber"
        private const val TAG = "GigParser"
    }
}

/**
 * Parser de ofertas de viaje para DiDi Driver.
 * Formato real capturado en RD (Popup oscuro):
 *   "$352.75"
 *   "Dinámica x1.4  $39.56"
 *   "4.96 • 189 viajes"
 *   "8min (1.2km)"
 *   "37min (13.6km)"
 */
class DiDiTripOfferParser @Inject constructor() : TripOfferParser {

    override fun parse(fullText: String): TripOfferRawData? {
        if (!isRealDiDiOffer(fullText)) {
            return null
        }

        Log.d(TAG, "🔍 Parsing DiDi: ${fullText.take(200)}")

        // Tarifa principal de DiDi siempre será el monto más alto en pantalla (ej: $142.80 > $37.21)
        val fareRegex = Regex("""\$\s*(\d+(?:[.,]\d{1,2})?)""")
        val fare = fareRegex.findAll(fullText)
            .mapNotNull { it.groupValues[1].replace(",", ".").toDoubleOrNull() }
            .maxOrNull()

        // Distancia: formato "Xmin (Y.Ykm)". Tomar el máximo asegura obtener la del viaje (y no recogida) aun si el OCR salta el orden.
        val distRegex = Regex("""\d+min\s*\((\d+(?:[.,]\d+)?)\s*km\)""", RegexOption.IGNORE_CASE)
        val dist = distRegex.findAll(fullText)
            .mapNotNull { it.groupValues[1].replace(",", ".").toDoubleOrNull() }
            .maxOrNull()

        // Duración: el mayor tiempo en minutos
        val durRegex = Regex("""(\d+)min""", RegexOption.IGNORE_CASE)
        val dur = durRegex.findAll(fullText)
            .mapNotNull { it.groupValues[1].toIntOrNull() }
            .maxOrNull()

        Log.d(TAG, "fare=$fare | dist=$dist | dur=$dur")

        if (fare == null || dist == null || dur == null) {
            Log.d(TAG, "DiDi SKIP — faltan datos. fare=$fare, dist=$dist, dur=$dur")
            return null
        }

        Log.d(TAG, "🎉 OFERTA DIDI: fare=$fare, dist=$dist km, dur=$dur min")
        return TripOfferRawData(fare, dist, dur, PLATFORM_DIDI)
    }

    private fun isRealDiDiOffer(text: String): Boolean {
        val hasPrice = Regex("""\$\s*\d+""").containsMatchIn(text)
        val hasKm = text.contains("km", ignoreCase = true)
        val hasMin = text.contains("min", ignoreCase = true)
        return hasPrice && hasKm && hasMin
    }

    companion object {
        const val PLATFORM_DIDI = "DiDi"
        private const val TAG = "GigParser"
    }
}

/**
 * Extrae todos los textos (text y contentDescription) del árbol de accesibilidad,
 * visitando cada nodo (no solo las hojas) para evitar perder información
 * en jerarquías complejas y reciclando los nodos para evitar fugas de memoria.
 */
fun extractAllTexts(node: AccessibilityNodeInfo): List<String> {
    val texts = mutableListOf<String>()

    node.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { texts.add(it) }
    node.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { texts.add(it) }

    for (i in 0 until node.childCount) {
        node.getChild(i)?.let { child ->
            texts.addAll(extractAllTexts(child))
            child.recycle()
        }
    }
    return texts
}
