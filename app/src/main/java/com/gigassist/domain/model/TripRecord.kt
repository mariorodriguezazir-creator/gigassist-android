package com.gigassist.domain.model

/**
 * Modelo de dominio para un viaje evaluado y registrado.
 */
data class TripRecord(
    val id: Long = 0,
    val shiftId: Long,
    val fare: Double,
    val distanceKm: Double,
    val durationMin: Int,
    val platform: String,
    val evaluationResult: EvaluationResult,
    val timestamp: Long = System.currentTimeMillis()
)
