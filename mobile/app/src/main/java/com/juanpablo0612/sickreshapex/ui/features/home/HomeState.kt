package com.juanpablo0612.sickreshapex.ui.features.home

import com.juanpablo0612.sickreshapex.domain.model.QuickExample
import com.juanpablo0612.sickreshapex.domain.model.RecentAnalysis

data class HomeState(
    val isLoading: Boolean = false,
    val quickExamples: List<QuickExample> = emptyList(),
    val recentAnalyses: List<RecentAnalysis> = emptyList(),
    val error: String? = null
)
