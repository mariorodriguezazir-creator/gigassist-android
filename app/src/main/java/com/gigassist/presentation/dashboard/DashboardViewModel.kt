package com.gigassist.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gigassist.domain.model.DistanceUnit
import com.gigassist.domain.model.ShiftStats
import com.gigassist.domain.model.TripEvaluationUiModel
import com.gigassist.domain.usecase.EndShiftUseCase
import com.gigassist.domain.usecase.GetActiveShiftUseCase
import com.gigassist.domain.usecase.GetDriverSettingsUseCase
import com.gigassist.domain.usecase.GetShiftStatsUseCase
import com.gigassist.domain.usecase.StartShiftUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val hasActiveShift: Boolean = false,
    val activeShiftId: Long? = null,
    val shiftStats: ShiftStats = ShiftStats(),
    val lastTripEvaluation: TripEvaluationUiModel? = null,
    val currencySymbol: String = "RD$",
    val distanceUnitLabel: String = "km",
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val getActiveShift: GetActiveShiftUseCase,
    private val startShift: StartShiftUseCase,
    private val endShift: EndShiftUseCase,
    private val getShiftStats: GetShiftStatsUseCase,
    private val getSettings: GetDriverSettingsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var statsJob: Job? = null

    init {
        loadDashboard()
    }

    private fun loadDashboard() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val settings = getSettings()
                val activeShift = getActiveShift()

                _uiState.update {
                    it.copy(
                        hasActiveShift = activeShift != null,
                        activeShiftId = activeShift?.id,
                        currencySymbol = settings?.countryCode?.currencySymbol ?: "RD$",
                        distanceUnitLabel = if (settings?.distanceUnit == DistanceUnit.MILES) "mi" else "km",
                        isLoading = false
                    )
                }

                if (activeShift != null) {
                    observeShiftStats(activeShift.id, settings?.operatingCostPerHour ?: 0.0)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private fun observeShiftStats(shiftId: Long, operatingCostPerHour: Double) {
        statsJob?.cancel()
        statsJob = viewModelScope.launch {
            getShiftStats(shiftId, operatingCostPerHour).collect { stats ->
                _uiState.update { it.copy(shiftStats = stats) }
            }
        }
    }

    fun onStartShift(platform: String) {
        viewModelScope.launch {
            try {
                val shift = startShift(platform)
                _uiState.update {
                    it.copy(
                        hasActiveShift = true,
                        activeShiftId = shift.id,
                        shiftStats = ShiftStats()
                    )
                }
                val settings = getSettings()
                observeShiftStats(shift.id, settings?.operatingCostPerHour ?: 0.0)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Error al iniciar turno: ${e.message}") }
            }
        }
    }

    fun onEndShift() {
        viewModelScope.launch {
            try {
                endShift()
                statsJob?.cancel()
                _uiState.update {
                    it.copy(
                        hasActiveShift = false,
                        activeShiftId = null,
                        shiftStats = ShiftStats()
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Error al finalizar turno: ${e.message}") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
