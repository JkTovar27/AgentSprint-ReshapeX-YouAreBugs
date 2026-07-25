package com.juanpablo0612.sickreshapex.domain.usecase

import com.juanpablo0612.sickreshapex.domain.model.Analysis
import com.juanpablo0612.sickreshapex.domain.repository.AnalysisRepository

class GetAnalysisDetailsUseCase(private val repository: AnalysisRepository) {
    suspend operator fun invoke(id: String): Analysis? {
        return repository.getAnalysisById(id)
    }
}
