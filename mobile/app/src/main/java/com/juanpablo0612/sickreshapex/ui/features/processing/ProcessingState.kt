package com.juanpablo0612.sickreshapex.ui.features.processing

import androidx.annotation.StringRes
import com.juanpablo0612.sickreshapex.domain.model.AgentStage
import com.juanpablo0612.sickreshapex.domain.model.CandidateVerdict
import com.juanpablo0612.sickreshapex.domain.model.PipelineResult
import com.juanpablo0612.sickreshapex.domain.model.ResultStatus
import com.juanpablo0612.sickreshapex.domain.model.StageDetail
import com.juanpablo0612.sickreshapex.domain.model.StageStatus

/**
 * Live-progress state for the orchestrator pipeline (intake -> clarificacion -> retrieval ->
 * evaluacion -> confianza). One [ProcessingViewModel] instance drives one session: each
 * [com.juanpablo0612.sickreshapex.domain.model.PipelineEvent] folds into this state so the
 * screen renders an always-consistent snapshot instead of reacting to raw events.
 */
data class ProcessingState(
    val stageStatuses: Map<AgentStage, StageStatus> = AgentStage.entries.associateWith { StageStatus.PENDING },
    val intake: StageDetail.Intake? = null,
    val clarification: StageDetail.Clarification? = null,
    val retrieval: StageDetail.Retrieval? = null,
    val evaluationCandidateCount: Int? = null,
    val verdicts: List<CandidateVerdict> = emptyList(),
    val confidence: StageDetail.Confidence? = null,
    val result: PipelineResult? = null,
    val analysisId: String? = null,
    val error: String? = null,
    @StringRes val errorRes: Int? = null
) {
    val completedCount: Int
        get() = stageStatuses.values.count { it == StageStatus.DONE }

    val overallProgress: Float
        get() = completedCount / AgentStage.entries.size.toFloat()

    val hasFailed: Boolean
        get() = error != null || errorRes != null

    /** The backend needs more input: show [PipelineResult.questions] and let the user re-submit. */
    val needsClarification: Boolean
        get() = result?.status == ResultStatus.NEEDS_CLARIFICATION

    /**
     * The pipeline delivered its terminal result but there was nothing to recommend (empty
     * shortlist), so no Analysis was saved and there is no results screen to hand off to.
     */
    val finishedWithoutData: Boolean
        get() = result != null && !needsClarification && analysisId == null

    val clarificationQuestions: List<String>
        get() = result?.questions?.takeIf { it.isNotEmpty() }
            ?: clarification?.questions.orEmpty()
}
