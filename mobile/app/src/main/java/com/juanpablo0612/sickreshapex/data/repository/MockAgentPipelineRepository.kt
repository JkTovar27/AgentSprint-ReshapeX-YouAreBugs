package com.juanpablo0612.sickreshapex.data.repository

import com.juanpablo0612.sickreshapex.domain.model.*
import com.juanpablo0612.sickreshapex.domain.repository.AgentPipelineRepository
import com.juanpablo0612.sickreshapex.domain.repository.AnalysisRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID

/**
 * Simulates the websocket stream from the Planner/Retriever/Validator/Evaluator/Responder
 * backend with realistic per-stage timing and data shaped exactly like each agent's real
 * response, so the Processing screen has something believable to animate against.
 *
 * Swap this for a websocket-backed [AgentPipelineRepository] once the backend is live —
 * only this class changes, the [PipelineEvent] contract consumed by the UI stays the same.
 */
class MockAgentPipelineRepository(
    private val analysisRepository: AnalysisRepository
) : AgentPipelineRepository {

    override fun streamAnalysis(description: String): Flow<PipelineEvent> = flow {
        // --- 1. Planner ---
        emit(PipelineEvent.StageStarted(AgentStage.PLANNER))
        delay(850)
        emit(
            PipelineEvent.PlannerCompleted(
                PlannerOutput(
                    structuredRequirements = listOf(
                        "Object type" to "Cardboard box",
                        "Distance" to "500 mm",
                        "Environment" to "Standard industrial",
                        "Surface" to "Matte",
                        "Mounting" to "Side mount"
                    ),
                    missingFields = emptyList(),
                    extractionConfidence = 0.93f,
                    plannerNotes = "Extracted 5 of 5 mandatory fields from the request. High-confidence parse — proceeding to retrieval."
                )
            )
        )

        // --- 2. Retriever ---
        emit(PipelineEvent.StageStarted(AgentStage.RETRIEVER))
        delay(1050)
        emit(
            PipelineEvent.RetrieverCompleted(
                RetrieverOutput(
                    candidates = listOf(
                        RetrievedCandidate(
                            productName = "W16-3",
                            sensorFamily = "W16",
                            specs = listOf("Range" to "0-2000 mm", "Technology" to "TwinEye")
                        ),
                        RetrievedCandidate(
                            productName = "W26-3",
                            sensorFamily = "W26",
                            specs = listOf("Range" to "0-3200 mm", "Technology" to "HDDM+")
                        ),
                        RetrievedCandidate(
                            productName = "WTB4S-3",
                            sensorFamily = "WTB4S",
                            specs = listOf("Range" to "0-600 mm", "Technology" to "Background suppression")
                        )
                    ),
                    sources = listOf(
                        DatasheetSource("DS-W16-2024", "W16 Photoelectric Sensor Datasheet", 0.97f),
                        DatasheetSource("DS-W26-2024", "W26 Photoelectric Sensor Datasheet", 0.91f),
                        DatasheetSource("DS-WTB4S-2023", "WTB4S Photoelectric Sensor Datasheet", 0.84f)
                    ),
                    retrievalNotes = "Semantic search over the SICK knowledge base returned 3 candidates from the photoelectric cluster."
                )
            )
        )

        // --- 3. Validator ---
        emit(PipelineEvent.StageStarted(AgentStage.VALIDATOR))
        delay(1150)
        emit(
            PipelineEvent.ValidatorCompleted(
                ValidatorOutput(
                    validations = listOf(
                        CandidateValidation(
                            productName = "W16-3",
                            criteria = listOf(
                                ValidationCriterion("Range", true, "500 mm within 0-2000 mm"),
                                ValidationCriterion("Surface", true, "Matte cardboard reliably detected via TwinEye"),
                                ValidationCriterion("Interface", true, "IO-Link supported")
                            ),
                            overallViability = true,
                            viabilityReason = "Meets every mandatory requirement with margin."
                        ),
                        CandidateValidation(
                            productName = "W26-3",
                            criteria = listOf(
                                ValidationCriterion("Range", true, "500 mm within 0-3200 mm"),
                                ValidationCriterion("Cost efficiency", false, "Over-specified for a 500 mm range")
                            ),
                            overallViability = true,
                            viabilityReason = "Technically viable but higher cost than necessary."
                        ),
                        CandidateValidation(
                            productName = "WTB4S-3",
                            criteria = listOf(
                                ValidationCriterion("Range", false, "500 mm exceeds the reliable margin of a 0-600 mm sensor")
                            ),
                            overallViability = false,
                            viabilityReason = "Fails range margin at the requested mounting distance."
                        )
                    ),
                    viableCandidates = listOf("W16-3", "W26-3"),
                    discardedCandidates = listOf("WTB4S-3"),
                    validatorNotes = "2 of 3 candidates passed every mandatory rule; 1 discarded on range margin."
                )
            )
        )

        // --- 4. Evaluator ---
        emit(PipelineEvent.StageStarted(AgentStage.EVALUATOR))
        delay(800)
        emit(
            PipelineEvent.EvaluatorCompleted(
                EvaluatorOutput(
                    confidence = 0.95f,
                    reasoning = "Top candidate satisfies all mandatory criteria with margin and lower cost than the runner-up.",
                    needsHumanReview = false,
                    nextStep = NextStep.RESPOND,
                    escalationReason = null,
                    evaluatorNotes = "Confidence exceeds the 0.85 auto-respond threshold."
                )
            )
        )

        // --- 5. Responder ---
        emit(PipelineEvent.StageStarted(AgentStage.RESPONDER))
        delay(650)

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
            confidenceExplanation = "High match based on object type, surface and distance requirements.",
            reasons = listOf(
                Reason("TwinEye Technology", "Provides reliable detection of matte or glossy cardboard regardless of print pattern."),
                Reason("BluePilot", "Allows fast and intuitive alignment during installation."),
                Reason("Cost efficiency", "Meets requirements without the added cost of a longer-range sensor.")
            ),
            sources = listOf(
                SourceReference("W16 Product Page", "https://www.sick.com/w16"),
                SourceReference("W16 Datasheet DS-W16-2024", "https://www.sick.com/w16/datasheet")
            )
        )
        val analysis = Analysis(
            id = UUID.randomUUID().toString(),
            description = description,
            timestamp = System.currentTimeMillis(),
            requirements = requirements,
            recommendation = recommendation,
            alternatives = listOf(
                AlternativeOption("W26", "Technically viable but over-specified for this range, higher cost."),
                AlternativeOption("WTB4S", "Discarded: requested distance exceeds its reliable range margin.")
            )
        )
        analysisRepository.saveAnalysis(analysis)

        emit(
            PipelineEvent.ResponderCompleted(
                output = ResponderOutput(
                    status = "success",
                    message = "Recommendation generated with high confidence.",
                    confidence = 0.95f,
                    escalation = null
                ),
                analysis = analysis
            )
        )
    }
}
