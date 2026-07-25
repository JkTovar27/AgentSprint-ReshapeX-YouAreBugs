package com.juanpablo0612.sickreshapex.data.repository

import com.juanpablo0612.sickreshapex.domain.model.*
import com.juanpablo0612.sickreshapex.domain.repository.AnalysisRepository
import kotlinx.coroutines.delay

class MockAnalysisRepository : AnalysisRepository {
    override suspend fun getRecentAnalyses(): List<RecentAnalysis> {
        return listOf(
            RecentAnalysis("1", "Detection of cardboard boxes on a conveyor belt", System.currentTimeMillis() - 3600000, "W16"),
            RecentAnalysis("2", "Checking the presence of caps on bottles", System.currentTimeMillis() - 7200000, "W26")
        )
    }

    override suspend fun getQuickExamples(): List<QuickExample> {
        return listOf(
            QuickExample("Conveyor Belt", "Detect objects on a fast conveyor belt with varying colors."),
            QuickExample("Level Sensing", "Measure the level of liquid in a tank with foam.")
        )
    }

    override suspend fun startAnalysis(description: String): Analysis {
        delay(2000) // Simulate network delay
        val requirements = ApplicationRequirements(
            tipo_objeto = "Caja de cartón",
            distancia_mm = 500,
            ambiente = "Industrial estándar",
            material_superficie = "Mate",
            montaje = "Lateral"
        )
        val recommendation = Recommendation(
            sensorFamily = "W16",
            executiveSummary = "The W16 sensor family is ideal for detecting cardboard boxes due to its high reliability and resistance to ambient light.",
            requirements = requirements,
            confidenceLevel = 0.95f,
            confidenceExplanation = "High match based on object type and distance requirements.",
            reasons = listOf(
                Reason("TwinEye Technology", "Provides reliable detection of glossy or jet-black objects."),
                Reason("BluePilot", "Allows for fast and intuitive alignment.")
            ),
            sources = listOf(
                SourceReference("W16 Product Page", "https://www.sick.com/w16")
            )
        )
        return Analysis(
            id = "3",
            description = description,
            timestamp = System.currentTimeMillis(),
            requirements = requirements,
            recommendation = recommendation,
            alternatives = listOf(
                AlternativeOption("W26", "Over-specified for this simple detection task, higher cost.")
            )
        )
    }

    override suspend fun getAnalysisById(id: String): Analysis? {
        return null // Mock implementation
    }

    override suspend fun getFavorites(): List<FavoriteAnalysis> {
        return emptyList()
    }

    override suspend fun toggleFavorite(analysisId: String): Boolean {
        return true
    }
}
