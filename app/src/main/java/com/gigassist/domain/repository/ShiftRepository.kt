package com.gigassist.domain.repository

import com.gigassist.domain.model.Shift

/**
 * Repositorio para turnos de trabajo.
 */
interface ShiftRepository {
    suspend fun startShift(platform: String): Shift
    suspend fun endActiveShift(): Shift?
    suspend fun getActiveShift(): Shift?
}
