package com.gigassist.domain.usecase

import com.gigassist.domain.model.EvaluationResult
import com.gigassist.domain.model.TripRecord
import com.gigassist.domain.repository.TripRepository
import javax.inject.Inject

/**
 * Guarda un registro de viaje evaluado en la base de datos.
 */
class SaveTripRecordUseCase @Inject constructor(
    private val repository: TripRepository
) {
    suspend operator fun invoke(
        fare: Double,
        distanceKm: Double,
        durationMin: Int,
        platform: String,
        evaluationResult: EvaluationResult,
        shiftId: Long
    ) {
        val record = TripRecord(
            shiftId = shiftId,
            fare = fare,
            distanceKm = distanceKm,
            durationMin = durationMin,
            platform = platform,
            evaluationResult = evaluationResult
        )
        repository.saveTripRecord(record)
    }
}
