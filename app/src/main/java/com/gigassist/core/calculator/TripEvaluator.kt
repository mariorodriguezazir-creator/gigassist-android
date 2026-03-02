package com.gigassist.core.calculator

import com.gigassist.domain.model.AirportConfig
import com.gigassist.domain.model.DistanceUnit
import com.gigassist.domain.model.EvaluationResult
import com.gigassist.domain.model.TripEvaluatorInput
import com.gigassist.domain.model.TripRates
import javax.inject.Inject

/**
 * UseCase puro que evalúa la rentabilidad de un viaje.
 *
 * Sin dependencias de Android → 100% testeable con JUnit puro.
 * Todas las distancias internas están en km.
 * La conversión a millas solo ocurre al calcular ratePerDistanceUnit
 * cuando el conductor usa DistanceUnit.MILES.
 */
class TripEvaluator @Inject constructor() {

    fun evaluate(input: TripEvaluatorInput): TripRates {
        // Protección contra datos inválidos o división por cero
        if (input.fare <= 0 || input.distanceKm <= 0 || input.durationMin <= 0) {
            return TripRates(
                ratePerHour = 0.0,
                ratePerDistanceUnit = 0.0,
                estimatedNetFare = 0.0,
                evaluationResult = EvaluationResult.POOR
            )
        }

        val totalDurationMin = calculateTotalDuration(input)

        // Ganancia por hora: (tarifa / duración en minutos) × 60
        val ratePerHour = (input.fare / totalDurationMin) * 60

        // Ganancia por km (siempre calculada internamente en km)
        val ratePerKm = input.fare / input.distanceKm

        // Ganancia por unidad de distancia (km o millas según settings)
        val ratePerDistanceUnit = when (input.settings.distanceUnit) {
            DistanceUnit.KM -> ratePerKm
            // Para millas: fare / (distanceKm * 0.621371) = ratePerKm / 0.621371
            DistanceUnit.MILES -> ratePerKm / KM_TO_MILES_FACTOR
        }

        // Ganancia neta: tarifa - (costo operativo por hora × duración en horas)
        val estimatedNetFare = input.fare -
            (input.settings.operatingCostPerHour * input.durationMin / 60.0)

        // Evaluar contra umbrales del conductor
        val meetsHourThreshold = ratePerHour >= input.settings.minRatePerHour
        val meetsDistanceThreshold = ratePerDistanceUnit >= input.settings.minRatePerDistanceUnit

        val evaluationResult = when {
            meetsHourThreshold && meetsDistanceThreshold -> EvaluationResult.GOOD
            meetsHourThreshold || meetsDistanceThreshold -> EvaluationResult.FAIR
            else -> EvaluationResult.POOR
        }

        return TripRates(
            ratePerHour = ratePerHour,
            ratePerDistanceUnit = ratePerDistanceUnit,
            estimatedNetFare = estimatedNetFare,
            evaluationResult = evaluationResult
        )
    }

    /**
     * Calcula la duración total del viaje considerando modo aeropuerto.
     * En modo normal devuelve solo la duración base.
     * En modo aeropuerto suma: espera + abordaje + (regreso vacío si aplica).
     */
    private fun calculateTotalDuration(input: TripEvaluatorInput): Double {
        val airport = input.airportConfig ?: return input.durationMin.toDouble()
        return input.durationMin.toDouble() +
            airport.waitingMin +
            airport.boardingMin +
            if (airport.returnsEmpty) airport.estimatedReturnMin else 0.0
    }

    companion object {
        private const val KM_TO_MILES_FACTOR = 0.621371
    }
}
