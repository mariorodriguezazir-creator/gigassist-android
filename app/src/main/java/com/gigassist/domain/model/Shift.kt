package com.gigassist.domain.model

/**
 * Modelo de dominio para un turno de trabajo.
 */
data class Shift(
    val id: Long = 0,
    val startTime: Long,
    val endTime: Long? = null,
    val platform: String
)
