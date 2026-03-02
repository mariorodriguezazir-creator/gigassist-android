package com.gigassist.domain.usecase

import com.gigassist.domain.model.DriverSettings
import com.gigassist.domain.repository.SettingsRepository
import javax.inject.Inject

class GetDriverSettingsUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    suspend operator fun invoke(): DriverSettings? {
        return repository.getSettings()
    }
}
