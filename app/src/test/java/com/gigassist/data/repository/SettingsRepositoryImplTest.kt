package com.gigassist.data.repository

import com.gigassist.data.local.dao.DriverSettingsDao
import com.gigassist.data.local.entity.DriverSettingsEntity
import com.gigassist.domain.model.CountryCode
import com.gigassist.domain.model.DistanceUnit
import com.gigassist.domain.model.DriverSettings
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Tests para SettingsRepositoryImpl usando un DAO fake in-memory.
 */
class SettingsRepositoryImplTest {

    private lateinit var fakeDao: FakeDriverSettingsDao
    private lateinit var repository: SettingsRepositoryImpl

    @Before
    fun setUp() {
        fakeDao = FakeDriverSettingsDao()
        repository = SettingsRepositoryImpl(fakeDao)
    }

    @Test
    fun `getSettings retorna null cuando no hay settings guardados`() = runTest {
        val result = repository.getSettings()
        assertNull(result)
    }

    @Test
    fun `saveSettings y getSettings redondea correctamente`() = runTest {
        val settings = DriverSettings(
            countryCode = CountryCode.DO,
            minRatePerHour = 600.0,
            minRatePerDistanceUnit = 35.0,
            operatingCostPerHour = 250.0
        )
        repository.saveSettings(settings)

        val result = repository.getSettings()
        assertNotNull(result)
        assertEquals(CountryCode.DO, result!!.countryCode)
        assertEquals(600.0, result.minRatePerHour, 0.01)
        assertEquals(35.0, result.minRatePerDistanceUnit, 0.01)
        assertEquals(250.0, result.operatingCostPerHour, 0.01)
        assertEquals(DistanceUnit.KM, result.distanceUnit)
    }

    @Test
    fun `saveSettings con pais Colombia mapea correctamente`() = runTest {
        val settings = DriverSettings(
            countryCode = CountryCode.CO,
            minRatePerHour = 15000.0,
            minRatePerDistanceUnit = 900.0,
            operatingCostPerHour = 5000.0
        )
        repository.saveSettings(settings)

        val result = repository.getSettings()
        assertNotNull(result)
        assertEquals(CountryCode.CO, result!!.countryCode)
        assertEquals(15000.0, result.minRatePerHour, 0.01)
    }

    @Test
    fun `saveSettings sobreescribe settings anteriores`() = runTest {
        val first = DriverSettings(countryCode = CountryCode.DO)
        repository.saveSettings(first)

        val second = DriverSettings(
            countryCode = CountryCode.MX,
            minRatePerHour = 120.0,
            minRatePerDistanceUnit = 8.0,
            operatingCostPerHour = 50.0
        )
        repository.saveSettings(second)

        val result = repository.getSettings()
        assertEquals(CountryCode.MX, result!!.countryCode)
        assertEquals(120.0, result.minRatePerHour, 0.01)
    }
}

/**
 * Fake DAO in-memory para tests sin necesidad de Room real.
 */
private class FakeDriverSettingsDao : DriverSettingsDao {
    private var stored: DriverSettingsEntity? = null

    override suspend fun getSettings(): DriverSettingsEntity? = stored

    override suspend fun upsertSettings(settings: DriverSettingsEntity) {
        stored = settings
    }
}
