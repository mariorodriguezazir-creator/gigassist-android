package com.gigassist.domain.model

/**
 * Datos crudos extraídos de una oferta de viaje por el parser.
 * Representan la información tal como se lee del árbol de accesibilidad
 * de Uber o DiDi, antes de ser evaluada por TripEvaluator.
 *
 * @param fare tarifa ofrecida en moneda local.
 * @param distanceKm distancia del viaje en kilómetros (siempre km internamente).
 * @param durationMin duración estimada del viaje en minutos.
 * @param platform identificador de la plataforma ("UBER" o "DIDI").
 */
data class TripOfferRawData(
    val fare: Double,
    val distanceKm: Double,
    val durationMin: Int,
    val platform: String
)
