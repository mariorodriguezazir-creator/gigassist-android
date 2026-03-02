package com.gigassist.domain.usecase

import com.gigassist.domain.model.CountryCode
import com.gigassist.domain.model.DriverSettings
import com.gigassist.domain.repository.SettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GetOrInitDefaultSettingsUseCaseTest {

    private lateinit var fakeRepo: FakeSettingsRepository
    private lateinit var useCase: GetOrInitDefaultSettingsUseCase

    @Before
    fun setUp() {
        fakeRepo = FakeSettingsRepository()
        useCase = GetOrInitDefaultSettingsUseCase(fakeRepo)
    }

    @Test
    fun `retorna settings existentes cuando hay datos guardados`() = runTest {
        val saved = DriverSettings(
            countryCode = CountryCode.MX,
            minRatePerHour = 120.0,
            minRatePerDistanceUnit = 8.0,
            operatingCostPerHour = 50.0
        )
        fakeRepo.savedSettings = saved

        val result = useCase()
        assertEquals(CountryCode.MX, result.countryCode)
        assertEquals(120.0, result.minRatePerHour, 0.01)
    }

    @Test
    fun `retorna defaults de DO cuando no hay datos guardados`() = runTest {
        fakeRepo.savedSettings = null

        val result = useCase()
        assertEquals(CountryCode.DO, result.countryCode)
        assertEquals(CountryCode.DO.defaultMinRatePerHour, result.minRatePerHour, 0.01)
        assertEquals(CountryCode.DO.defaultMinRatePerKm, result.minRatePerDistanceUnit, 0.01)
    }
}

private class FakeSettingsRepository : SettingsRepository {
    var savedSettings: DriverSettings? = null

    override suspend fun getSettings(): DriverSettings? = savedSettings

    override suspend fun saveSettings(settings: DriverSettings) {
        savedSettings = settings
    }
}
