package com.juanpablo0612.sickreshapex.domain.repository

import com.juanpablo0612.sickreshapex.domain.model.*

interface AnalysisRepository {
    suspend fun getRecentAnalyses(): List<RecentAnalysis>
    suspend fun getQuickExamples(): List<QuickExample>
    suspend fun startAnalysis(description: String): Analysis
    suspend fun getAnalysisById(id: String): Analysis?
    suspend fun getFavorites(): List<FavoriteAnalysis>
    suspend fun toggleFavorite(analysisId: String): Boolean
}
