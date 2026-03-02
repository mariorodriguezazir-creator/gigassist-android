package com.gigassist.domain.model

/**
 * Configuración del modo aeropuerto.
 * Añade tiempos extra al cálculo de duración total del viaje.
 */
data class AirportConfig(
    val waitingMin: Double = 0.0,
    val boardingMin: Double = 0.0,
    val returnsEmpty: Boolean = false,
    val estimatedReturnMin: Double = 0.0
)
