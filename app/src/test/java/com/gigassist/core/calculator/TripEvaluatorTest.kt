package com.gigassist.core.calculator

import com.gigassist.domain.model.AirportConfig
import com.gigassist.domain.model.CountryCode
import com.gigassist.domain.model.DistanceUnit
import com.gigassist.domain.model.DriverSettings
import com.gigassist.domain.model.EvaluationResult
import com.gigassist.domain.model.TripEvaluatorInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests unitarios para TripEvaluator.
 * Casos basados en trip_evaluator.md skill.
 * Verifican fórmulas de cálculo, modo aeropuerto y edge cases.
 */
class TripEvaluatorTest {

    private lateinit var evaluator: TripEvaluator

    // Configuración base: conductor en Rep. Dominicana
    // minRatePerHour = 600 DOP/h, minRatePerKm = 35 DOP/km
    // operatingCostPerHour = 250 DOP/h
    private val baseSettings = DriverSettings(
        countryCode = CountryCode.DO,
        minRatePerHour = 600.0,
        minRatePerDistanceUnit = 35.0,
        operatingCostPerHour = 250.0,
        distanceUnit = DistanceUnit.KM
    )

    @Before
    fun setUp() {
        evaluator = TripEvaluator()
    }

    @Test
    fun `viaje rentable - cumple ambos umbrales devuelve GOOD`() {
        // fare=450, dist=10km, dur=20min
        // ratePerHour = (450/20)*60 = 1350 → >= 600 ✓
        // ratePerKm = 450/10 = 45 → >= 35 ✓
        val input = TripEvaluatorInput(
            fare = 450.0,
            distanceKm = 10.0,
            durationMin = 20,
            settings = baseSettings
        )
        val result = evaluator.evaluate(input)

        assertEquals(EvaluationResult.GOOD, result.evaluationResult)
        assertEquals(1350.0, result.ratePerHour, 0.01)
        assertEquals(45.0, result.ratePerDistanceUnit, 0.01)
    }

    @Test
    fun `viaje no rentable - no cumple ninguno devuelve POOR`() {
        // fare=100, dist=15km, dur=30min
        // ratePerHour = (100/30)*60 = 200 → < 600 ✗
        // ratePerKm = 100/15 = 6.67 → < 35 ✗
        val input = TripEvaluatorInput(
            fare = 100.0,
            distanceKm = 15.0,
            durationMin = 30,
            settings = baseSettings
        )
        val result = evaluator.evaluate(input)

        assertEquals(EvaluationResult.POOR, result.evaluationResult)
    }

    @Test
    fun `viaje FAIR - cumple solo umbral de hora`() {
        // fare=350, dist=15km, dur=15min
        // ratePerHour = (350/15)*60 = 1400 → >= 600 ✓
        // ratePerKm = 350/15 = 23.33 → < 35 ✗
        val input = TripEvaluatorInput(
            fare = 350.0,
            distanceKm = 15.0,
            durationMin = 15,
            settings = baseSettings
        )
        val result = evaluator.evaluate(input)

        assertEquals(EvaluationResult.FAIR, result.evaluationResult)
    }

    @Test
    fun `viaje FAIR - cumple solo umbral de km`() {
        // fare=400, dist=8km, dur=60min
        // ratePerHour = (400/60)*60 = 400 → < 600 ✗
        // ratePerKm = 400/8 = 50 → >= 35 ✓
        val input = TripEvaluatorInput(
            fare = 400.0,
            distanceKm = 8.0,
            durationMin = 60,
            settings = baseSettings
        )
        val result = evaluator.evaluate(input)

        assertEquals(EvaluationResult.FAIR, result.evaluationResult)
    }

    @Test
    fun `ganancia neta descuenta costos operativos correctamente`() {
        // fare=450, dur=20min, operatingCost=250/h
        // netFare = 450 - (250 * 20/60) = 450 - 83.33 = 366.67
        val input = TripEvaluatorInput(
            fare = 450.0,
            distanceKm = 10.0,
            durationMin = 20,
            settings = baseSettings
        )
        val result = evaluator.evaluate(input)

        assertEquals(366.67, result.estimatedNetFare, 0.01)
    }

