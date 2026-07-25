package com.juanpablo0612.sickreshapex.domain.usecase

import com.juanpablo0612.sickreshapex.domain.repository.AnalysisRepository

class ToggleFavoriteUseCase(private val repository: AnalysisRepository) {
    suspend operator fun invoke(analysisId: String): Boolean {
        return repository.toggleFavorite(analysisId)
    }
}
