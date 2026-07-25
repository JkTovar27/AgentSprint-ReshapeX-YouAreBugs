package com.juanpablo0612.sickreshapex.di

import com.juanpablo0612.sickreshapex.data.repository.MockAnalysisRepository
import com.juanpablo0612.sickreshapex.domain.repository.AnalysisRepository
import com.juanpablo0612.sickreshapex.ui.features.home.HomeViewModel
import com.juanpablo0612.sickreshapex.ui.features.recommendation.RecommendationViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single<AnalysisRepository> { MockAnalysisRepository() }
    
    viewModel { HomeViewModel(get()) }
    viewModel { RecommendationViewModel(get()) }
}
