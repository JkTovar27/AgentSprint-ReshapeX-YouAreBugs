package com.juanpablo0612.sickreshapex.di

import com.juanpablo0612.sickreshapex.data.repository.MockAgentPipelineRepository
import com.juanpablo0612.sickreshapex.data.repository.MockAnalysisRepository
import com.juanpablo0612.sickreshapex.domain.repository.AgentPipelineRepository
import com.juanpablo0612.sickreshapex.domain.repository.AnalysisRepository
import com.juanpablo0612.sickreshapex.ui.features.home.HomeViewModel
import com.juanpablo0612.sickreshapex.ui.features.recommendation.RecommendationViewModel
import com.juanpablo0612.sickreshapex.ui.theme.ThemeController
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single<AnalysisRepository> { MockAnalysisRepository() }
    single<AgentPipelineRepository> { MockAgentPipelineRepository(get()) }
    single { ThemeController() }

    viewModel { HomeViewModel(get()) }
    viewModel { RecommendationViewModel(get()) }
    // Processing/Settings/History view models are registered during final integration
    // once those screens exist, to avoid concurrent edits to this file.
}
