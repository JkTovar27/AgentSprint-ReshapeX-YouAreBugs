package com.juanpablo0612.sickreshapex.ui.features.processing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juanpablo0612.sickreshapex.domain.model.AgentStage
import com.juanpablo0612.sickreshapex.domain.model.PipelineEvent
import com.juanpablo0612.sickreshapex.domain.model.StageStatus
import com.juanpablo0612.sickreshapex.domain.repository.AgentPipelineRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the "Processing" screen by collecting the live [PipelineEvent] stream for a single
 * analysis request and folding it into [ProcessingState]. One instance == one run: [start]
 * is idempotent so a recomposition or process/config change replaying `LaunchedEffect(description)`
 * never re-kicks the pipeline or duplicates its side effects (e.g. saving the analysis).
 */
class ProcessingViewModel(
    private val pipelineRepository: AgentPipelineRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProcessingState())
    val uiState: StateFlow<ProcessingState> = _uiState.asStateFlow()

    private var hasStarted = false

    fun start(description: String) {
        if (hasStarted) return
        hasStarted = true

        viewModelScope.launch {
            pipelineRepository.streamAnalysis(description)
                .catch { throwable ->
                    _uiState.update {
                        it.copy(error = throwable.message ?: "Unexpected error while analyzing the application.")
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

                is PipelineEvent.PlannerCompleted ->
                    state.withStatus(AgentStage.PLANNER, StageStatus.DONE)
                        .copy(plannerOutput = event.output)

                is PipelineEvent.RetrieverCompleted ->
                    state.withStatus(AgentStage.RETRIEVER, StageStatus.DONE)
                        .copy(retrieverOutput = event.output)

                is PipelineEvent.ValidatorCompleted ->
                    state.withStatus(AgentStage.VALIDATOR, StageStatus.DONE)
                        .copy(validatorOutput = event.output)

                is PipelineEvent.EvaluatorCompleted ->
                    state.withStatus(AgentStage.EVALUATOR, StageStatus.DONE)
                        .copy(evaluatorOutput = event.output)

                is PipelineEvent.ResponderCompleted ->
                    state.withStatus(AgentStage.RESPONDER, StageStatus.DONE)
                        .copy(
                            responderOutput = event.output,
                            analysisId = event.analysis.id
                        )

                is PipelineEvent.StageFailed ->
                    state.withStatus(event.stage, StageStatus.ERROR)
                        .copy(failedStage = event.stage, error = event.reason)
            }
        }
    }

    private fun ProcessingState.withStatus(stage: AgentStage, status: StageStatus): ProcessingState =
        copy(stageStatuses = stageStatuses + (stage to status))
}
