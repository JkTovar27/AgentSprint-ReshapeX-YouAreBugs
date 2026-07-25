package com.juanpablo0612.sickreshapex.ui.features.recommendation

import com.juanpablo0612.sickreshapex.domain.model.Analysis

data class RecommendationState(
    val isLoading: Boolean = false,
    val analysis: Analysis? = null,
    val error: String? = null
)
