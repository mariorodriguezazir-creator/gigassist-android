package com.gigassist.domain.model

/**
 * Resultado calculado por TripEvaluator.
 * Contiene las métricas de rentabilidad del viaje evaluado.
 *
 * @param ratePerHour ganancia estimada por hora en moneda local.
 * @param ratePerDistanceUnit ganancia por unidad de distancia (km o mi según settings).
 * @param estimatedNetFare ganancia neta estimada descontando costos operativos.
 * @param evaluationResult clasificación del viaje: GOOD, FAIR o POOR.
 */
data class TripRates(
    val ratePerHour: Double,
    val ratePerDistanceUnit: Double,
    val estimatedNetFare: Double,
    val evaluationResult: EvaluationResult
)
