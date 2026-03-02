package com.gigassist.core.di

import android.content.Context
import androidx.room.Room
import com.gigassist.data.local.GigAssistDatabase
import com.gigassist.data.local.dao.DriverSettingsDao
import com.gigassist.data.local.dao.ExpenseDao
import com.gigassist.data.local.dao.ShiftDao
import com.gigassist.data.local.dao.TripRecordDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): GigAssistDatabase {
        return Room.databaseBuilder(
            context,
            GigAssistDatabase::class.java,
            "gigassist_database"
        ).build()
    }

    @Provides
    fun provideDriverSettingsDao(db: GigAssistDatabase): DriverSettingsDao = db.driverSettingsDao()

    @Provides
    fun provideExpenseDao(db: GigAssistDatabase): ExpenseDao = db.expenseDao()

    @Provides
    fun provideShiftDao(db: GigAssistDatabase): ShiftDao = db.shiftDao()

    @Provides
    fun provideTripRecordDao(db: GigAssistDatabase): TripRecordDao = db.tripRecordDao()
}
