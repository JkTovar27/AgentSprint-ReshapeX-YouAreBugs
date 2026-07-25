package com.juanpablo0612.sickreshapex.ui.features.processing

import com.juanpablo0612.sickreshapex.domain.model.AgentStage
import com.juanpablo0612.sickreshapex.domain.model.EvaluatorOutput
import com.juanpablo0612.sickreshapex.domain.model.PlannerOutput
import com.juanpablo0612.sickreshapex.domain.model.ResponderOutput
import com.juanpablo0612.sickreshapex.domain.model.RetrieverOutput
import com.juanpablo0612.sickreshapex.domain.model.StageStatus
import com.juanpablo0612.sickreshapex.domain.model.ValidatorOutput

/**
 * Live-progress state for the 5-agent pipeline (Planner -> Retriever -> Validator ->
 * Evaluator -> Responder). One [ProcessingViewModel] instance drives one run: each
 * [com.juanpablo0612.sickreshapex.domain.model.PipelineEvent] folds into this state so the
 * screen can render an always-consistent snapshot instead of reacting to raw events.
 */
data class ProcessingState(
    val stageStatuses: Map<AgentStage, StageStatus> = AgentStage.entries.associateWith { StageStatus.PENDING },
    val plannerOutput: PlannerOutput? = null,
    val retrieverOutput: RetrieverOutput? = null,
    val validatorOutput: ValidatorOutput? = null,
    val evaluatorOutput: EvaluatorOutput? = null,
    val responderOutput: ResponderOutput? = null,
    val analysisId: String? = null,
    val failedStage: AgentStage? = null,
    val error: String? = null
) {
    val completedCount: Int
        get() = stageStatuses.values.count { it == StageStatus.DONE }

    val overallProgress: Float
        get() = completedCount / AgentStage.entries.size.toFloat()

    val hasFailed: Boolean
        get() = error != null
}
