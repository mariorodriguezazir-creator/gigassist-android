package com.gigassist.domain.model

/**
 * Resultado de la evaluación de rentabilidad de un viaje.
 * - GOOD: cumple ambos umbrales ($/hora y $/km).
 * - FAIR: cumple solo uno de los dos umbrales.
 * - POOR: no cumple ninguno de los umbrales.
 */
enum class EvaluationResult {
    GOOD,
    FAIR,
    POOR
}
