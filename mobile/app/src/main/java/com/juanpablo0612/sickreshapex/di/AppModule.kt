package com.juanpablo0612.sickreshapex.di

import com.google.gson.Gson
import com.juanpablo0612.sickreshapex.BuildConfig
import com.juanpablo0612.sickreshapex.data.remote.OrchestratorApi
import com.juanpablo0612.sickreshapex.data.remote.PipelineFrameParser
import com.juanpablo0612.sickreshapex.data.repository.MockAnalysisRepository
import com.juanpablo0612.sickreshapex.data.repository.MockSettingsRepository
import com.juanpablo0612.sickreshapex.data.repository.WebSocketAgentPipelineRepository
import com.juanpablo0612.sickreshapex.domain.repository.AgentPipelineRepository
import com.juanpablo0612.sickreshapex.domain.repository.AnalysisRepository
import com.juanpablo0612.sickreshapex.domain.repository.SettingsRepository
import com.juanpablo0612.sickreshapex.domain.usecase.GetAnalysisDetailsUseCase
import com.juanpablo0612.sickreshapex.domain.usecase.GetFavoritesUseCase
import com.juanpablo0612.sickreshapex.domain.usecase.GetQuickExamplesUseCase
import com.juanpablo0612.sickreshapex.domain.usecase.GetRecentAnalysesUseCase
import com.juanpablo0612.sickreshapex.domain.usecase.StartAnalysisUseCase
import com.juanpablo0612.sickreshapex.domain.usecase.ToggleFavoriteUseCase
import com.juanpablo0612.sickreshapex.ui.features.history.HistoryViewModel
import com.juanpablo0612.sickreshapex.ui.features.home.HomeViewModel
import com.juanpablo0612.sickreshapex.ui.features.processing.ProcessingViewModel
import com.juanpablo0612.sickreshapex.ui.features.recommendation.RecommendationViewModel
import com.juanpablo0612.sickreshapex.ui.features.settings.SettingsViewModel
import com.juanpablo0612.sickreshapex.ui.theme.ThemeController
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * secrets.properties may hold `http(s)://host:port`, `ws(s)://host:port`, or the full
 * `ws://host:port/ws/orchestrator` endpoint — normalize all of them to `scheme://host:port`.
 */
private fun backendBaseUrl(): String =
    BuildConfig.BACKEND_BASE_URL.trim().trimEnd('/').removeSuffix("/ws/orchestrator")

private fun httpBaseUrl(): String = backendBaseUrl().replaceFirst(Regex("^ws"), "http")

private fun orchestratorWsUrl(): String =
    backendBaseUrl().replaceFirst(Regex("^http"), "ws") + "/ws/orchestrator"

val appModule = module {
    single<AnalysisRepository> { MockAnalysisRepository() }
    single<SettingsRepository> { MockSettingsRepository() }
    single { ThemeController() }

    single { Gson() }
    single { PipelineFrameParser(get()) }
    single {
        OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS) // keep the orchestrator socket alive on long stages
            .build()
    }
    single<OrchestratorApi> {
        Retrofit.Builder()
            .baseUrl(httpBaseUrl() + "/")
            .client(get())
            .addConverterFactory(GsonConverterFactory.create(get<Gson>()))
            .build()
            .create(OrchestratorApi::class.java)
    }
    single<AgentPipelineRepository> {
        WebSocketAgentPipelineRepository(
            client = get(),
            parser = get(),
            analysisRepository = get(),
            api = get(),
            orchestratorWsUrl = orchestratorWsUrl()
        )
    }

    factory { GetRecentAnalysesUseCase(get()) }
    factory { GetQuickExamplesUseCase(get()) }
    factory { GetAnalysisDetailsUseCase(get()) }
    factory { GetFavoritesUseCase(get()) }
    factory { ToggleFavoriteUseCase(get()) }
    factory { StartAnalysisUseCase(get()) }

    viewModel { HomeViewModel(get(), get()) }
    viewModel { RecommendationViewModel(get(), get(), get()) }
    viewModel { ProcessingViewModel(get()) }
    viewModel { HistoryViewModel(get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get()) }
}
