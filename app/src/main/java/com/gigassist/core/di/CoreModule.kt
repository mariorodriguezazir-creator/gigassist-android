package com.gigassist.core.di

import com.gigassist.core.calculator.TripEvaluator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Módulo Hilt para proveer dependencias del core.
 */
@Module
@InstallIn(SingletonComponent::class)
object CoreModule {

    @Provides
    @Singleton
    fun provideTripEvaluator(): TripEvaluator {
        return TripEvaluator()
    }
}
