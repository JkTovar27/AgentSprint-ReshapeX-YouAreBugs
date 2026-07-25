package com.juanpablo0612.sickreshapex.ui.theme

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemePreference { LIGHT, DARK, SYSTEM }

fun ThemePreference.toSettingsValue(): String = when (this) {
    ThemePreference.LIGHT -> "light"
    ThemePreference.DARK -> "dark"
    ThemePreference.SYSTEM -> "system"
}

fun String.toThemePreference(): ThemePreference = when (this) {
    "light" -> ThemePreference.LIGHT
    "dark" -> ThemePreference.DARK
    else -> ThemePreference.SYSTEM
}

/**
 * App-wide theme selection, held outside any single screen so the Settings screen can
 * change what MainActivity renders. Registered as a Koin singleton; read with
 * `koinInject<ThemeController>()` from MainActivity and written from SettingsViewModel.
 */
class ThemeController {
    private val _preference = MutableStateFlow(ThemePreference.SYSTEM)
    val preference: StateFlow<ThemePreference> = _preference

    fun setPreference(preference: ThemePreference) {
        _preference.value = preference
    }
}
