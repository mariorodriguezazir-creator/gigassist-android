package com.gigassist.domain.usecase

import com.gigassist.domain.model.Shift
import com.gigassist.domain.repository.ShiftRepository
import javax.inject.Inject

class EndShiftUseCase @Inject constructor(
    private val repository: ShiftRepository
) {
    suspend operator fun invoke(): Shift? {
        return repository.endActiveShift()
    }
}
