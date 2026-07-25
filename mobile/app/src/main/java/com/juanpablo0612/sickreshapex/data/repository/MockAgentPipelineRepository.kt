package com.juanpablo0612.sickreshapex.data.repository

import com.juanpablo0612.sickreshapex.data.remote.toAnalysis
import com.juanpablo0612.sickreshapex.domain.model.*
import com.juanpablo0612.sickreshapex.domain.repository.AgentPipelineRepository
import com.juanpablo0612.sickreshapex.domain.repository.AnalysisRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Simulates the orchestrator websocket stream (intake -> clarificacion -> retrieval ->
 * evaluacion -> confianza -> resultado) with realistic per-stage timing and detail payloads
 * shaped exactly like the backend contract, so the Processing screen can be demoed offline.
 *
 * The real implementation is [WebSocketAgentPipelineRepository]; both feed the same
 * [PipelineEvent] contract, so swapping them is a one-line DI change.
 */
class MockAgentPipelineRepository(
    private val analysisRepository: AnalysisRepository
) : AgentPipelineRepository {

    override fun streamAnalysis(userQuery: String, sessionId: String): Flow<PipelineEvent> = flow {
        val intake = StageDetail.Intake(
            structuredRequirements = listOf(
                "tipo_objeto" to "Caja de cartón",
                "distancia_mm" to "500",
                "ambiente" to "Industrial estándar",
                "material_superficie" to "Mate",
                "montaje" to "Lateral"
            ),
            missingFields = emptyList()
        )

        emit(PipelineEvent.StageStarted(AgentStage.INTAKE))
        delay(850)
        emit(PipelineEvent.StageCompleted(AgentStage.INTAKE, intake))

        emit(PipelineEvent.StageStarted(AgentStage.CLARIFICATION))
        delay(600)
        emit(
            PipelineEvent.StageCompleted(
                AgentStage.CLARIFICATION,
                StageDetail.Clarification(questions = emptyList(), iterationCount = 1)
            )
        )

        emit(PipelineEvent.StageStarted(AgentStage.RETRIEVAL))
        delay(1050)
        emit(
            PipelineEvent.StageCompleted(
                AgentStage.RETRIEVAL,
                StageDetail.Retrieval(totalCandidates = 3)
            )
        )

        emit(PipelineEvent.StageStarted(AgentStage.EVALUATION))
        delay(500)
        emit(
            PipelineEvent.StageCompleted(
                AgentStage.EVALUATION,
                StageDetail.Evaluation(candidateCount = 3, verdict = null)
            )
        )
        for ((name, viable) in listOf("W16" to true, "W26" to true, "WTB4S" to false)) {
            delay(350)
            emit(
                PipelineEvent.StageCompleted(
                    AgentStage.EVALUATION,
                    StageDetail.Evaluation(
                        candidateCount = null,
                        verdict = CandidateVerdict(name, viable)
                    )
                )
            )
        }

        emit(PipelineEvent.StageStarted(AgentStage.CONFIDENCE))
        delay(800)
        emit(
            PipelineEvent.StageCompleted(
                AgentStage.CONFIDENCE,
                StageDetail.Confidence(
                    score = 95,
                    reasoning = "El candidato principal cumple todos los requisitos con margen.",
                    needsHumanReview = false
                )
            )
        )

        delay(500)
        val result = PipelineResult(
            status = ResultStatus.RECOMMENDED,
            shortlist = listOf(
                SensorCandidate("W16", listOf("range" to "0-2000 mm", "technology" to "TwinEye")),
                SensorCandidate("W26", listOf("range" to "0-3200 mm", "technology" to "HDDM+"))
            ),
            discards = listOf(
                SensorCandidate("WTB4S", listOf("range" to "0-600 mm"))
            ),
            reasons = mapOf(
                "W16" to "Detección fiable de cartón mate a 500 mm con margen y menor costo.",
                "W26" to "Viable pero sobredimensionado para 500 mm.",
                "WTB4S" to "La distancia solicitada excede su margen fiable de alcance."
            ),
            confidence = ResultConfidence(
                score = 95,
                rationale = "Coincidencia alta según tipo de objeto, superficie y distancia."
            ),
            sources = listOf(
                "https://www.sick.com/w16",
                "W16 Datasheet DS-W16-2024"
            ),
            questions = emptyList()
        )
        val analysis = result.toAnalysis(userQuery, sessionId, intake)
        analysisRepository.saveAnalysis(analysis)
        emit(PipelineEvent.FinalResult(result, analysis))
    }
}
