package com.gigassist.domain.model

/**
 * Modelo de presentación para mostrar la última evaluación de viaje.
 */
data class TripEvaluationUiModel(
    val fare: Double,
    val ratePerHour: Double,
    val ratePerDistanceUnit: Double,
    val estimatedNetFare: Double,
    val evaluationResult: EvaluationResult,
    val platform: String,
    val distanceKm: Double,
    val durationMin: Int,
    val currencySymbol: String,
    val distanceUnitLabel: String
)
