package com.gigassist.domain.usecase

import com.gigassist.domain.model.ShiftStats
import com.gigassist.domain.model.TripRecord
import com.gigassist.domain.repository.TripRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Calcula estadísticas de un turno dado sus viajes.
 */
class GetShiftStatsUseCase @Inject constructor(
    private val tripRepository: TripRepository
) {
    operator fun invoke(shiftId: Long, operatingCostPerHour: Double): Flow<ShiftStats> {
        return tripRepository.getTripsForShift(shiftId).map { trips ->
            calculateStats(trips, operatingCostPerHour)
        }
    }

    companion object {
        fun calculateStats(trips: List<TripRecord>, operatingCostPerHour: Double): ShiftStats {
            if (trips.isEmpty()) return ShiftStats()

            val totalTrips = trips.size
            val grossEarnings = trips.sumOf { it.fare }
            val totalMinutes = trips.sumOf { it.durationMin }
            val activeHours = totalMinutes / 60.0
            val operatingCost = operatingCostPerHour * activeHours
            val netEarnings = grossEarnings - operatingCost
            val averageRatePerHour = if (activeHours > 0) grossEarnings / activeHours else 0.0
            val tripsPerPlatform = trips.groupBy { it.platform }.mapValues { it.value.size }

            return ShiftStats(
                totalTrips = totalTrips,
                grossEarnings = grossEarnings,
                netEarnings = netEarnings,
                activeHours = activeHours,
                averageRatePerHour = averageRatePerHour,
                tripsPerPlatform = tripsPerPlatform
            )
        }
    }
}
