package com.juanpablo0612.sickreshapex.data.remote

import com.google.gson.JsonObject
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * REST fallback of the orchestrator: `GET /result/{session_id}` returns the same
 * `resultado` object the websocket streams as its terminal event, so a dropped socket can
 * still resolve into a final answer. Parsed with [PipelineFrameParser.parseResult].
 */
interface OrchestratorApi {
    @GET("result/{sessionId}")
    suspend fun getResult(@Path("sessionId") sessionId: String): JsonObject
}
