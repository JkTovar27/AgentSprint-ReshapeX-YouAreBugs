package com.juanpablo0612.sickreshapex.data.remote

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import com.juanpablo0612.sickreshapex.domain.model.*
import java.util.UUID

/**
 * Wire protocol of the agent-pipeline websocket (`/ws/analyze` on the FastAPI backend).
 * Every frame is one JSON text message. This file is the single source of truth for the
 * contract on the mobile side — the backend endpoint must emit frames with these shapes.
 *
 * Client -> server, sent once right after the connection opens:
 * ```json
 * {"type": "start_analysis", "description": "<free-text application description>"}
 * ```
 *
 * Server -> client, in pipeline order (`stage` is one of
 * `planner|retriever|validator|evaluator|responder`):
 * ```json
 * {"type": "stage_started",      "stage": "planner"}
 * {"type": "planner_completed",  "output": {"structured_requirements": [{"name": "...", "value": "..."}],
 *                                           "missing_fields": [], "extraction_confidence": 0.93, "notes": "..."}}
 * {"type": "retriever_completed","output": {"candidates": [{"product_name": "...", "sensor_family": "...",
 *                                            "specs": [{"name": "...", "value": "..."}]}],
 *                                           "sources": [{"datasheet_id": "...", "title": "...", "confidence": 0.97}],
 *                                           "notes": "..."}}
 * {"type": "validator_completed","output": {"validations": [{"product_name": "...",
 *                                            "criteria": [{"name": "...", "passed": true, "detail": "..."}],
 *                                            "overall_viability": true, "viability_reason": "..."}],
 *                                           "viable_candidates": [], "discarded_candidates": [], "notes": "..."}}
 * {"type": "evaluator_completed","output": {"confidence": 0.95, "reasoning": "...", "needs_human_review": false,
 *                                           "next_step": "respond|clarify|continue|escalate",
 *                                           "escalation_reason": null, "notes": "..."}}
 * {"type": "responder_completed","output": {"status": "success", "message": "...", "confidence": 0.95,
 *                                           "escalation": null},
 *                                "analysis": {"id": "...", "description": "...", "timestamp": 1750000000000,
 *                                             "requirements": {"tipo_objeto": "...", "distancia_mm": 500,
 *                                                              "ambiente": "...", "material_superficie": "...",
 *                                                              "montaje": "..."},
 *                                             "recommendation": {"sensor_family": "...", "executive_summary": "...",
 *                                                                "requirements": {...}, "confidence_level": 0.95,
 *                                                                "confidence_explanation": "...",
 *                                                                "reasons": [{"title": "...", "description": "..."}],
 *                                                                "sources": [{"title": "...", "url": "..."}]},
 *                                             "alternatives": [{"sensor_family": "...", "discard_reason": "..."}]}}
 * {"type": "stage_failed",       "stage": "planner", "reason": "human-readable failure"}
 * ```
 *
 * `responder_completed` and `stage_failed` are terminal; the client closes the socket after
 * either. Unknown frame types are ignored so the backend can add heartbeats or new events
 * without breaking older clients.
 */
class PipelineFrameParser(private val gson: Gson) {

    fun encodeStartRequest(description: String): String =
        gson.toJson(StartAnalysisFrame(description = description))

    /**
     * Translates one inbound frame into a domain [PipelineEvent], or null for frame types
     * this client does not understand. [requestDescription] backfills the persisted
     * [Analysis] if the responder frame omits one.
     */
    fun parse(text: String, requestDescription: String): PipelineEvent? {
        val root = JsonParser.parseString(text).asJsonObject
        return when (root.get("type")?.asString) {
            "stage_started" -> root.stage()?.let { PipelineEvent.StageStarted(it) }

            "planner_completed" -> PipelineEvent.PlannerCompleted(
                root.output(PlannerOutputDto::class.java).toDomain()
            )

            "retriever_completed" -> PipelineEvent.RetrieverCompleted(
                root.output(RetrieverOutputDto::class.java).toDomain()
            )

            "validator_completed" -> PipelineEvent.ValidatorCompleted(
                root.output(ValidatorOutputDto::class.java).toDomain()
            )

            "evaluator_completed" -> PipelineEvent.EvaluatorCompleted(
                root.output(EvaluatorOutputDto::class.java).toDomain()
            )

            "responder_completed" -> PipelineEvent.ResponderCompleted(
                output = root.output(ResponderOutputDto::class.java).toDomain(),
                analysis = gson.fromJson(root.get("analysis"), AnalysisDto::class.java)
                    ?.toDomain() ?: fallbackAnalysis(requestDescription)
            )

            "stage_failed" -> PipelineEvent.StageFailed(
                stage = root.stage() ?: AgentStage.PLANNER,
                reason = root.get("reason")?.asString ?: "Unknown pipeline failure"
            )

            else -> null
        }
    }

