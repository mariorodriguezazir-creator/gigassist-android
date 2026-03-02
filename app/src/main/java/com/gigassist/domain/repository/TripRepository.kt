package com.gigassist.domain.repository

import com.gigassist.domain.model.TripRecord
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Repositorio para registros de viajes evaluados.
 */
interface TripRepository {
    suspend fun saveTripRecord(record: TripRecord)
    fun getTripsForShift(shiftId: Long): Flow<List<TripRecord>>
    fun getTripsForDay(date: LocalDate): Flow<List<TripRecord>>
    fun getTripsForDateRange(start: LocalDate, end: LocalDate): Flow<List<TripRecord>>
}
