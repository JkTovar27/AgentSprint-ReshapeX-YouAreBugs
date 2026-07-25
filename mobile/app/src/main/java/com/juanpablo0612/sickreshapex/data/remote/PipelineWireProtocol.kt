package com.juanpablo0612.sickreshapex.data.remote

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.juanpablo0612.sickreshapex.domain.model.AgentStage
import com.juanpablo0612.sickreshapex.domain.model.Analysis
import com.juanpablo0612.sickreshapex.domain.model.AlternativeOption
import com.juanpablo0612.sickreshapex.domain.model.ApplicationRequirements
import com.juanpablo0612.sickreshapex.domain.model.CandidateVerdict
import com.juanpablo0612.sickreshapex.domain.model.PipelineEvent
import com.juanpablo0612.sickreshapex.domain.model.PipelineResult
import com.juanpablo0612.sickreshapex.domain.model.Reason
import com.juanpablo0612.sickreshapex.domain.model.Recommendation
import com.juanpablo0612.sickreshapex.domain.model.ResultConfidence
import com.juanpablo0612.sickreshapex.domain.model.ResultStatus
import com.juanpablo0612.sickreshapex.domain.model.SensorCandidate
import com.juanpablo0612.sickreshapex.domain.model.SourceReference
import com.juanpablo0612.sickreshapex.domain.model.StageDetail

/**
 * Wire protocol of the orchestrator websocket (`/ws/orchestrator` on the FastAPI backend).
 * Every frame is one JSON text message. This file is the single source of truth for the
 * contract on the mobile side.
 *
 * Client -> server, sent once right after the connection opens:
 * ```json
 * {"user_query": "<free-text request>", "session_id": "<uuid>"}
 * ```
 *
 * Server -> client, one event per stage in pipeline order:
 * ```json
 * {"etapa": "intake|clarificacion|retrieval|evaluacion|confianza",
 *  "estado": "iniciado|completado",
 *  "detalle": {...}}
 * ```
 * `detalle` on completion, per stage:
 *  - intake        -> {structured_requirements: {...}, missing_fields: [...]}
 *  - clarificacion -> {questions: [...], iteration_count: N}
 *  - retrieval     -> {total_candidates: N}
 *  - evaluacion    -> {candidatos: N} plus one event per candidate with veredicto viable|descartado
 *  - confianza     -> {score: 0-100, reasoning: "...", needs_human_review: bool}
 *
 * The terminal event is `{"etapa": "resultado", ... "detalle": {status, shortlist, discards,
 * reasons, confidence: {score, rationale}, sources[, questions]}}`. The same object is also
 * served over REST at `GET /result/{session_id}` as a fallback when the socket drops.
 *
 * Unknown event/stage names are ignored so the backend can evolve without breaking clients.
 */
class PipelineFrameParser(private val gson: Gson) {

    fun encodeStartRequest(userQuery: String, sessionId: String): String {
        val frame = JsonObject().apply {
            addProperty("user_query", userQuery)
            addProperty("session_id", sessionId)
        }
        return gson.toJson(frame)
    }

    /**
     * Translates one inbound frame into a domain [PipelineEvent], or null for frames this
     * client does not understand.
     */
    fun parse(text: String): PipelineEvent? {
        val root = JsonParser.parseString(text).takeIf { it.isJsonObject }?.asJsonObject
            ?: return null
        val etapa = root.str("etapa") ?: return null

        if (etapa.equals("resultado", ignoreCase = true)) {
            val detail = root.obj("detalle") ?: root
            return PipelineEvent.FinalResult(parseResult(detail), analysis = null)
        }

        val stage = stageFor(etapa) ?: return null
        return when (root.str("estado")) {
            "iniciado" -> PipelineEvent.StageStarted(stage)
            "completado" -> PipelineEvent.StageCompleted(stage, parseDetail(stage, root.obj("detalle")))
            else -> null
        }
    }

    /** Parses the `resultado` object — the same shape the REST fallback returns. */
    fun parseResult(json: JsonObject): PipelineResult {
        val status = when (json.str("status")?.lowercase()) {
            "needs_clarification" -> ResultStatus.NEEDS_CLARIFICATION
            else -> ResultStatus.RECOMMENDED
        }
        return PipelineResult(
            status = status,
            shortlist = json.arr("shortlist").toCandidates(),
            discards = json.arr("discards").toCandidates(),
            reasons = json.obj("reasons")?.entrySet()
                ?.associate { (key, value) -> key to value.asDisplayString() }
                .orEmpty(),
            confidence = json.obj("confidence")?.let {
                ResultConfidence(
                    score = it.int("score") ?: 0,
                    rationale = it.str("rationale").orEmpty()
                )
            },
            sources = json.arr("sources").toDisplayStrings(),
            questions = json.arr("questions").toDisplayStrings()
        )
    }

    private fun stageFor(etapa: String): AgentStage? = when (etapa.lowercase()) {
        "intake" -> AgentStage.INTAKE
        "clarificacion" -> AgentStage.CLARIFICATION
        "retrieval" -> AgentStage.RETRIEVAL
        "evaluacion" -> AgentStage.EVALUATION
        "confianza" -> AgentStage.CONFIDENCE
        else -> null
    }

