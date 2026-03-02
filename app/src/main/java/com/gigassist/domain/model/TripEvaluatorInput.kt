package com.gigassist.domain.model

/**
 * Input del evaluador de viajes.
 * Contiene los datos crudos del viaje más la configuración del conductor.
 * Se usa como parámetro único de TripEvaluator.evaluate().
 *
 * @param fare tarifa ofrecida en moneda local.
 * @param distanceKm distancia en kilómetros (siempre km internamente).
 * @param durationMin duración estimada en minutos.
 * @param settings configuración actual del conductor.
 * @param airportConfig configuración de modo aeropuerto (null si no aplica).
 */
data class TripEvaluatorInput(
    val fare: Double,
    val distanceKm: Double,
    val durationMin: Int,
    val settings: DriverSettings,
    val airportConfig: AirportConfig? = null
)