    @Test
    fun `modo aeropuerto - retorno vacio aumenta duracion total`() {
        // fare=800, dist=25km, dur=30min
        // airport: wait=20, board=3, returnsEmpty=true, return=15
        // totalDuration = 30 + 20 + 3 + 15 = 68 min
        // ratePerHour = (800/68)*60 = 705.88 → >= 600 ✓
        val airport = AirportConfig(
            waitingMin = 20.0,
            boardingMin = 3.0,
            returnsEmpty = true,
            estimatedReturnMin = 15.0
        )
        val input = TripEvaluatorInput(
            fare = 800.0,
            distanceKm = 25.0,
            durationMin = 30,
            settings = baseSettings,
            airportConfig = airport
        )
        val result = evaluator.evaluate(input)

        assertTrue(
            "ratePerHour con aeropuerto debería ser > 600, fue: ${result.ratePerHour}",
            result.ratePerHour > 600.0
        )
        assertEquals(705.88, result.ratePerHour, 0.01)
    }

    @Test
    fun `modo aeropuerto - sin retorno vacio no suma returnMin`() {
        // fare=800, dist=25km, dur=30min
        // airport: wait=20, board=3, returnsEmpty=false
        // totalDuration = 30 + 20 + 3 + 0 = 53 min
        // ratePerHour = (800/53)*60 = 905.66
        val airport = AirportConfig(
            waitingMin = 20.0,
            boardingMin = 3.0,
            returnsEmpty = false,
            estimatedReturnMin = 15.0
        )
        val input = TripEvaluatorInput(
            fare = 800.0,
            distanceKm = 25.0,
            durationMin = 30,
            settings = baseSettings,
            airportConfig = airport
        )
        val result = evaluator.evaluate(input)

        assertEquals(905.66, result.ratePerHour, 0.01)
    }

    @Test
    fun `edge case - fare cero devuelve POOR con valores cero`() {
        val input = TripEvaluatorInput(
            fare = 0.0,
            distanceKm = 10.0,
            durationMin = 15,
            settings = baseSettings
        )
        val result = evaluator.evaluate(input)

        assertEquals(EvaluationResult.POOR, result.evaluationResult)
        assertEquals(0.0, result.ratePerHour, 0.01)
        assertEquals(0.0, result.ratePerDistanceUnit, 0.01)
        assertEquals(0.0, result.estimatedNetFare, 0.01)
    }

    @Test
    fun `edge case - duracion cero devuelve POOR con valores cero`() {
        val input = TripEvaluatorInput(
            fare = 500.0,
            distanceKm = 10.0,
            durationMin = 0,
            settings = baseSettings
        )
        val result = evaluator.evaluate(input)

        assertEquals(EvaluationResult.POOR, result.evaluationResult)
        assertEquals(0.0, result.ratePerHour, 0.01)
    }

    @Test
    fun `edge case - distancia cero devuelve POOR con valores cero`() {
        val input = TripEvaluatorInput(
            fare = 500.0,
            distanceKm = 0.0,
            durationMin = 15,
            settings = baseSettings
        )
        val result = evaluator.evaluate(input)

        assertEquals(EvaluationResult.POOR, result.evaluationResult)
        assertEquals(0.0, result.ratePerDistanceUnit, 0.01)
    }

    @Test
    fun `edge case - valores negativos devuelve POOR`() {
        val input = TripEvaluatorInput(
            fare = -100.0,
            distanceKm = 10.0,
            durationMin = 15,
            settings = baseSettings
        )
        val result = evaluator.evaluate(input)

        assertEquals(EvaluationResult.POOR, result.evaluationResult)
    }

    @Test
    fun `conversion a millas calcula correctamente ratePerDistanceUnit`() {
        // fare=500, dist=10km, dur=20min
        // ratePerKm = 500/10 = 50
        // ratePerMile = 50 / 0.621371 = 80.47
        val milesSettings = baseSettings.copy(
            distanceUnit = DistanceUnit.MILES,
            minRatePerDistanceUnit = 2.0
        )
        val input = TripEvaluatorInput(
            fare = 500.0,
            distanceKm = 10.0,
            durationMin = 20,
            settings = milesSettings
        )
        val result = evaluator.evaluate(input)

        assertEquals(80.47, result.ratePerDistanceUnit, 0.01)
    }
}
