package com.juanpablo0612.sickreshapex.domain.model

data class Recommendation(
    val sensorFamily: String,
    val executiveSummary: String,
    val requirements: ApplicationRequirements,
    val confidenceLevel: Float, // 0.0 to 1.0
    val confidenceExplanation: String,
    val reasons: List<Reason>,
    val sources: List<SourceReference>
)

data class Reason(
    val title: String,
    val description: String
)

data class AlternativeOption(
    val sensorFamily: String,
    val discardReason: String
)

data class SourceReference(
    val title: String,
    val url: String
)

data class Analysis(
    val id: String,
    val description: String,
    val timestamp: Long,
    val requirements: ApplicationRequirements?,
    val recommendation: Recommendation?,
    val alternatives: List<AlternativeOption>
)

data class RecentAnalysis(
    val id: String,
    val description: String,
    val timestamp: Long,
    val sensorFamily: String?
)

data class QuickExample(
    val title: String,
    val description: String
)

data class FavoriteAnalysis(
    val analysisId: String,
    val title: String,
    val timestamp: Long
)

data class UserSettings(
    val theme: String, // "light", "dark", "system"
    val notificationsEnabled: Boolean
)
