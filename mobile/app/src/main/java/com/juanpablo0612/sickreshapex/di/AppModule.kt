package com.juanpablo0612.sickreshapex.di

import com.juanpablo0612.sickreshapex.data.repository.MockAgentPipelineRepository
import com.juanpablo0612.sickreshapex.data.repository.MockAnalysisRepository
import com.juanpablo0612.sickreshapex.data.repository.MockSettingsRepository
import com.juanpablo0612.sickreshapex.domain.repository.AgentPipelineRepository
import com.juanpablo0612.sickreshapex.domain.repository.AnalysisRepository
import com.juanpablo0612.sickreshapex.domain.repository.SettingsRepository
import com.juanpablo0612.sickreshapex.ui.features.history.HistoryViewModel
import com.juanpablo0612.sickreshapex.ui.features.home.HomeViewModel
import com.juanpablo0612.sickreshapex.ui.features.processing.ProcessingViewModel
import com.juanpablo0612.sickreshapex.ui.features.recommendation.RecommendationViewModel
import com.juanpablo0612.sickreshapex.ui.features.settings.SettingsViewModel
import com.juanpablo0612.sickreshapex.ui.theme.ThemeController
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single<AnalysisRepository> { MockAnalysisRepository() }
    single<AgentPipelineRepository> { MockAgentPipelineRepository(get()) }
    single<SettingsRepository> { MockSettingsRepository() }
    single { ThemeController() }

    viewModel { HomeViewModel(get()) }
    viewModel { RecommendationViewModel(get()) }
    viewModel { ProcessingViewModel(get()) }
    viewModel { HistoryViewModel(get()) }
    viewModel { SettingsViewModel(get(), get()) }
}
