package com.juanpablo0612.sickreshapex.domain.repository

import com.juanpablo0612.sickreshapex.domain.model.PipelineEvent
import kotlinx.coroutines.flow.Flow

/**
 * Streams the live progress of the Planner -> Retriever -> Validator -> Evaluator ->
 * Responder backend pipeline for a single request. Implemented by a websocket client
 * that translates inbound JSON frames into [PipelineEvent]s.
 */
interface AgentPipelineRepository {
    fun streamAnalysis(description: String): Flow<PipelineEvent>
}
