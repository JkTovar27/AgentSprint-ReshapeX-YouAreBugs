package com.juanpablo0612.sickreshapex.domain.model

/**
 * Mirrors the orchestrator backend contract (`ws://<host>/ws/orchestrator`): the server
 * streams one JSON event per stage as `{"etapa": ..., "estado": "iniciado|completado",
 * "detalle": {...}}`, in the order intake -> clarificacion -> retrieval -> evaluacion ->
 * confianza, followed by a terminal `resultado` event. [PipelineEvent] is the domain-level
 * unit; a screen collects a Flow<PipelineEvent> to render live progress instead of waiting
 * for one final response.
 */
enum class AgentStage {
    INTAKE, CLARIFICATION, RETRIEVAL, EVALUATION, CONFIDENCE
}

enum class StageStatus { PENDING, ACTIVE, DONE, ERROR }

/** `detalle` payload of a completed stage — only fields the backend contract defines. */
sealed class StageDetail {

    /** intake -> {structured_requirements: {...}, missing_fields: [...]} */
    data class Intake(
        val structuredRequirements: List<Pair<String, String>>,
        val missingFields: List<String>
    ) : StageDetail()

    /** clarificacion -> {questions: [...], iteration_count: N} */
    data class Clarification(
        val questions: List<String>,
        val iterationCount: Int
    ) : StageDetail()

    /** retrieval -> {total_candidates: N} */
    data class Retrieval(
        val totalCandidates: Int
    ) : StageDetail()

    /**
     * evaluacion -> {candidatos: N}, plus one event per candidate carrying its
     * veredicto (`viable` | `descartado`). Either field may be absent on a given event.
     */
    data class Evaluation(
        val candidateCount: Int?,
        val verdict: CandidateVerdict?
    ) : StageDetail()

    /** confianza -> {score: 0-100, reasoning: "...", needs_human_review: bool} */
    data class Confidence(
        val score: Int,
        val reasoning: String,
        val needsHumanReview: Boolean
    ) : StageDetail()
}

data class CandidateVerdict(
    val candidate: String,
    val isViable: Boolean
)

enum class ResultStatus { RECOMMENDED, NEEDS_CLARIFICATION }

/** One sensor entry of the resultado shortlist/discards, kept schema-agnostic. */
data class SensorCandidate(
    val name: String,
    val attributes: List<Pair<String, String>>
)

data class ResultConfidence(
    val score: Int,
    val rationale: String
)

/**
 * Terminal `resultado` payload: {status, shortlist, discards, reasons, confidence, sources}
 * — plus `questions` when status = needs_clarification, which the UI must surface so the
 * user can answer and re-submit.
 */
data class PipelineResult(
    val status: ResultStatus,
    val shortlist: List<SensorCandidate>,
    val discards: List<SensorCandidate>,
    val reasons: Map<String, String>,
    val confidence: ResultConfidence?,
    val sources: List<String>,
    val questions: List<String>
)

/** One backend event as it arrives over the websocket connection. */
sealed class PipelineEvent {
    data class StageStarted(val stage: AgentStage) : PipelineEvent()
    data class StageCompleted(val stage: AgentStage, val detail: StageDetail?) : PipelineEvent()

    /**
     * Terminal event. [analysis] is non-null only when status = recommended: the data layer
     * persists the run so the results screen can load it by id afterward.
     */
    data class FinalResult(val result: PipelineResult, val analysis: Analysis?) : PipelineEvent()
}
