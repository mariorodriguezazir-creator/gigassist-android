package com.gigassist.data.local.mapper

import com.gigassist.data.local.entity.DriverSettingsEntity
import com.gigassist.data.local.entity.ExpenseEntity
import com.gigassist.data.local.entity.ShiftEntity
import com.gigassist.domain.model.AirportConfig
import com.gigassist.domain.model.CountryCode
import com.gigassist.domain.model.DistanceUnit
import com.gigassist.domain.model.DriverSettings
import com.gigassist.domain.model.Expense
import com.gigassist.domain.model.ExpenseCategory
import com.gigassist.domain.model.Shift

/**
 * Mappers entre entidades Room y modelos de dominio.
 */

// ── DriverSettings ──

fun DriverSettingsEntity.toDomain(): DriverSettings {
    val country = try {
        CountryCode.valueOf(countryCode)
    } catch (e: IllegalArgumentException) {
        CountryCode.DO
    }
    val unit = try {
        DistanceUnit.valueOf(distanceUnit)
    } catch (e: IllegalArgumentException) {
        DistanceUnit.KM
    }
    val airport = if (airportWaitingMin != null) {
        AirportConfig(
            waitingMin = airportWaitingMin,
            boardingMin = airportBoardingMin ?: 0.0,
            returnsEmpty = airportReturnsEmpty ?: false,
            estimatedReturnMin = airportEstimatedReturnMin ?: 0.0
        )
    } else null

    return DriverSettings(
        countryCode = country,
        minRatePerHour = minRatePerHour,
        minRatePerDistanceUnit = minRatePerDistanceUnit,
        operatingCostPerHour = operatingCostPerHour,
        distanceUnit = unit,
        airportConfig = airport
    )
}

fun DriverSettings.toEntity(): DriverSettingsEntity {
    return DriverSettingsEntity(
        id = 1,
        countryCode = countryCode.name,
        currencyCode = countryCode.currencyCode,
        distanceUnit = distanceUnit.name,
        minRatePerHour = minRatePerHour,
        minRatePerDistanceUnit = minRatePerDistanceUnit,
        operatingCostPerHour = operatingCostPerHour,
        airportWaitingMin = airportConfig?.waitingMin,
        airportBoardingMin = airportConfig?.boardingMin,
        airportReturnsEmpty = airportConfig?.returnsEmpty,
        airportEstimatedReturnMin = airportConfig?.estimatedReturnMin
    )
}

// ── Expense ──

fun ExpenseEntity.toDomain(): Expense {
    val cat = try {
        ExpenseCategory.valueOf(category)
    } catch (e: IllegalArgumentException) {
        ExpenseCategory.OTROS
    }
    return Expense(
        id = id,
        category = cat,
        amount = amount,
        date = date,
        notes = notes
    )
}

fun Expense.toEntity(): ExpenseEntity {
    return ExpenseEntity(
        id = id,
        category = category.name,
        amount = amount,
        date = date,
        notes = notes
    )
}

// ── Shift ──

fun ShiftEntity.toDomain(): Shift {
    return Shift(
        id = id,
        startTime = startTime,
        endTime = endTime,
        platform = platform
    )
}

fun Shift.toEntity(): ShiftEntity {
    return ShiftEntity(
        id = id,
        startTime = startTime,
        endTime = endTime,
        platform = platform
    )
}
