package com.gigassist.core.parser

import android.view.accessibility.AccessibilityNodeInfo
import com.gigassist.domain.model.TripOfferRawData
import timber.log.Timber
import javax.inject.Inject

/**
 * Parser de ofertas de viaje para Uber Driver.
 * Extrae tarifa, distancia y duración de los textos del árbol
 * de accesibilidad usando regex.
 *
 * Los regex están basados en los formatos observados en la app
 * de Uber Driver en países hispanohablantes.
 */
class UberTripOfferParser @Inject constructor() : TripOfferParser {

    override fun parse(rootNode: AccessibilityNodeInfo): TripOfferRawData? {
        val texts = extractLeafTexts(rootNode)
        if (texts.isEmpty()) return null

        val fare = extractFare(texts)
        val distanceKm = extractDistance(texts)
        val durationMin = extractDuration(texts)

        if (fare == null || distanceKm == null || durationMin == null) {
            Timber.d("Uber parser: datos incompletos — fare=$fare, dist=$distanceKm, dur=$durationMin")
            return null
        }

        Timber.d("Uber parser: oferta detectada — fare=$fare, dist=$distanceKm km, dur=$durationMin min")
        return TripOfferRawData(fare, distanceKm, durationMin, PLATFORM_UBER)
    }

    companion object {
        const val PLATFORM_UBER = "UBER"

        // Tarifa: captura número después de símbolo de moneda
        // Ejemplos: "RD$ 450", "$ 185.00", "COP 15000", "S/ 25.50"
        val fareRegex = Regex("""(?:RD\$|COP|MXN|ARS|PEN|CLP|VES|BOB|PYG|UYU|EUR|USD|S/|Bs\.|₲|€|\$)\s?(\d+[\.,]?\d*)""")

        // Distancia: captura número antes de km o mi
        // Ejemplos: "12 km", "12.5 km", "7.8 mi"
        val distanceRegex = Regex("""(\d+[\.,]?\d*)\s?(?:km|mi|millas)""", RegexOption.IGNORE_CASE)

        // Duración: captura número antes de min, mins, minutos
        // Ejemplos: "18 min", "45 minutos", "1 h 08"
        val durationRegex = Regex("""(\d+)\s?(?:min|mins|minutos)""", RegexOption.IGNORE_CASE)
    }
}

/**
 * Parser de ofertas de viaje para DiDi Driver.
 * Usa la misma lógica de extracción que Uber pero con
 * ajustes específicos si se detectan diferencias en los
 * formatos de texto de DiDi.
 *
 * Nota: verificar en dispositivo real el formato exacto de
 * DiDi en cada país. Puede variar por región.
 */
class DiDiTripOfferParser @Inject constructor() : TripOfferParser {

    override fun parse(rootNode: AccessibilityNodeInfo): TripOfferRawData? {
        val texts = extractLeafTexts(rootNode)
        if (texts.isEmpty()) return null

        val fare = extractFare(texts)
        val distanceKm = extractDistance(texts)
        val durationMin = extractDuration(texts)

        if (fare == null || distanceKm == null || durationMin == null) {
            Timber.d("DiDi parser: datos incompletos — fare=$fare, dist=$distanceKm, dur=$durationMin")
            return null
        }

        Timber.d("DiDi parser: oferta detectada — fare=$fare, dist=$distanceKm km, dur=$durationMin min")
        return TripOfferRawData(fare, distanceKm, durationMin, PLATFORM_DIDI)
    }

    companion object {
        const val PLATFORM_DIDI = "DIDI"

        // DiDi usa formatos similares; ajustar si se detectan diferencias
        val fareRegex = UberTripOfferParser.fareRegex
        val distanceRegex = UberTripOfferParser.distanceRegex
        val durationRegex = UberTripOfferParser.durationRegex
    }
}

/**
 * Extrae todos los textos de nodos hoja del árbol de accesibilidad.
 * Recorre recursivamente el árbol y recolecta el texto de cada
 * nodo que no tiene hijos (nodo hoja).
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

/**
 * Extrae la tarifa del primer texto que coincide con el patrón de moneda.
 * Reemplaza comas por puntos para manejar formatos numéricos locales.
 */
fun extractFare(texts: List<String>): Double? {
    for (text in texts) {
        val match = UberTripOfferParser.fareRegex.find(text)
        if (match != null) {
            return match.groupValues[1].replace(",", ".").toDoubleOrNull()
        }
    }
    return null
}

/**
 * Extrae la distancia del primer texto que coincide con el patrón km/mi.
 */
fun extractDistance(texts: List<String>): Double? {
    for (text in texts) {
        val match = UberTripOfferParser.distanceRegex.find(text)
        if (match != null) {
            return match.groupValues[1].replace(",", ".").toDoubleOrNull()
        }
    }
    return null
}

/**
 * Extrae la duración en minutos del primer texto que coincide con el patrón.
 */
fun extractDuration(texts: List<String>): Int? {
    for (text in texts) {
        val match = UberTripOfferParser.durationRegex.find(text)
        if (match != null) {
            return match.groupValues[1].toIntOrNull()
        }
    }
    return null
}
