package com.gigassist.domain.model

/**
 * Configuración del modo aeropuerto.
 * Añade tiempos extra al cálculo de duración total del viaje:
 * - waitingMin: tiempo de espera en el aeropuerto antes de recoger al pasajero.
 * - boardingMin: tiempo de abordaje (carga de equipaje, etc.).
 * - returnsEmpty: si el conductor regresa vacío (sin pasajero de vuelta).
 * - estimatedReturnMin: tiempo estimado del regreso vacío.
 */
data class AirportConfig(
    val waitingMin: Double = 0.0,
    val boardingMin: Double = 0.0,
    val returnsEmpty: Boolean = false,
    val estimatedReturnMin: Double = 0.0
)
