package com.juanpablo0612.sickreshapex.ui.features.recommendation

import androidx.annotation.StringRes
import com.juanpablo0612.sickreshapex.domain.model.Analysis

data class RecommendationState(
    val isLoading: Boolean = false,
    val analysis: Analysis? = null,
    @StringRes val errorRes: Int? = null,
    val isFavorite: Boolean = false
)
