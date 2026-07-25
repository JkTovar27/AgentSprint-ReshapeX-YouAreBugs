package com.juanpablo0612.sickreshapex.domain.usecase

import com.juanpablo0612.sickreshapex.domain.model.RecentAnalysis
import com.juanpablo0612.sickreshapex.domain.repository.AnalysisRepository

class GetRecentAnalysesUseCase(private val repository: AnalysisRepository) {
    suspend operator fun invoke(): List<RecentAnalysis> {
        return repository.getRecentAnalyses()
    }
}
