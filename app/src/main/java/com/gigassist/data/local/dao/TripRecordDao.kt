package com.gigassist.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.gigassist.data.local.entity.TripRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TripRecordDao {

    @Insert
    suspend fun insert(record: TripRecordEntity)

    @Query("SELECT * FROM trip_records WHERE timestamp >= :timestampStart AND timestamp <= :timestampEnd ORDER BY timestamp DESC")
    fun getTripsForDay(timestampStart: Long, timestampEnd: Long): Flow<List<TripRecordEntity>>

    @Query("SELECT * FROM trip_records WHERE shiftId = :shiftId ORDER BY timestamp DESC")
    fun getTripsForShift(shiftId: Long): Flow<List<TripRecordEntity>>
}
