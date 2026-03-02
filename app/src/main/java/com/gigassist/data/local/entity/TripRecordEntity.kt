package com.gigassist.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entidad Room para registros de viajes evaluados.
 */
@Entity(tableName = "trip_records")
data class TripRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val shiftId: Long,
    val fare: Double,
    val distanceKm: Double,
    val durationMin: Int,
    val platform: String,
    val evaluationResult: String,
    val timestamp: Long
)
