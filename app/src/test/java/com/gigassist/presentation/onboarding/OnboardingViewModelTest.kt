package com.gigassist.presentation.onboarding

import com.gigassist.domain.model.CountryCode
import com.gigassist.domain.model.DriverSettings
import com.gigassist.domain.repository.SettingsRepository
import com.gigassist.domain.usecase.GetOrInitDefaultSettingsUseCase
import com.gigassist.domain.usecase.SaveDriverSettingsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepo: FakeSettingsRepo
    private lateinit var viewModel: OnboardingViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepo = FakeSettingsRepo()
        viewModel = OnboardingViewModel(
            getOrInitSettings = GetOrInitDefaultSettingsUseCase(fakeRepo),
            saveSettings = SaveDriverSettingsUseCase(fakeRepo)
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init carga defaults de DO cuando no hay settings`() = runTest {
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(CountryCode.DO, state.selectedCountry)
        assertEquals("RD$", state.currencySymbol)
        assertFalse(state.isLoading)
    }

    @Test
    fun `onCountrySelected actualiza pais y umbrales sugeridos`() = runTest {
        advanceUntilIdle()
        viewModel.onCountrySelected(CountryCode.CO)

        val state = viewModel.uiState.value
        assertEquals(CountryCode.CO, state.selectedCountry)
        assertEquals("$", state.currencySymbol)
        assertEquals(
            CountryCode.CO.defaultMinRatePerHour.toBigDecimal().stripTrailingZeros().toPlainString(),
            state.minRatePerHour
        )
    }

    @Test
    fun `onSave con valores invalidos muestra error`() = runTest {
        advanceUntilIdle()
        viewModel.onMinRatePerHourChanged("abc")
        viewModel.onSave()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isSaved)
        assertEquals("Por favor ingresa valores numéricos válidos", state.error)
    }

    @Test
    fun `onSave con valores validos guarda y marca isSaved`() = runTest {
        advanceUntilIdle()
        viewModel.onMinRatePerHourChanged("600")
        viewModel.onMinRatePerDistanceUnitChanged("35")
        viewModel.onOperatingCostChanged("250")
        viewModel.onSave()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isSaved)
        assertFalse(state.isLoading)

        // Verify settings were persisted
        val saved = fakeRepo.savedSettings!!
        assertEquals(600.0, saved.minRatePerHour, 0.01)
        assertEquals(35.0, saved.minRatePerDistanceUnit, 0.01)
        assertEquals(250.0, saved.operatingCostPerHour, 0.01)
    }

    @Test
    fun `onSave con umbral cero muestra error`() = runTest {
        advanceUntilIdle()
        viewModel.onMinRatePerHourChanged("0")
        viewModel.onMinRatePerDistanceUnitChanged("35")
        viewModel.onOperatingCostChanged("250")
        viewModel.onSave()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isSaved)
        assertEquals("Los umbrales deben ser mayores a cero", state.error)
    }
}

private class FakeSettingsRepo : SettingsRepository {
    var savedSettings: DriverSettings? = null
    override suspend fun getSettings(): DriverSettings? = savedSettings
    override suspend fun saveSettings(settings: DriverSettings) {
        savedSettings = settings
    }
}
