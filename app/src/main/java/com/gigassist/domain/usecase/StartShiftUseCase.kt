package com.gigassist.domain.usecase

import com.gigassist.domain.model.Shift
import com.gigassist.domain.repository.ShiftRepository
import javax.inject.Inject

class StartShiftUseCase @Inject constructor(
    private val repository: ShiftRepository
) {
    suspend operator fun invoke(platform: String): Shift {
        return repository.startShift(platform)
    }
}
