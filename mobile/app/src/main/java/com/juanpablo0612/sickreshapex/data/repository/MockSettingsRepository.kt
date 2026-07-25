package com.juanpablo0612.sickreshapex.data.repository

import com.juanpablo0612.sickreshapex.domain.model.UserSettings
import com.juanpablo0612.sickreshapex.domain.repository.SettingsRepository
import kotlinx.coroutines.delay

/**
 * In-memory [SettingsRepository] used until a real backend/local-storage settings
 * store exists. Mirrors the latency feel of [MockAnalysisRepository] with a small
 * artificial delay so the Settings screen's loading state is visible.
 */
class MockSettingsRepository : SettingsRepository {

    private var settings = UserSettings(theme = "system", notificationsEnabled = true)

    override suspend fun getUserSettings(): UserSettings {
        delay(150)
        return settings
    }

    override suspend fun updateUserSettings(settings: UserSettings) {
        delay(100)
        this.settings = settings
    }
}
