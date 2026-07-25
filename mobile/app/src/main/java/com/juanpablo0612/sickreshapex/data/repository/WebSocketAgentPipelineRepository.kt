package com.juanpablo0612.sickreshapex.data.repository

import android.util.Log
import com.juanpablo0612.sickreshapex.data.remote.OrchestratorApi
import com.juanpablo0612.sickreshapex.data.remote.PipelineFrameParser
import com.juanpablo0612.sickreshapex.data.remote.toAnalysis
import com.juanpablo0612.sickreshapex.domain.model.PipelineEvent
import com.juanpablo0612.sickreshapex.domain.model.ResultStatus
import com.juanpablo0612.sickreshapex.domain.model.StageDetail
import com.juanpablo0612.sickreshapex.domain.repository.AgentPipelineRepository
import com.juanpablo0612.sickreshapex.domain.repository.AnalysisRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Streams the orchestrator pipeline (intake -> clarificacion -> retrieval -> evaluacion ->
 * confianza -> resultado) over a live websocket. One [streamAnalysis] call == one
 * connection: it opens the socket, sends `{user_query, session_id}`, and emits each inbound
 * `{etapa, estado, detalle}` frame as a [PipelineEvent] until the terminal `resultado`
 * arrives. If the socket dies before that, the REST fallback `GET /result/{session_id}` gets
 * one chance to recover the final result before the flow fails.
 */
class WebSocketAgentPipelineRepository(
    private val client: OkHttpClient,
    private val parser: PipelineFrameParser,
    private val analysisRepository: AnalysisRepository,
    private val api: OrchestratorApi,
    private val orchestratorWsUrl: String
) : AgentPipelineRepository {

    override fun streamAnalysis(userQuery: String, sessionId: String): Flow<PipelineEvent> {
        // Latest intake detail seen this run — feeds requirement fields of the saved Analysis.
        var intake: StageDetail.Intake? = null

        return socketEvents(userQuery, sessionId)
            .catch { error ->
                val recovered = runCatching { api.getResult(sessionId) }.getOrNull()
                    ?: throw error
                emit(PipelineEvent.FinalResult(parser.parseResult(recovered), analysis = null))
            }
            .map { event ->
                when (event) {
                    is PipelineEvent.StageCompleted -> {
                        (event.detail as? StageDetail.Intake)?.let { intake = it }
                        event
                    }

                    is PipelineEvent.FinalResult -> {
                        // An empty shortlist means there is nothing to recommend — don't save a
                        // hollow Analysis; the UI reports "finished without data" instead.
                        val analysis = event.result
                            .takeIf { it.status == ResultStatus.RECOMMENDED && it.shortlist.isNotEmpty() }
                            ?.toAnalysis(userQuery, sessionId, intake)
                            ?.also { analysisRepository.saveAnalysis(it) }
                        event.copy(analysis = analysis)
                    }

                    else -> event
                }
            }
    }

    private fun socketEvents(userQuery: String, sessionId: String): Flow<PipelineEvent> =
        callbackFlow {
            val sawResult = AtomicBoolean(false)

            val listener = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send(parser.encodeStartRequest(userQuery, sessionId))
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    Log.d(TAG, "frame <- $text")
                    val event = runCatching { parser.parse(text) }
                        .getOrNull() ?: return // unknown or malformed frame — keep streaming

                    trySend(event)

                    if (event is PipelineEvent.FinalResult) {
                        sawResult.set(true)
                        webSocket.close(NORMAL_CLOSURE, "pipeline finished")
                        close()
                    }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(code, reason)
                    closeCompleteOrFail()
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    closeCompleteOrFail()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.w(TAG, "websocket failure (http=${response?.code})", t)
                    close(IOException("Pipeline websocket failed: ${t.message}", t))
                }

                private fun closeCompleteOrFail() {
                    if (sawResult.get()) {
                        close()
                    } else {
                        close(IOException("Pipeline connection closed before the analysis finished"))
                    }
                }
            }

            val webSocket = client.newWebSocket(
                Request.Builder().url(orchestratorWsUrl).build(),
                listener
            )
            awaitClose { webSocket.cancel() }
        }

    private companion object {
        const val NORMAL_CLOSURE = 1000
        const val TAG = "OrchestratorWS"
    }
}
