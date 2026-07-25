package com.juanpablo0612.sickreshapex.domain.model

/**
 * Mirrors the multi-agent backend workflow (Planner -> Retriever -> Validator ->
 * Evaluator -> Responder) that will be streamed to the app over a websocket.
 * [PipelineEvent] is the wire-level unit; a screen collects a Flow<PipelineEvent>
 * to render live progress instead of waiting for one final response.
 */
enum class AgentStage {
    PLANNER, RETRIEVER, VALIDATOR, EVALUATOR, RESPONDER
}

enum class StageStatus { PENDING, ACTIVE, DONE, ERROR }

data class PlannerOutput(
    val structuredRequirements: List<Pair<String, String>>,
    val missingFields: List<String>,
    val extractionConfidence: Float,
    val plannerNotes: String
)

data class DatasheetSource(
    val datasheetId: String,
    val title: String,
    val confidence: Float
)

data class RetrievedCandidate(
    val productName: String,
    val sensorFamily: String,
    val specs: List<Pair<String, String>>
)

data class RetrieverOutput(
    val candidates: List<RetrievedCandidate>,
    val sources: List<DatasheetSource>,
    val retrievalNotes: String
)

data class ValidationCriterion(
    val name: String,
    val passed: Boolean,
    val detail: String
)

data class CandidateValidation(
    val productName: String,
    val criteria: List<ValidationCriterion>,
    val overallViability: Boolean,
    val viabilityReason: String
)

data class ValidatorOutput(
    val validations: List<CandidateValidation>,
    val viableCandidates: List<String>,
    val discardedCandidates: List<String>,
    val validatorNotes: String
)

enum class NextStep { RESPOND, CLARIFY, CONTINUE, ESCALATE }

data class EvaluatorOutput(
    val confidence: Float,
    val reasoning: String,
    val needsHumanReview: Boolean,
    val nextStep: NextStep,
    val escalationReason: String?,
    val evaluatorNotes: String
)

data class ResponderOutput(
    val status: String,
    val message: String,
    val confidence: Float,
    val escalation: String?
)

/** One message as it would arrive over the websocket connection. */
sealed class PipelineEvent {
    data class StageStarted(val stage: AgentStage) : PipelineEvent()
    data class PlannerCompleted(val output: PlannerOutput) : PipelineEvent()
    data class RetrieverCompleted(val output: RetrieverOutput) : PipelineEvent()
    data class ValidatorCompleted(val output: ValidatorOutput) : PipelineEvent()
    data class EvaluatorCompleted(val output: EvaluatorOutput) : PipelineEvent()
    data class ResponderCompleted(val output: ResponderOutput, val analysis: Analysis) : PipelineEvent()
    data class StageFailed(val stage: AgentStage, val reason: String) : PipelineEvent()
}
