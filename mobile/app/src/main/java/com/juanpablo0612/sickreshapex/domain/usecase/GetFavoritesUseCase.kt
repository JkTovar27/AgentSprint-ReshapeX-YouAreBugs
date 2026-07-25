package com.juanpablo0612.sickreshapex.domain.usecase

import com.juanpablo0612.sickreshapex.domain.model.FavoriteAnalysis
import com.juanpablo0612.sickreshapex.domain.repository.AnalysisRepository

class GetFavoritesUseCase(private val repository: AnalysisRepository) {
    suspend operator fun invoke(): List<FavoriteAnalysis> {
        return repository.getFavorites()
    }
}