    private fun parseDetail(stage: AgentStage, detail: JsonObject?): StageDetail? {
        detail ?: return null
        return when (stage) {
            AgentStage.INTAKE -> StageDetail.Intake(
                structuredRequirements = detail.obj("structured_requirements")?.entrySet()
                    ?.map { (key, value) -> key to value.asDisplayString() }
                    .orEmpty(),
                missingFields = detail.arr("missing_fields").toDisplayStrings()
            )

            AgentStage.CLARIFICATION -> StageDetail.Clarification(
                questions = detail.arr("questions").toDisplayStrings(),
                iterationCount = detail.int("iteration_count") ?: 0
            )

            AgentStage.RETRIEVAL -> StageDetail.Retrieval(
                totalCandidates = detail.int("total_candidates") ?: 0
            )

            AgentStage.EVALUATION -> StageDetail.Evaluation(
                candidateCount = detail.int("candidatos"),
                verdict = detail.str("veredicto")?.let { veredicto ->
                    CandidateVerdict(
                        candidate = detail.firstStringBesides("veredicto").orEmpty(),
                        isViable = veredicto.equals("viable", ignoreCase = true)
                    )
                }
            )

            AgentStage.CONFIDENCE -> StageDetail.Confidence(
                score = detail.int("score") ?: 0,
                reasoning = detail.str("reasoning").orEmpty(),
                needsHumanReview = detail.bool("needs_human_review") ?: false
            )
        }
    }
}

/**
 * Projects a finished [PipelineResult] onto the app's [Analysis] model so the existing
 * results/history screens can render it. Only contract fields are used: the top shortlist
 * entry becomes the recommendation, discards become alternatives (with their `reasons`
 * entry as discard reason), and `confidence.rationale` doubles as the summary text.
 */
fun PipelineResult.toAnalysis(
    userQuery: String,
    sessionId: String,
    intake: StageDetail.Intake?
): Analysis {
    val requirements = intake?.let { detail ->
        val byKey = detail.structuredRequirements.toMap()
        ApplicationRequirements(
            tipo_objeto = byKey["tipo_objeto"].orEmpty(),
            distancia_mm = byKey["distancia_mm"]?.toFloatOrNull()?.toInt() ?: 0,
            ambiente = byKey["ambiente"].orEmpty(),
            material_superficie = byKey["material_superficie"].orEmpty(),
            montaje = byKey["montaje"].orEmpty()
        )
    }

    val discardNames = discards.map { it.name }.toSet()
    val recommendation = shortlist.firstOrNull()?.let { top ->
        Recommendation(
            sensorFamily = top.name,
            executiveSummary = confidence?.rationale.orEmpty(),
            requirements = requirements ?: ApplicationRequirements("", 0, "", "", ""),
            confidenceLevel = (confidence?.score ?: 0) / 100f,
            confidenceExplanation = confidence?.rationale.orEmpty(),
            reasons = reasons
                .filterKeys { it !in discardNames }
                .map { (title, description) -> Reason(title, description) },
            sources = sources.map { source ->
                SourceReference(
                    title = source,
                    url = if (source.startsWith("http")) source else ""
                )
            }
        )
    }

    return Analysis(
        id = sessionId,
        description = userQuery,
        timestamp = System.currentTimeMillis(),
        requirements = requirements,
        recommendation = recommendation,
        alternatives = discards.map {
            AlternativeOption(
                sensorFamily = it.name,
                discardReason = reasons[it.name].orEmpty()
            )
        }
    )
}

/* Lenient JSON accessors: the stream must survive missing/renamed/mistyped fields, so every
   read is null-safe instead of throwing deep inside the websocket listener. */

private fun JsonObject.str(key: String): String? =
    get(key)?.takeIf { it.isJsonPrimitive }?.asString

private fun JsonObject.int(key: String): Int? =
    get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asFloat?.toInt()

private fun JsonObject.bool(key: String): Boolean? =
    get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean

private fun JsonObject.obj(key: String): JsonObject? =
    get(key)?.takeIf { it.isJsonObject }?.asJsonObject

private fun JsonObject.arr(key: String): JsonArray? =
    get(key)?.takeIf { it.isJsonArray }?.asJsonArray

/** First string-valued field other than [excluded] — used to find a candidate's name. */
private fun JsonObject.firstStringBesides(excluded: String): String? =
    entrySet().firstOrNull { (key, value) ->
        key != excluded && value.isJsonPrimitive && value.asJsonPrimitive.isString
    }?.value?.asString

/** Human-readable rendering of an arbitrary JSON value (primitives stay bare). */
private fun JsonElement.asDisplayString(): String = when {
    isJsonPrimitive -> asJsonPrimitive.asString
    else -> toString()
}

private fun JsonArray?.toDisplayStrings(): List<String> =
    this?.map { element ->
        if (element.isJsonObject) {
            val obj = element.asJsonObject
            obj.str("question") ?: obj.str("text") ?: obj.str("title") ?: element.toString()
        } else {
            element.asDisplayString()
        }
    }.orEmpty()

private val CANDIDATE_NAME_KEYS =
    listOf("name", "product_name", "sensor", "sensor_family", "model", "modelo", "nombre", "id")

/** Shortlist/discard entries may be plain strings or objects; keep every field we get. */
private fun JsonArray?.toCandidates(): List<SensorCandidate> =
    this?.map { element ->
        if (element.isJsonObject) {
            val obj = element.asJsonObject
            val nameKey = CANDIDATE_NAME_KEYS.firstOrNull { obj.str(it) != null }
            SensorCandidate(
                name = nameKey?.let { obj.str(it) } ?: element.toString(),
                attributes = obj.entrySet()
                    .filter { (key, _) -> key != nameKey }
                    .map { (key, value) -> key to value.asDisplayString() }
            )
        } else {
            SensorCandidate(name = element.asDisplayString(), attributes = emptyList())
        }
    }.orEmpty()
