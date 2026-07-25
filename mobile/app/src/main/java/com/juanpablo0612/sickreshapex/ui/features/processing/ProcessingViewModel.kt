package com.juanpablo0612.sickreshapex.ui.features.processing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juanpablo0612.sickreshapex.R
import com.juanpablo0612.sickreshapex.domain.model.AgentStage
import com.juanpablo0612.sickreshapex.domain.model.PipelineEvent
import com.juanpablo0612.sickreshapex.domain.model.StageDetail
import com.juanpablo0612.sickreshapex.domain.model.StageStatus
import com.juanpablo0612.sickreshapex.domain.repository.AgentPipelineRepository
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the "Processing" screen by collecting the live [PipelineEvent] stream for one
 * orchestrator session and folding it into [ProcessingState]. One instance == one session:
 * [start] is idempotent so recomposition never re-kicks the pipeline, and the generated
 * session id is reused by [sendClarification] re-submissions and the REST result fallback.
 */
class ProcessingViewModel(
    private val pipelineRepository: AgentPipelineRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProcessingState())
    val uiState: StateFlow<ProcessingState> = _uiState.asStateFlow()

    private val sessionId = UUID.randomUUID().toString()
    private var streamJob: Job? = null
    private var userQuery = ""

    fun start(description: String) {
        if (streamJob != null) return
        userQuery = description
        launchStream()
    }

    /**
     * Answers a needs_clarification result: appends the user's answer to the query and
     * re-runs the pipeline under the same session id.
     */
    fun sendClarification(answer: String) {
        if (answer.isBlank()) return
        userQuery = "$userQuery\n$answer"
        _uiState.value = ProcessingState()
        launchStream()
    }

    private fun launchStream() {
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            pipelineRepository.streamAnalysis(userQuery, sessionId)
                .catch { throwable ->
                    _uiState.update {
                        it.copy(
                            error = throwable.message,
                            errorRes = R.string.error_unexpected_analysis
                        )
                    }
                }
                .collect { event -> reduce(event) }
        }
    }

    private fun reduce(event: PipelineEvent) {
        _uiState.update { state ->
            when (event) {
                is PipelineEvent.StageStarted ->
                    state.withStatus(event.stage, StageStatus.ACTIVE)

                is PipelineEvent.StageCompleted ->
                    state.withStatus(event.stage, StageStatus.DONE).withDetail(event.detail)

                is PipelineEvent.FinalResult ->
                    state.copy(
                        result = event.result,
                        analysisId = event.analysis?.id
                    )
            }
        }
    }

    private fun ProcessingState.withDetail(detail: StageDetail?): ProcessingState = when (detail) {
        is StageDetail.Intake -> copy(intake = detail)
        is StageDetail.Clarification -> copy(clarification = detail)
        is StageDetail.Retrieval -> copy(retrieval = detail)
        is StageDetail.Evaluation -> copy(
            evaluationCandidateCount = detail.candidateCount ?: evaluationCandidateCount,
            verdicts = detail.verdict?.let { verdicts + it } ?: verdicts
        )
        is StageDetail.Confidence -> copy(confidence = detail)
        null -> this
    }

    private fun ProcessingState.withStatus(stage: AgentStage, status: StageStatus): ProcessingState =
        copy(stageStatuses = stageStatuses + (stage to status))
}