    private fun <T> JsonObject.output(dtoClass: Class<T>): T =
        gson.fromJson(get("output"), dtoClass)

    private fun JsonObject.stage(): AgentStage? =
        AgentStage.entries.firstOrNull { it.name.equals(get("stage")?.asString, ignoreCase = true) }

    /** The responder frame carried no analysis payload — keep the run navigable anyway. */
    private fun fallbackAnalysis(description: String) = Analysis(
        id = UUID.randomUUID().toString(),
        description = description,
        timestamp = System.currentTimeMillis(),
        requirements = null,
        recommendation = null,
        alternatives = emptyList()
    )
}

private data class StartAnalysisFrame(
    val type: String = "start_analysis",
    val description: String
)

/* DTO fields are nullable because Gson bypasses Kotlin null checks on malformed frames;
   mapping applies safe defaults instead of throwing deep inside the stream. */

private data class NamedValueDto(
    val name: String?,
    val value: String?
)

private fun List<NamedValueDto>?.toPairs(): List<Pair<String, String>> =
    orEmpty().map { (it.name ?: "") to (it.value ?: "") }

private data class PlannerOutputDto(
    @SerializedName("structured_requirements") val structuredRequirements: List<NamedValueDto>?,
    @SerializedName("missing_fields") val missingFields: List<String>?,
    @SerializedName("extraction_confidence") val extractionConfidence: Float?,
    val notes: String?
) {
    fun toDomain() = PlannerOutput(
        structuredRequirements = structuredRequirements.toPairs(),
        missingFields = missingFields.orEmpty(),
        extractionConfidence = extractionConfidence ?: 0f,
        plannerNotes = notes.orEmpty()
    )
}

private data class RetrievedCandidateDto(
    @SerializedName("product_name") val productName: String?,
    @SerializedName("sensor_family") val sensorFamily: String?,
    val specs: List<NamedValueDto>?
)

private data class DatasheetSourceDto(
    @SerializedName("datasheet_id") val datasheetId: String?,
    val title: String?,
    val confidence: Float?
)

private data class RetrieverOutputDto(
    val candidates: List<RetrievedCandidateDto>?,
    val sources: List<DatasheetSourceDto>?,
    val notes: String?
) {
    fun toDomain() = RetrieverOutput(
        candidates = candidates.orEmpty().map {
            RetrievedCandidate(
                productName = it.productName.orEmpty(),
                sensorFamily = it.sensorFamily.orEmpty(),
                specs = it.specs.toPairs()
            )
        },
        sources = sources.orEmpty().map {
            DatasheetSource(
                datasheetId = it.datasheetId.orEmpty(),
                title = it.title.orEmpty(),
                confidence = it.confidence ?: 0f
            )
        },
        retrievalNotes = notes.orEmpty()
    )
}

private data class ValidationCriterionDto(
    val name: String?,
    val passed: Boolean?,
    val detail: String?
)

private data class CandidateValidationDto(
    @SerializedName("product_name") val productName: String?,
    val criteria: List<ValidationCriterionDto>?,
    @SerializedName("overall_viability") val overallViability: Boolean?,
    @SerializedName("viability_reason") val viabilityReason: String?
)

private data class ValidatorOutputDto(
    val validations: List<CandidateValidationDto>?,
    @SerializedName("viable_candidates") val viableCandidates: List<String>?,
    @SerializedName("discarded_candidates") val discardedCandidates: List<String>?,
    val notes: String?
) {
    fun toDomain() = ValidatorOutput(
        validations = validations.orEmpty().map { validation ->
            CandidateValidation(
                productName = validation.productName.orEmpty(),
                criteria = validation.criteria.orEmpty().map {
                    ValidationCriterion(
                        name = it.name.orEmpty(),
                        passed = it.passed ?: false,
                        detail = it.detail.orEmpty()
                    )
                },
                overallViability = validation.overallViability ?: false,
                viabilityReason = validation.viabilityReason.orEmpty()
            )
        },
        viableCandidates = viableCandidates.orEmpty(),
        discardedCandidates = discardedCandidates.orEmpty(),
        validatorNotes = notes.orEmpty()
    )
}

