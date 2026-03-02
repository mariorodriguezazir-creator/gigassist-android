package com.gigassist.domain.model

/**
 * Estadísticas de un turno de trabajo.
 */
data class ShiftStats(
    val totalTrips: Int = 0,
    val grossEarnings: Double = 0.0,
    val netEarnings: Double = 0.0,
    val activeHours: Double = 0.0,
    val averageRatePerHour: Double = 0.0,
    val tripsPerPlatform: Map<String, Int> = emptyMap()
)
