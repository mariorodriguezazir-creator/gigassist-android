package com.gigassist.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entidad Room para la configuración del conductor.
 * Se usa un id fijo (1) porque solo hay una configuración activa.
 */
@Entity(tableName = "driver_settings")
data class DriverSettingsEntity(
    @PrimaryKey
    val id: Int = 1,
    val countryCode: String,
    val currencyCode: String,
    val distanceUnit: String,
    val minRatePerHour: Double,
    val minRatePerDistanceUnit: Double,
    val operatingCostPerHour: Double,
    val airportWaitingMin: Double? = null,
    val airportBoardingMin: Double? = null,
    val airportReturnsEmpty: Boolean? = null,
    val airportEstimatedReturnMin: Double? = null
)
