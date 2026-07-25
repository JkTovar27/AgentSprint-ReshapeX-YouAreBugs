package com.juanpablo0612.sickreshapex.domain.repository

import com.juanpablo0612.sickreshapex.domain.model.UserSettings

interface SettingsRepository {
    suspend fun getUserSettings(): UserSettings
    suspend fun updateUserSettings(settings: UserSettings)
}
