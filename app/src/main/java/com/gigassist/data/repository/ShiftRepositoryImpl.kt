package com.gigassist.data.repository

import com.gigassist.data.local.dao.ShiftDao
import com.gigassist.data.local.mapper.toDomain
import com.gigassist.data.local.mapper.toEntity
import com.gigassist.domain.model.Shift
import com.gigassist.domain.repository.ShiftRepository
import javax.inject.Inject

class ShiftRepositoryImpl @Inject constructor(
    private val dao: ShiftDao
) : ShiftRepository {

    override suspend fun startShift(platform: String): Shift {
        val shift = Shift(
            startTime = System.currentTimeMillis(),
            platform = platform
        )
        val id = dao.startShift(shift.toEntity())
        return shift.copy(id = id)
    }

    override suspend fun endActiveShift(): Shift? {
        val active = dao.getActiveShift() ?: return null
        val endTime = System.currentTimeMillis()
        dao.endShift(active.id, endTime)
        return active.toDomain().copy(endTime = endTime)
    }

    override suspend fun getActiveShift(): Shift? {
        return dao.getActiveShift()?.toDomain()
    }
}
