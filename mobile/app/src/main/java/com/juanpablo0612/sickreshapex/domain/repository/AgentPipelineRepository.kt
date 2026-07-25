package com.juanpablo0612.sickreshapex.domain.repository

import com.juanpablo0612.sickreshapex.domain.model.PipelineEvent
import kotlinx.coroutines.flow.Flow

/**
 * Streams the live progress of the orchestrator pipeline (intake -> clarificacion ->
 * retrieval -> evaluacion -> confianza -> resultado) for a single request. Implemented by a
 * websocket client that translates inbound `{etapa, estado, detalle}` frames into
 * [PipelineEvent]s.
 *
 * [sessionId] identifies the conversation on the backend: reuse it when re-submitting after
 * a needs_clarification result, and it is the key for the REST fallback `/result/{id}`.
 */
interface AgentPipelineRepository {
    fun streamAnalysis(userQuery: String, sessionId: String): Flow<PipelineEvent>
}
