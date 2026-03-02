package com.gigassist.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gigassist.data.local.entity.DriverSettingsEntity

@Dao
interface DriverSettingsDao {

    @Query("SELECT * FROM driver_settings WHERE id = 1")
    suspend fun getSettings(): DriverSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(settings: DriverSettingsEntity)
}
