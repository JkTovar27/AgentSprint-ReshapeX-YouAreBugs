package com.juanpablo0612.sickreshapex.ui.features.history

import com.juanpablo0612.sickreshapex.domain.model.RecentAnalysis

data class HistoryState(
    val isLoading: Boolean = false,
    val analyses: List<RecentAnalysis> = emptyList(),
    val favoriteIds: Set<String> = emptySet(),
    val error: String? = null
)
