package com.juanpablo0612.sickreshapex.domain.usecase

import com.juanpablo0612.sickreshapex.domain.model.Analysis
import com.juanpablo0612.sickreshapex.domain.repository.AnalysisRepository

class StartAnalysisUseCase(private val repository: AnalysisRepository) {
    suspend operator fun invoke(description: String): Analysis {
        return repository.startAnalysis(description)
    }
}
