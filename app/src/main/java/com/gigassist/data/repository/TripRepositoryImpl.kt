package com.gigassist.data.repository

import com.gigassist.data.local.dao.TripRecordDao
import com.gigassist.data.local.entity.TripRecordEntity
import com.gigassist.domain.model.EvaluationResult
import com.gigassist.domain.model.TripRecord
import com.gigassist.domain.repository.TripRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class TripRepositoryImpl @Inject constructor(
    private val dao: TripRecordDao
) : TripRepository {

    override suspend fun saveTripRecord(record: TripRecord) {
        dao.insert(record.toEntity())
    }

    override fun getTripsForShift(shiftId: Long): Flow<List<TripRecord>> {
        return dao.getTripsForShift(shiftId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getTripsForDay(date: LocalDate): Flow<List<TripRecord>> {
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        return dao.getTripsForDay(start, end).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getTripsForDateRange(start: LocalDate, end: LocalDate): Flow<List<TripRecord>> {
        val zone = ZoneId.systemDefault()
        val startMillis = start.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillis = end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        return dao.getTripsForDay(startMillis, endMillis).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    private fun TripRecord.toEntity() = TripRecordEntity(
        id = id,
        shiftId = shiftId,
        fare = fare,
        distanceKm = distanceKm,
        durationMin = durationMin,
        platform = platform,
        evaluationResult = evaluationResult.name,
        timestamp = timestamp
    )

    private fun TripRecordEntity.toDomain() = TripRecord(
        id = id,
        shiftId = shiftId,
        fare = fare,
        distanceKm = distanceKm,
        durationMin = durationMin,
        platform = platform,
        evaluationResult = try {
            EvaluationResult.valueOf(evaluationResult)
        } catch (e: IllegalArgumentException) {
            EvaluationResult.POOR
        },
        timestamp = timestamp
    )
}
