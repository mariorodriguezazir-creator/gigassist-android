package com.gigassist.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.gigassist.data.local.dao.DriverSettingsDao
import com.gigassist.data.local.dao.ExpenseDao
import com.gigassist.data.local.dao.ShiftDao
import com.gigassist.data.local.dao.TripRecordDao
import com.gigassist.data.local.entity.DriverSettingsEntity
import com.gigassist.data.local.entity.ExpenseEntity
import com.gigassist.data.local.entity.ShiftEntity
import com.gigassist.data.local.entity.TripRecordEntity

@Database(
    entities = [
        DriverSettingsEntity::class,
        ExpenseEntity::class,
        ShiftEntity::class,
        TripRecordEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class GigAssistDatabase : RoomDatabase() {
    abstract fun driverSettingsDao(): DriverSettingsDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun shiftDao(): ShiftDao
    abstract fun tripRecordDao(): TripRecordDao
}
