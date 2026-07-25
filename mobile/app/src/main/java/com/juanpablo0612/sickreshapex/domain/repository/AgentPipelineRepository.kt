package com.juanpablo0612.sickreshapex.domain.repository

import com.juanpablo0612.sickreshapex.domain.model.PipelineEvent
import kotlinx.coroutines.flow.Flow

/**
 * Streams the live progress of the Planner -> Retriever -> Validator -> Evaluator ->
 * Responder backend pipeline for a single request. Implemented today by a mock that
 * simulates realistic timing; a real implementation would open a websocket connection
 * and translate inbound frames into [PipelineEvent]s with this same contract.
 */
interface AgentPipelineRepository {
    fun streamAnalysis(description: String): Flow<PipelineEvent>
}
