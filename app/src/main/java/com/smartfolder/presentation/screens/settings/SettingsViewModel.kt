package com.smartfolder.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartfolder.domain.model.ExecutionProfile
import com.smartfolder.domain.model.ModelChoice
import com.smartfolder.domain.repository.SettingsRepository
import com.smartfolder.domain.usecase.ClearCacheUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val clearCacheUseCase: ClearCacheUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.threshold.collect { threshold ->
                _uiState.value = _uiState.value.copy(threshold = threshold)
            }
        }
        viewModelScope.launch {
            settingsRepository.modelChoice.collect { choice ->
                _uiState.value = _uiState.value.copy(modelChoice = choice)
            }
        }
        viewModelScope.launch {
            settingsRepository.executionProfile.collect { profile ->
                _uiState.value = _uiState.value.copy(executionProfile = profile)
            }
        }
        viewModelScope.launch {
            settingsRepository.darkMode.collect { darkMode ->
                _uiState.value = _uiState.value.copy(darkMode = darkMode)
            }
        }
        viewModelScope.launch {
            settingsRepository.manualMode.collect { manualMode ->
                _uiState.value = _uiState.value.copy(manualMode = manualMode)
            }
        }
    }

    fun setThreshold(value: Float) {
        viewModelScope.launch {
            settingsRepository.setThreshold(value)
        }
    }

    fun setModelChoice(choice: ModelChoice) {
        viewModelScope.launch {
            settingsRepository.setModelChoice(choice)
        }
    }

    fun setExecutionProfile(profile: ExecutionProfile) {
        viewModelScope.launch {
            settingsRepository.setExecutionProfile(profile)
        }
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDarkMode(enabled)
        }
    }

    fun setManualMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setManualMode(enabled)
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isClearingCache = true)
            try {
                clearCacheUseCase()
                _uiState.value = _uiState.value.copy(
                    isClearingCache = false,
                    message = "Cache cleared successfully"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isClearingCache = false,
                    message = "Failed to clear cache: ${e.message}"
                )
            }
        }
    }

    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
