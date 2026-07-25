package com.juanpablo0612.sickreshapex.data.repository

import com.juanpablo0612.sickreshapex.data.remote.PipelineFrameParser
import com.juanpablo0612.sickreshapex.domain.model.PipelineEvent
import com.juanpablo0612.sickreshapex.domain.repository.AgentPipelineRepository
import com.juanpablo0612.sickreshapex.domain.repository.AnalysisRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.onEach
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Streams the Planner -> Retriever -> Validator -> Evaluator -> Responder pipeline over a
 * live websocket. One [streamAnalysis] call == one connection: it opens the socket, sends
 * the `start_analysis` frame, and emits each inbound frame as a [PipelineEvent] until a
 * terminal frame (`responder_completed` / `stage_failed`) arrives. Cancelling the
 * collecting coroutine tears the connection down.
 */
class WebSocketAgentPipelineRepository(
    private val client: OkHttpClient,
    private val parser: PipelineFrameParser,
    private val analysisRepository: AnalysisRepository,
    private val serverUrl: String
) : AgentPipelineRepository {

    override fun streamAnalysis(description: String): Flow<PipelineEvent> = callbackFlow {
        val sawTerminalEvent = AtomicBoolean(false)

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(parser.encodeStartRequest(description))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val event = runCatching { parser.parse(text, description) }
                    .getOrNull() ?: return // unknown or malformed frame — keep streaming

                trySend(event)

                if (event is PipelineEvent.ResponderCompleted || event is PipelineEvent.StageFailed) {
                    sawTerminalEvent.set(true)
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
                close(IOException("Pipeline websocket failed: ${t.message}", t))
            }

            private fun closeCompleteOrFail() {
                if (sawTerminalEvent.get()) {
                    close()
                } else {
                    close(IOException("Pipeline connection closed before the analysis finished"))
                }
            }
        }

        val webSocket = client.newWebSocket(Request.Builder().url(serverUrl).build(), listener)
        awaitClose { webSocket.cancel() }
    }.onEach { event ->
        if (event is PipelineEvent.ResponderCompleted) {
            analysisRepository.saveAnalysis(event.analysis)
        }
    }

    private companion object {
        const val NORMAL_CLOSURE = 1000
    }
}
