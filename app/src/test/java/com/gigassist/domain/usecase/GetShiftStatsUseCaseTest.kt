package com.gigassist.domain.usecase

import com.gigassist.domain.model.EvaluationResult
import com.gigassist.domain.model.ShiftStats
import com.gigassist.domain.model.TripRecord
import org.junit.Assert.assertEquals
import org.junit.Test

class GetShiftStatsUseCaseTest {

    @Test
    fun `turno sin viajes retorna stats vacias`() {
        val stats = GetShiftStatsUseCase.calculateStats(emptyList(), 250.0)

        assertEquals(0, stats.totalTrips)
        assertEquals(0.0, stats.grossEarnings, 0.01)
        assertEquals(0.0, stats.netEarnings, 0.01)
        assertEquals(0.0, stats.activeHours, 0.01)
        assertEquals(0.0, stats.averageRatePerHour, 0.01)
        assertEquals(emptyMap<String, Int>(), stats.tripsPerPlatform)
    }

    @Test
    fun `turno con viajes calcula stats correctamente`() {
        val trips = listOf(
            TripRecord(id = 1, shiftId = 1, fare = 450.0, distanceKm = 10.0, durationMin = 20,
                platform = "Uber", evaluationResult = EvaluationResult.GOOD),
            TripRecord(id = 2, shiftId = 1, fare = 300.0, distanceKm = 8.0, durationMin = 15,
                platform = "DiDi", evaluationResult = EvaluationResult.FAIR),
            TripRecord(id = 3, shiftId = 1, fare = 200.0, distanceKm = 5.0, durationMin = 10,
                platform = "Uber", evaluationResult = EvaluationResult.POOR)
        )
        val operatingCostPerHour = 250.0
        val stats = GetShiftStatsUseCase.calculateStats(trips, operatingCostPerHour)

        assertEquals(3, stats.totalTrips)
        assertEquals(950.0, stats.grossEarnings, 0.01)

        // activeHours = 45min / 60 = 0.75h
        assertEquals(0.75, stats.activeHours, 0.01)

        // netEarnings = 950 - (250 * 0.75) = 950 - 187.5 = 762.5
        assertEquals(762.5, stats.netEarnings, 0.01)

        // averageRatePerHour = 950 / 0.75 = 1266.67
        assertEquals(1266.67, stats.averageRatePerHour, 0.01)

        // platforms
        assertEquals(mapOf("Uber" to 2, "DiDi" to 1), stats.tripsPerPlatform)
    }

    @Test
    fun `netEarnings se calcula correctamente con costos operativos altos`() {
        val trips = listOf(
            TripRecord(id = 1, shiftId = 1, fare = 100.0, distanceKm = 5.0, durationMin = 30,
                platform = "Uber", evaluationResult = EvaluationResult.POOR)
        )
        // activeHours = 0.5h, operatingCost = 500 * 0.5 = 250
        // netEarnings = 100 - 250 = -150
        val stats = GetShiftStatsUseCase.calculateStats(trips, 500.0)
        assertEquals(-150.0, stats.netEarnings, 0.01)
    }
}
