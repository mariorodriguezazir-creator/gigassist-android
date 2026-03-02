package com.gigassist.presentation.dashboard

import com.gigassist.domain.model.CountryCode
import com.gigassist.domain.model.DriverSettings
import com.gigassist.domain.model.EvaluationResult
import com.gigassist.domain.model.Shift
import com.gigassist.domain.model.ShiftStats
import com.gigassist.domain.model.TripRecord
import com.gigassist.domain.repository.SettingsRepository
import com.gigassist.domain.repository.ShiftRepository
import com.gigassist.domain.repository.TripRepository
import com.gigassist.domain.usecase.EndShiftUseCase
import com.gigassist.domain.usecase.GetActiveShiftUseCase
import com.gigassist.domain.usecase.GetDriverSettingsUseCase
import com.gigassist.domain.usecase.GetShiftStatsUseCase
import com.gigassist.domain.usecase.StartShiftUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeShiftRepo: FakeDashboardShiftRepo
    private lateinit var fakeSettingsRepo: FakeDashboardSettingsRepo
    private lateinit var fakeTripRepo: FakeDashboardTripRepo
    private lateinit var viewModel: DashboardViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeShiftRepo = FakeDashboardShiftRepo()
        fakeSettingsRepo = FakeDashboardSettingsRepo()
        fakeTripRepo = FakeDashboardTripRepo()

        viewModel = DashboardViewModel(
            getActiveShift = GetActiveShiftUseCase(fakeShiftRepo),
            startShift = StartShiftUseCase(fakeShiftRepo),
            endShift = EndShiftUseCase(fakeShiftRepo),
            getShiftStats = GetShiftStatsUseCase(fakeTripRepo),
            getSettings = GetDriverSettingsUseCase(fakeSettingsRepo)
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init carga estado sin turno activo`() = runTest {
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.hasActiveShift)
        assertNull(state.activeShiftId)
    }

    @Test
    fun `onStartShift inicia turno correctamente`() = runTest {
        advanceUntilIdle()
        viewModel.onStartShift("Uber")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.hasActiveShift)
        assertEquals(1L, state.activeShiftId)
    }

    @Test
    fun `onEndShift finaliza turno correctamente`() = runTest {
        advanceUntilIdle()
        viewModel.onStartShift("Uber")
        advanceUntilIdle()
        viewModel.onEndShift()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.hasActiveShift)
        assertNull(state.activeShiftId)
    }

    @Test
    fun `init con turno activo muestra stats`() = runTest {
        // Simular turno activo
        fakeShiftRepo.activeShift = Shift(id = 5, startTime = 1000L, platform = "DiDi")
        fakeTripRepo.shiftTrips = listOf(
            TripRecord(id = 1, shiftId = 5, fare = 300.0, distanceKm = 10.0,
                durationMin = 20, platform = "DiDi", evaluationResult = EvaluationResult.GOOD)
        )

        // Re-create viewmodel with active shift
        viewModel = DashboardViewModel(
            getActiveShift = GetActiveShiftUseCase(fakeShiftRepo),
            startShift = StartShiftUseCase(fakeShiftRepo),
            endShift = EndShiftUseCase(fakeShiftRepo),
            getShiftStats = GetShiftStatsUseCase(fakeTripRepo),
            getSettings = GetDriverSettingsUseCase(fakeSettingsRepo)
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.hasActiveShift)
        assertEquals(5L, state.activeShiftId)
        assertEquals(1, state.shiftStats.totalTrips)
        assertEquals(300.0, state.shiftStats.grossEarnings, 0.01)
    }
}

// ── Fakes ──

private class FakeDashboardShiftRepo : ShiftRepository {
    var activeShift: Shift? = null
    private var nextId = 1L

    override suspend fun startShift(platform: String): Shift {
        val shift = Shift(id = nextId++, startTime = System.currentTimeMillis(), platform = platform)
        activeShift = shift
        return shift
    }

    override suspend fun endActiveShift(): Shift? {
        val s = activeShift?.copy(endTime = System.currentTimeMillis())
        activeShift = null
        return s
    }

    override suspend fun getActiveShift(): Shift? = activeShift
}

private class FakeDashboardSettingsRepo : SettingsRepository {
    override suspend fun getSettings(): DriverSettings = DriverSettings(countryCode = CountryCode.DO)
    override suspend fun saveSettings(settings: DriverSettings) {}
}

private class FakeDashboardTripRepo : TripRepository {
    var shiftTrips: List<TripRecord> = emptyList()

    override suspend fun saveTripRecord(record: TripRecord) {}
    override fun getTripsForShift(shiftId: Long): Flow<List<TripRecord>> = flowOf(shiftTrips)
    override fun getTripsForDay(date: LocalDate): Flow<List<TripRecord>> = flowOf(emptyList())
    override fun getTripsForDateRange(start: LocalDate, end: LocalDate): Flow<List<TripRecord>> = flowOf(emptyList())
}
