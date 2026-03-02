package com.gigassist.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gigassist.domain.model.CountryCode
import com.gigassist.domain.model.DistanceUnit
import com.gigassist.domain.model.DriverSettings
import com.gigassist.domain.usecase.GetOrInitDefaultSettingsUseCase
import com.gigassist.domain.usecase.SaveDriverSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val availableCountries: List<CountryCode> = CountryCode.entries.filter { it != CountryCode.US },
    val selectedCountry: CountryCode = CountryCode.DO,
    val minRatePerHour: String = "",
    val minRatePerDistanceUnit: String = "",
    val operatingCostPerHour: String = "",
    val currencySymbol: String = "RD$",
    val distanceUnitLabel: String = "km",
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val getOrInitSettings: GetOrInitDefaultSettingsUseCase,
    private val saveSettings: SaveDriverSettingsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val settings = getOrInitSettings()
                _uiState.update {
                    it.copy(
                        selectedCountry = settings.countryCode,
                        minRatePerHour = settings.minRatePerHour.toBigDecimal().stripTrailingZeros().toPlainString(),
                        minRatePerDistanceUnit = settings.minRatePerDistanceUnit.toBigDecimal().stripTrailingZeros().toPlainString(),
                        operatingCostPerHour = settings.operatingCostPerHour.toBigDecimal().stripTrailingZeros().toPlainString(),
                        currencySymbol = settings.countryCode.currencySymbol,
                        distanceUnitLabel = if (settings.distanceUnit == DistanceUnit.KM) "km" else "mi",
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun onCountrySelected(countryCode: CountryCode) {
        _uiState.update {
            it.copy(
                selectedCountry = countryCode,
                minRatePerHour = countryCode.defaultMinRatePerHour.toBigDecimal().stripTrailingZeros().toPlainString(),
                minRatePerDistanceUnit = countryCode.defaultMinRatePerKm.toBigDecimal().stripTrailingZeros().toPlainString(),
                currencySymbol = countryCode.currencySymbol,
                distanceUnitLabel = if (countryCode.distanceUnit == DistanceUnit.KM) "km" else "mi"
            )
        }
    }

    fun onMinRatePerHourChanged(value: String) {
        _uiState.update { it.copy(minRatePerHour = value) }
    }

    fun onMinRatePerDistanceUnitChanged(value: String) {
        _uiState.update { it.copy(minRatePerDistanceUnit = value) }
    }

    fun onOperatingCostChanged(value: String) {
        _uiState.update { it.copy(operatingCostPerHour = value) }
    }

    fun onSave() {
        val state = _uiState.value
        val ratePerHour = state.minRatePerHour.toDoubleOrNull()
        val ratePerDistance = state.minRatePerDistanceUnit.toDoubleOrNull()
        val costPerHour = state.operatingCostPerHour.toDoubleOrNull()

        if (ratePerHour == null || ratePerDistance == null || costPerHour == null) {
            _uiState.update { it.copy(error = "Por favor ingresa valores numéricos válidos") }
            return
        }
        if (ratePerHour <= 0 || ratePerDistance <= 0) {
            _uiState.update { it.copy(error = "Los umbrales deben ser mayores a cero") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val settings = DriverSettings(
                    countryCode = state.selectedCountry,
                    minRatePerHour = ratePerHour,
                    minRatePerDistanceUnit = ratePerDistance,
                    operatingCostPerHour = costPerHour
                )
                saveSettings(settings)
                _uiState.update { it.copy(isLoading = false, isSaved = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Error al guardar: ${e.message}") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