private data class EvaluatorOutputDto(
    val confidence: Float?,
    val reasoning: String?,
    @SerializedName("needs_human_review") val needsHumanReview: Boolean?,
    @SerializedName("next_step") val nextStep: String?,
    @SerializedName("escalation_reason") val escalationReason: String?,
    val notes: String?
) {
    fun toDomain() = EvaluatorOutput(
        confidence = confidence ?: 0f,
        reasoning = reasoning.orEmpty(),
        needsHumanReview = needsHumanReview ?: false,
        nextStep = NextStep.entries.firstOrNull { it.name.equals(nextStep, ignoreCase = true) }
            ?: NextStep.CONTINUE,
        escalationReason = escalationReason,
        evaluatorNotes = notes.orEmpty()
    )
}

private data class ResponderOutputDto(
    val status: String?,
    val message: String?,
    val confidence: Float?,
    val escalation: String?
) {
    fun toDomain() = ResponderOutput(
        status = status.orEmpty(),
        message = message.orEmpty(),
        confidence = confidence ?: 0f,
        escalation = escalation
    )
}

private data class RequirementsDto(
    @SerializedName("tipo_objeto") val tipoObjeto: String?,
    @SerializedName("distancia_mm") val distanciaMm: Int?,
    val ambiente: String?,
    @SerializedName("material_superficie") val materialSuperficie: String?,
    val montaje: String?
) {
    fun toDomain() = ApplicationRequirements(
        tipo_objeto = tipoObjeto.orEmpty(),
        distancia_mm = distanciaMm ?: 0,
        ambiente = ambiente.orEmpty(),
        material_superficie = materialSuperficie.orEmpty(),
        montaje = montaje.orEmpty()
    )
}

private data class ReasonDto(val title: String?, val description: String?)

private data class SourceReferenceDto(val title: String?, val url: String?)

private data class RecommendationDto(
    @SerializedName("sensor_family") val sensorFamily: String?,
    @SerializedName("executive_summary") val executiveSummary: String?,
    val requirements: RequirementsDto?,
    @SerializedName("confidence_level") val confidenceLevel: Float?,
    @SerializedName("confidence_explanation") val confidenceExplanation: String?,
    val reasons: List<ReasonDto>?,
    val sources: List<SourceReferenceDto>?
) {
    fun toDomain(fallbackRequirements: ApplicationRequirements?) = Recommendation(
        sensorFamily = sensorFamily.orEmpty(),
        executiveSummary = executiveSummary.orEmpty(),
        requirements = (requirements?.toDomain() ?: fallbackRequirements)
            ?: ApplicationRequirements("", 0, "", "", ""),
        confidenceLevel = confidenceLevel ?: 0f,
        confidenceExplanation = confidenceExplanation.orEmpty(),
        reasons = reasons.orEmpty().map { Reason(it.title.orEmpty(), it.description.orEmpty()) },
        sources = sources.orEmpty().map { SourceReference(it.title.orEmpty(), it.url.orEmpty()) }
    )
}

private data class AlternativeOptionDto(
    @SerializedName("sensor_family") val sensorFamily: String?,
    @SerializedName("discard_reason") val discardReason: String?
)

private data class AnalysisDto(
    val id: String?,
    val description: String?,
    val timestamp: Long?,
    val requirements: RequirementsDto?,
    val recommendation: RecommendationDto?,
    val alternatives: List<AlternativeOptionDto>?
) {
    fun toDomain(): Analysis {
        val domainRequirements = requirements?.toDomain()
        return Analysis(
            id = id ?: UUID.randomUUID().toString(),
            description = description.orEmpty(),
            timestamp = timestamp ?: System.currentTimeMillis(),
            requirements = domainRequirements,
            recommendation = recommendation?.toDomain(domainRequirements),
            alternatives = alternatives.orEmpty().map {
                AlternativeOption(
                    sensorFamily = it.sensorFamily.orEmpty(),
                    discardReason = it.discardReason.orEmpty()
                )
            }
        )
    }
}
