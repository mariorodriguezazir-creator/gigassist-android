package com.gigassist.core.parser

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.gigassist.domain.model.TripOfferRawData
import timber.log.Timber
import javax.inject.Inject

/**
 * Parser de ofertas de viaje para Uber Driver.
 * Formato real capturado en RD:
 *   Tarifa: "DOP108" o "DOP108.50"
 *   Bonus:  "+DOP16.38 por inicio de viaje" (se ignora)
 *   Pickup: "A 6 min (1.4 km)"
 *   Viaje:  "Viaje: 10 min (2.7 km)"
 */
class UberTripOfferParser @Inject constructor() : TripOfferParser {

    override fun parse(rootNode: AccessibilityNodeInfo): TripOfferRawData? {
        val texts = extractLeafTexts(rootNode)
        if (texts.isEmpty()) return null

        val fullText = texts.joinToString(" ")
        Log.d("GigParser", "=== TEXTO CRUDO === $fullText")

        // Tarifa principal — primer "DOP" seguido de número SIN el "+" delante
        val fareRegex = Regex("""(?<!\+)DOP\s*(\d+(?:[.,]\d{1,2})?)""")
        val fare = fareRegex.find(fullText)?.groupValues?.get(1)
            ?.replace(",", ".")?.toDoubleOrNull()

        // Distancia del viaje (no del pickup)
        val distRegex = Regex("""[Vv]iaje:\s*\d+\s*min\s*\((\d+(?:[.,]\d)?)\s*km\)""")
        val dist = distRegex.find(fullText)?.groupValues?.get(1)
            ?.replace(",", ".")?.toDoubleOrNull()

        // Duración del viaje (no del pickup)
        val durRegex = Regex("""[Vv]iaje:\s*(\d+)\s*min""")
        val dur = durRegex.find(fullText)?.groupValues?.get(1)?.toIntOrNull()

        Log.d("GigParser", "fare=$fare | dist=$dist | dur=$dur")

        if (fare == null || dist == null || dur == null) {
            Timber.d("Uber parser: datos incompletos — fare=$fare, dist=$dist, dur=$dur")
            return null
        }

        Timber.d("Uber parser: oferta detectada — fare=$fare, dist=$dist km, dur=$dur min")
        return TripOfferRawData(fare, dist, dur, PLATFORM_UBER)
    }

    companion object {
        const val PLATFORM_UBER = "Uber"
    }
}

/**
 * Parser de ofertas de viaje para DiDi Driver.
 * Dos formatos reales capturados en RD:
 *
 * Vista popup (oferta rápida):
 *   Tarifa: "$352.75"
 *   Pickup: "8min (1.2km)"
 *   Viaje:  "37min (13.6km)"
 *
 * Vista lista (Centro de viajes):
 *   Tarifa: "$352.75  ⚡x1.4"
 *   Pickup: "(9 min 1.2 km)"
 *   Viaje:  "(37 min 13.6 km)"
 */
class DiDiTripOfferParser @Inject constructor() : TripOfferParser {

    override fun parse(rootNode: AccessibilityNodeInfo): TripOfferRawData? {
        val texts = extractLeafTexts(rootNode)
        if (texts.isEmpty()) return null

        val fullText = texts.joinToString(" ")
        Log.d("GigParser", "=== TEXTO CRUDO === $fullText")

        // Tarifa — "$" seguido de número con decimales
        val fareRegex = Regex("""\$\s*(\d+(?:[.,]\d{1,2})?)""")
        val fare = fareRegex.find(fullText)?.groupValues?.get(1)
            ?.replace(",", ".")?.toDoubleOrNull()

        // Distancia — tomar la SEGUNDA coincidencia de km (la del viaje, no del pickup)
        val distRegex = Regex("""(\d+(?:[.,]\d)?)\s*km""")
        val distMatches = distRegex.findAll(fullText).toList()
        val dist = if (distMatches.size >= 2) {
            distMatches[1].groupValues[1].replace(",", ".").toDoubleOrNull()
        } else {
            distMatches.firstOrNull()?.groupValues?.get(1)?.replace(",", ".")?.toDoubleOrNull()
        }

        // Duración — tomar la SEGUNDA coincidencia de min (la del viaje, no del pickup)
        val durRegex = Regex("""(\d+)\s*min""")
        val durMatches = durRegex.findAll(fullText).toList()
        val dur = if (durMatches.size >= 2) {
            durMatches[1].groupValues[1].toIntOrNull()
        } else {
            durMatches.firstOrNull()?.groupValues?.get(1)?.toIntOrNull()
        }

        Log.d("GigParser", "fare=$fare | dist=$dist | dur=$dur")

        if (fare == null || dist == null || dur == null) {
            Timber.d("DiDi parser: datos incompletos — fare=$fare, dist=$dist, dur=$dur")
            return null
        }

        Timber.d("DiDi parser: oferta detectada — fare=$fare, dist=$dist km, dur=$dur min")
        return TripOfferRawData(fare, dist, dur, PLATFORM_DIDI)
    }

    companion object {
        const val PLATFORM_DIDI = "DiDi"
    }
}

/**
 * Extrae todos los textos de nodos hoja del árbol de accesibilidad.
 */
fun extractLeafTexts(node: AccessibilityNodeInfo): List<String> {
    val texts = mutableListOf<String>()
    if (node.childCount == 0) {
        node.text?.toString()?.let { texts.add(it) }
    } else {
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                texts.addAll(extractLeafTexts(child))
            }
        }
    }
    return texts
}
