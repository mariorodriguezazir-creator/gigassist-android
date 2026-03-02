package com.gigassist.domain.repository

import com.gigassist.domain.model.DriverSettings

/**
 * Repositorio para la configuración del conductor.
 */
interface SettingsRepository {
    suspend fun getSettings(): DriverSettings?
    suspend fun saveSettings(settings: DriverSettings)
}
