package com.juanpablo0612.sickreshapex.domain.usecase

import com.juanpablo0612.sickreshapex.domain.model.QuickExample
import com.juanpablo0612.sickreshapex.domain.repository.AnalysisRepository

class GetQuickExamplesUseCase(private val repository: AnalysisRepository) {
    suspend operator fun invoke(): List<QuickExample> {
        return repository.getQuickExamples()
    }
}
