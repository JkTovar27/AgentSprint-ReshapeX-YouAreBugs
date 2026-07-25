package com.juanpablo0612.sickreshapex.ui.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juanpablo0612.sickreshapex.domain.model.UserSettings
import com.juanpablo0612.sickreshapex.domain.repository.SettingsRepository
import com.juanpablo0612.sickreshapex.ui.theme.ThemeController
import com.juanpablo0612.sickreshapex.ui.theme.ThemePreference
import com.juanpablo0612.sickreshapex.ui.theme.toSettingsValue
import com.juanpablo0612.sickreshapex.ui.theme.toThemePreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Loads persisted [UserSettings], mirrors the theme choice into [ThemeController] so the
 * whole app (via MainActivity) reacts immediately, and persists any change the user makes.
 */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val themeController: ThemeController
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsState())
    val uiState: StateFlow<SettingsState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val settings = settingsRepository.getUserSettings()
            val preference = settings.theme.toThemePreference()

            // Reflect the persisted choice in the app-wide theme immediately.
            themeController.setPreference(preference)

            _uiState.update {
                it.copy(
                    theme = preference,
                    notificationsEnabled = settings.notificationsEnabled,
                    isLoading = false
                )
            }
        }
    }

    fun setTheme(preference: ThemePreference) {
        val current = _uiState.value
        _uiState.update { it.copy(theme = preference) }
        themeController.setPreference(preference)

        viewModelScope.launch {
            settingsRepository.updateUserSettings(
                UserSettings(
                    theme = preference.toSettingsValue(),
                    notificationsEnabled = current.notificationsEnabled
                )
            )
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        val current = _uiState.value
        _uiState.update { it.copy(notificationsEnabled = enabled) }

        viewModelScope.launch {
            settingsRepository.updateUserSettings(
                UserSettings(
                    theme = current.theme.toSettingsValue(),
                    notificationsEnabled = enabled
                )
            )
        }
    }
}
