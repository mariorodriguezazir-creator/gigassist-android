# SKILL: TripEvaluator — Motor de Cálculo de Rentabilidad

## Propósito
UseCase puro que decide si un viaje vale la pena.
Sin dependencias de Android → 100% testeable con JUnit puro.

---

## Inputs

data class TripEvaluatorInput(
    val fare: Double,
    val distanceKm: Double,
    val durationMin: Int,
    val settings: DriverSettings,
    val airportConfig: AirportConfig? = null
)

---

## Outputs

data class TripRates(
    val ratePerHour: Double,
    val ratePerDistanceUnit: Double,
    val estimatedNetFare: Double,
    val evaluationResult: EvaluationResult
)

enum class EvaluationResult { GOOD, FAIR, POOR }

---

## Implementación

class TripEvaluator {
    fun evaluate(input: TripEvaluatorInput): TripRates {
        // Protección contra división por cero
        if (input.fare <= 0 || input.distanceKm <= 0
            || input.durationMin <= 0) {
            return TripRates(0.0, 0.0, 0.0, EvaluationResult.POOR)
        }

        val totalDuration = calculateTotalDuration(input)

        val ratePerHour = (input.fare / totalDuration) * 60

        val ratePerKm = input.fare / input.distanceKm

        val ratePerUnit = when (input.settings.distanceUnit) {
            DistanceUnit.KM    -> ratePerKm
            DistanceUnit.MILES -> ratePerKm / 0.621371
        }

        val netFare = input.fare -
            (input.settings.operatingCostPerHour * input.durationMin / 60)

        val result = when {
            ratePerHour >= input.settings.minRatePerHour &&
            ratePerUnit >= input.settings.minRatePerDistanceUnit
                -> EvaluationResult.GOOD
            ratePerHour >= input.settings.minRatePerHour ||
            ratePerUnit >= input.settings.minRatePerDistanceUnit
                -> EvaluationResult.FAIR
            else -> EvaluationResult.POOR
        }

        return TripRates(ratePerHour, ratePerUnit, netFare, result)
    }

    private fun calculateTotalDuration(input: TripEvaluatorInput): Double {
        val airport = input.airportConfig ?: return input.durationMin.toDouble()
        return input.durationMin.toDouble() +
            airport.waitingMin +
            airport.boardingMin +
            if (airport.returnsEmpty) airport.estimatedReturnMin else 0.0
    }
}

---

## Tests obligatorios

class TripEvaluatorTest {

    private val evaluator = TripEvaluator()
    private val baseSettings = DriverSettings(
        minRatePerHour = 600.0,
        minRatePerDistanceUnit = 35.0,
        operatingCostPerHour = 250.0,
        distanceUnit = DistanceUnit.KM
    )

    @Test
    fun `caso base - viaje rentable`() {
        val input = TripEvaluatorInput(
            fare = 450.0, distanceKm = 10.0,
            durationMin = 20, settings = baseSettings
        )
        val result = evaluator.evaluate(input)
        assert(result.evaluationResult == EvaluationResult.GOOD)
    }

    @Test
    fun `caso base - viaje no rentable`() {
        val input = TripEvaluatorInput(
            fare = 100.0, distanceKm = 15.0,
            durationMin = 30, settings = baseSettings
        )
        val result = evaluator.evaluate(input)
        assert(result.evaluationResult == EvaluationResult.POOR)
    }

    @Test
    fun `modo aeropuerto - retorno vacio`() {
        val airport = AirportConfig(
            waitingMin = 20, boardingMin = 3,
            returnsEmpty = true, estimatedReturnMin = 15
        )
        val input = TripEvaluatorInput(
            fare = 800.0, distanceKm = 25.0,
            durationMin = 30, settings = baseSettings,
            airportConfig = airport
        )
        val result = evaluator.evaluate(input)
        // Con tiempo total = 30+20+3+15 = 68 min
        // ratePerHour = (800/68)*60 = 705 → GOOD
        assert(result.ratePerHour > 600.0)
    }

    @Test
    fun `edge case - fare cero devuelve POOR`() {
        val input = TripEvaluatorInput(
            fare = 0.0, distanceKm = 10.0,
            durationMin = 15, settings = baseSettings
        )
        val result = evaluator.evaluate(input)
        assert(result.evaluationResult == EvaluationResult.POOR)
    }

    @Test
    fun `edge case - duracion cero devuelve POOR`() {
        val input = TripEvaluatorInput(
            fare = 500.0, distanceKm = 10.0,
            durationMin = 0, settings = baseSettings
        )
        val result = evaluator.evaluate(input)
        assert(result.evaluationResult == EvaluationResult.POOR)
    }
}
