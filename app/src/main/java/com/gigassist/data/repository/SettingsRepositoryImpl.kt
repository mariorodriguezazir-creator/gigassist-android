package com.gigassist.data.repository

import com.gigassist.data.local.dao.DriverSettingsDao
import com.gigassist.data.local.mapper.toDomain
import com.gigassist.data.local.mapper.toEntity
import com.gigassist.domain.model.DriverSettings
import com.gigassist.domain.repository.SettingsRepository
import javax.inject.Inject

class SettingsRepositoryImpl @Inject constructor(
    private val dao: DriverSettingsDao
) : SettingsRepository {

    override suspend fun getSettings(): DriverSettings? {
        return dao.getSettings()?.toDomain()
    }

    override suspend fun saveSettings(settings: DriverSettings) {
        dao.upsertSettings(settings.toEntity())
    }
}
