package com.gigassist.domain.usecase

import com.gigassist.domain.model.CountryCode
import com.gigassist.domain.model.DriverSettings
import com.gigassist.domain.repository.SettingsRepository
import javax.inject.Inject

/**
 * Obtiene las settings existentes o inicializa con defaults.
 * Si no hay settings en DB, retorna defaults basados en CountryCode.DO.
 */
class GetOrInitDefaultSettingsUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    suspend operator fun invoke(): DriverSettings {
        return repository.getSettings() ?: DriverSettings(countryCode = CountryCode.DO)
    }
}
