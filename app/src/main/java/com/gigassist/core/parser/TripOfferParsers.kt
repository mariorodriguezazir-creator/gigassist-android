package com.gigassist.core.parser

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.gigassist.domain.model.TripOfferRawData
import timber.log.Timber
import javax.inject.Inject

/**
 * Parser de ofertas de viaje para Uber Driver.
 * Formato real capturado en RD (Popup):
 *   "DOP108"
 *   "+DOP16.38 por inicio de viaje"
 *   "A 6 min (1.4 km)"
 *   "Viaje: 10 min (2.7 km)"
 *   "Aceptar"
 */
class UberTripOfferParser @Inject constructor() : TripOfferParser {

    override fun parse(fullText: String): TripOfferRawData? {
        Log.d("GigParser", "=== TEXTO CRUDO === $fullText")

        if (!isRealUberOffer(fullText)) {
            Log.d("GigParser", "SKIP — no es oferta real Uber")
            return null
        }

        // Tarifa principal — primer "DOP" seguido de número SIN el "+" delante
        val fareRegex = Regex("""(?<!\+)DOP\s*(\d+(?:[.,]\d{1,2})?)""")
        val fare = fareRegex.find(fullText)?.groupValues?.get(1)
            ?.replace(",", ".")?.toDoubleOrNull()

        // Distancia del viaje (la que tiene "Viaje" delante)
        val distRegex = Regex("""[Vv]iaje[^\d]*?(\d+(?:[.,]\d)?)\s*km""", RegexOption.IGNORE_CASE)
        val dist = distRegex.find(fullText)?.groupValues?.get(1)
            ?.replace(",", ".")?.toDoubleOrNull()

        // Duración del viaje
        val durRegex = Regex("""[Vv]iaje[^\d]*?(\d+)\s*min""", RegexOption.IGNORE_CASE)
        val dur = durRegex.find(fullText)?.groupValues?.get(1)?.toIntOrNull()

        Log.d("GigParser", "fare=$fare | dist=$dist | dur=$dur")

        if (fare == null || dist == null || dur == null) {
            Timber.d("Uber parser: datos incompletos — fare=$fare, dist=$dist, dur=$dur")
            Log.d("GigParser", "SKIP — faltan datos. fare=$fare, dist=$dist, dur=$dur")
            return null
        }

        Timber.d("Uber parser: oferta detectada — fare=$fare, dist=$dist km, dur=$dur min")
        return TripOfferRawData(fare, dist, dur, PLATFORM_UBER)
    }

    private fun isRealUberOffer(text: String): Boolean {
        return text.contains("viaje", ignoreCase = true) &&
               text.contains("aceptar", ignoreCase = true) &&
               text.contains("km", ignoreCase = true)
    }

    companion object {
        const val PLATFORM_UBER = "Uber"
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
        Log.d("GigParser", "=== TEXTO CRUDO === $fullText")

        if (!isRealDiDiOffer(fullText)) {
            Log.d("GigParser", "SKIP — no es oferta real DiDi")
            return null
        }

        // Tarifa: primer "$" seguido de número
        val fareRegex = Regex("""\$\s*(\d+(?:[.,]\d{1,2})?)""")
        val fare = fareRegex.find(fullText)?.groupValues?.get(1)
            ?.replace(",", ".")?.toDoubleOrNull()

        // Distancia: formato "Xmin (Y.Ykm)" sin espacios. Tomar la SEGUNDA ocurrencia.
        val distRegex = Regex("""\d+min\s*\((\d+(?:[.,]\d+)?)\s*km\)""", RegexOption.IGNORE_CASE)
        val distMatches = distRegex.findAll(fullText).toList()
        val distStr = if (distMatches.size >= 2) distMatches[1].groupValues[1]
                      else distMatches.firstOrNull()?.groupValues?.get(1)
        val dist = distStr?.replace(",", ".")?.toDoubleOrNull()

        // Duración: segunda ocurrencia de "Xmin"
        val durRegex = Regex("""(\d+)min""", RegexOption.IGNORE_CASE)
        val durMatches = durRegex.findAll(fullText).toList()
        val durStr = if (durMatches.size >= 2) durMatches[1].groupValues[1]
                     else durMatches.firstOrNull()?.groupValues?.get(1)
        val dur = durStr?.toIntOrNull()

        Log.d("GigParser", "fare=$fare | dist=$dist | dur=$dur")

        if (fare == null || dist == null || dur == null) {
            Timber.d("DiDi parser: datos incompletos — fare=$fare, dist=$dist, dur=$dur")
            return null
        }

        Timber.d("DiDi parser: oferta detectada — fare=$fare, dist=$dist km, dur=$dur min")
        return TripOfferRawData(fare, dist, dur, PLATFORM_DIDI)
    }

    private fun isRealDiDiOffer(text: String): Boolean {
        val hasPrice = Regex("""\$\s*\d+""").containsMatchIn(text)
        val hasKm = text.contains("km", ignoreCase = true)
        val hasMin = text.contains("min", ignoreCase = true)
        
        if (!hasPrice || !hasKm || !hasMin) {
            Log.d("GigParser", "DiDi isRealOffer checks failed: price=$hasPrice, km=$hasKm, min=$hasMin")
            return false
        }
        return true
    }

    companion object {
        const val PLATFORM_DIDI = "DiDi"
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
