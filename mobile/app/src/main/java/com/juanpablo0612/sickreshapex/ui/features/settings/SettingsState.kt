package com.juanpablo0612.sickreshapex.ui.features.settings

import com.juanpablo0612.sickreshapex.ui.theme.ThemePreference

data class SettingsState(
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val notificationsEnabled: Boolean = true,
    val isLoading: Boolean = true
)
