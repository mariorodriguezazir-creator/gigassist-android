package com.gigassist.domain.usecase

import com.gigassist.domain.model.EvaluationResult
import com.gigassist.domain.model.TripRecord
import com.gigassist.domain.repository.TripRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class SaveTripRecordUseCaseTest {

    private lateinit var fakeRepo: FakeTripRepository
    private lateinit var useCase: SaveTripRecordUseCase

    @Before
    fun setUp() {
        fakeRepo = FakeTripRepository()
        useCase = SaveTripRecordUseCase(fakeRepo)
    }

    @Test
    fun `guarda un trip record correctamente`() = runTest {
        useCase(
            fare = 450.0,
            distanceKm = 10.0,
            durationMin = 20,
            platform = "Uber",
            evaluationResult = EvaluationResult.GOOD,
            shiftId = 1L
        )

        assertEquals(1, fakeRepo.savedRecords.size)
        val saved = fakeRepo.savedRecords[0]
        assertEquals(450.0, saved.fare, 0.01)
        assertEquals(10.0, saved.distanceKm, 0.01)
        assertEquals(20, saved.durationMin)
        assertEquals("Uber", saved.platform)
        assertEquals(EvaluationResult.GOOD, saved.evaluationResult)
        assertEquals(1L, saved.shiftId)
    }

    @Test
    fun `guarda multiples records`() = runTest {
        useCase(fare = 100.0, distanceKm = 5.0, durationMin = 10,
            platform = "Uber", evaluationResult = EvaluationResult.POOR, shiftId = 1L)
        useCase(fare = 200.0, distanceKm = 8.0, durationMin = 15,
            platform = "DiDi", evaluationResult = EvaluationResult.FAIR, shiftId = 1L)

        assertEquals(2, fakeRepo.savedRecords.size)
    }
}

private class FakeTripRepository : TripRepository {
    val savedRecords = mutableListOf<TripRecord>()

    override suspend fun saveTripRecord(record: TripRecord) {
        savedRecords.add(record)
    }

    override fun getTripsForShift(shiftId: Long): Flow<List<TripRecord>> = flowOf(savedRecords.filter { it.shiftId == shiftId })
    override fun getTripsForDay(date: LocalDate): Flow<List<TripRecord>> = flowOf(emptyList())
    override fun getTripsForDateRange(start: LocalDate, end: LocalDate): Flow<List<TripRecord>> = flowOf(emptyList())
}
