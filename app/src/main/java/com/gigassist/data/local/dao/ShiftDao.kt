package com.gigassist.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.gigassist.data.local.entity.ShiftEntity

@Dao
interface ShiftDao {

    @Insert
    suspend fun startShift(shift: ShiftEntity): Long

    @Query("UPDATE shifts SET endTime = :endTime WHERE id = :id")
    suspend fun endShift(id: Long, endTime: Long)

    @Query("SELECT * FROM shifts WHERE endTime IS NULL LIMIT 1")
    suspend fun getActiveShift(): ShiftEntity?
}
