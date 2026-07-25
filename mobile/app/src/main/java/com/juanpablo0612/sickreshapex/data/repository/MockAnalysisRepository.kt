package com.juanpablo0612.sickreshapex.data.repository

import com.juanpablo0612.sickreshapex.domain.model.*
import com.juanpablo0612.sickreshapex.domain.repository.AnalysisRepository
import kotlinx.coroutines.delay
import java.util.UUID

class MockAnalysisRepository : AnalysisRepository {

    private val analyses = mutableListOf(seedConveyorAnalysis(), seedBottleCapAnalysis())
    private val favoriteIds = mutableSetOf<String>()

    override suspend fun getRecentAnalyses(): List<RecentAnalysis> {
        return analyses
            .sortedByDescending { it.timestamp }
            .map { RecentAnalysis(it.id, it.description, it.timestamp, it.recommendation?.sensorFamily) }
    }

    override suspend fun getQuickExamples(): List<QuickExample> {
        return listOf(
            QuickExample("Conveyor Belt", "Detect cardboard boxes on a fast conveyor belt with varying colors."),
            QuickExample("Level Sensing", "Measure the level of liquid in a tank with foam."),
            QuickExample("Bottle Cap Check", "Check the presence of caps on bottles moving at high speed.")
        )
    }

    override suspend fun startAnalysis(description: String): Analysis {
        delay(400)
        val analysis = seedConveyorAnalysis().copy(
            id = UUID.randomUUID().toString(),
            description = description,
            timestamp = System.currentTimeMillis()
        )
        analyses.add(0, analysis)
        return analysis
    }

    override suspend fun getAnalysisById(id: String): Analysis? {
        return analyses.firstOrNull { it.id == id }
    }

    override suspend fun getFavorites(): List<FavoriteAnalysis> {
        return analyses
            .filter { favoriteIds.contains(it.id) }
            .map { FavoriteAnalysis(it.id, it.description, it.timestamp) }
    }

    override suspend fun toggleFavorite(analysisId: String): Boolean {
        return if (!favoriteIds.add(analysisId)) {
            favoriteIds.remove(analysisId)
            false
        } else {
            true
        }
    }

    override suspend fun saveAnalysis(analysis: Analysis) {
        analyses.removeAll { it.id == analysis.id }
        analyses.add(0, analysis)
    }
}

private fun seedConveyorAnalysis(): Analysis {
    val requirements = ApplicationRequirements(
        tipo_objeto = "Cardboard box",
        distancia_mm = 500,
        ambiente = "Standard industrial",
        material_superficie = "Matte",
        montaje = "Side mount"
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
        sources = listOf(SourceReference("W16 Product Page", "https://www.sick.com/w16"))
    )
    return Analysis(
        id = "1",
        description = "Detection of cardboard boxes on a conveyor belt",
        timestamp = System.currentTimeMillis() - 3_600_000,
        requirements = requirements,
        recommendation = recommendation,
        alternatives = listOf(
            AlternativeOption("W26", "Over-specified for this simple detection task, higher cost.")
        )
    )
}

private fun seedBottleCapAnalysis(): Analysis {
    val requirements = ApplicationRequirements(
        tipo_objeto = "Bottle cap",
        distancia_mm = 150,
        ambiente = "Wash-down, high humidity",
        material_superficie = "Reflective plastic",
        montaje = "Overhead"
    )
    val recommendation = Recommendation(
        sensorFamily = "W26",
        executiveSummary = "The W26 handles reflective caps at close range and tolerates the wash-down environment on the bottling line.",
        requirements = requirements,
        confidenceLevel = 0.88f,
        confidenceExplanation = "Strong match on environment and surface reflectivity; distance is well within range.",
        reasons = listOf(
            Reason("HDDM+ Technology", "Suppresses background reflections from the conveyor and neighboring bottles."),
            Reason("IP69K Rating", "Withstands high-pressure wash-down cycles common on bottling lines.")
        ),
        sources = listOf(SourceReference("W26 Product Page", "https://www.sick.com/w26"))
    )
    return Analysis(
        id = "2",
        description = "Checking the presence of caps on bottles",
        timestamp = System.currentTimeMillis() - 7_200_000,
        requirements = requirements,
        recommendation = recommendation,
        alternatives = listOf(
            AlternativeOption("W16", "Not rated for wash-down environments.")
        )
    )
}
