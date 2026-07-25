import json
import logging
from typing import Dict, Any, List
from pydantic import BaseModel, Field

from .state import OrchestratorState, check_escalation_trigger, validate_iteration_count
from .prompts import PLANNER_SYSTEM_PROMPT, RETRIEVER_SYSTEM_PROMPT
from src.utils.llm import call_llm_structured
from src.tools.requisitos import RequisitosSensor
from src.tools.reglas import evaluar_familia, ResultadoEvaluacion
from src.tools.confianza import calcular_confianza

logger = logging.getLogger(__name__)


# ---------------------------------------------------------------------------
# Pydantic schemas for LLM-structured calls
# ---------------------------------------------------------------------------

class PlannerOutput(BaseModel):
    structured_requirements: dict = Field(default_factory=dict)
    missing_fields: list = Field(default_factory=list)
    extraction_confidence: float = 0.5
    planner_notes: list = Field(default_factory=list)


class ClarifierOutput(BaseModel):
    questions: list = Field(default_factory=list)


class EvidenceExtractionOutput(BaseModel):
    candidates: list = Field(default_factory=list)
    sources: list = Field(default_factory=list)
    extraction_notes: list = Field(default_factory=list)


class ResponderOutput(BaseModel):
    status: str = "needs_clarification"
    message: str = ""
    shortlist: list = Field(default_factory=list)
    discards: list = Field(default_factory=list)
    citations: list = Field(default_factory=list)
    confidence: dict = Field(default_factory=lambda: {"score": 0, "rationale": ""})


# ---------------------------------------------------------------------------
# Schema bridge helpers
# ---------------------------------------------------------------------------

_AMBIENTE_MAP = {
    "dusty": "polvo",
    "outdoor": "exterior",
    "indoor": "polvo",
    "wet": "exterior",
    "vibration": "vibracion",
    "high_temperature": "exterior",
    "clean": "polvo",
    "dust": "polvo",
    "exterior": "exterior",
    "polvo": "polvo",
    "vibracion": "vibracion",
}

_AMBIENTE_REVERSE = {v: k for k, v in _AMBIENTE_MAP.items()}


def _to_requisitos_sensor(structured: dict) -> RequisitosSensor:
    tipo = structured.get("sensor_type") or structured.get("tipo_objeto")
    rmin = structured.get("range_min_m")
    rmax = structured.get("range_max_m")
    distancia_mm = None
    if rmin is not None and rmax is not None:
        distancia_mm = ((rmin or 0) + (rmax or 0)) / 2 * 1000
    elif rmax is not None:
        distancia_mm = rmax * 1000
    elif rmin is not None:
        distancia_mm = rmin * 1000

    raw_ambiente = structured.get("environment") or structured.get("ambiente") or []
    if isinstance(raw_ambiente, str):
        raw_ambiente = [raw_ambiente]
    ambiente_normalized = [_AMBIENTE_MAP.get(a.lower(), a.lower()) for a in raw_ambiente if a]
    ambiente_normalized = [a for a in ambiente_normalized if a in ("polvo", "vibracion", "exterior")]

    return RequisitosSensor(
        tipo_objeto=tipo,
        distancia_mm=distancia_mm,
        ambiente=ambiente_normalized,
        material_superficie=structured.get("material_surface") or structured.get("material_superficie"),
        montaje=structured.get("mounting") or structured.get("montaje"),
    )


def _candidate_to_reglas_candidato(c: dict) -> dict:
    rango = c.get("rango_distancia_mm")
    if rango is None:
        rango_raw = c.get("range") or c.get("rango", "")
        rango = _parse_range_mm(rango_raw) if isinstance(rango_raw, str) else None

    ambientes = c.get("ambientes_soportados") or c.get("environment") or []
    if isinstance(ambientes, str):
        ambientes = [ambientes]
    ambientes_normalized = []
    for a in ambientes:
        a_lower = a.lower()
        ambientes_normalized.append(_AMBIENTE_MAP.get(a_lower, a_lower))

    return {
        "familia": c.get("model", c.get("familia", "unknown")),
        "fuente": c.get("datasheet_reference", c.get("fuente", "")),
        "rango_distancia_mm": rango,
        "ambientes_soportados": ambientes_normalized,
        "materiales_soportados": c.get("materiales_soportados") or ["opaco", "translucido", "metalico"],
        "montajes": c.get("montajes") or ["fijo", "movil"],
    }


def _parse_range_mm(raw: str):
    import re
    nums = re.findall(r"[\d.]+", raw.replace(",", "."))
    if len(nums) >= 2:
        try:
            return [float(nums[0]) * 1000, float(nums[1]) * 1000]
        except ValueError:
            return None
    return None


def _confianza_a_100(confianza_0_1: float) -> int:
    return min(100, max(0, round(confianza_0_1 * 100)))


# ---------------------------------------------------------------------------
# ChromaDB helper
# ---------------------------------------------------------------------------

CHROMA_COLLECTION = "sick_manuals"


def _query_chroma(query_text: str, top_k: int = 10):
    try:
        from src.utils.config import CHROMADB_API_KEY, CHROMADB_TENANT, CHROMADB_DATABASE
        if not CHROMADB_API_KEY or CHROMADB_API_KEY.startswith("your_"):
            logger.warning("ChromaDB credentials not configured")
            return None
        import chromadb
        chroma_client = chromadb.CloudClient(
            api_key=CHROMADB_API_KEY,
            tenant=CHROMADB_TENANT,
            database=CHROMADB_DATABASE,
        )
        collection = chroma_client.get_collection(name=CHROMA_COLLECTION)
        results = collection.query(
            query_texts=[query_text],
            n_results=top_k,
        )
        logger.info(f"[CHROMA] Retrieved {len(results.get('ids', [[]])[0])} chunks")
        return results
    except Exception as e:
        logger.error(f"[CHROMA] Query failed: {e}")
        return None


def _chroma_results_to_evidence(chroma_results) -> list[dict]:
    if not chroma_results:
        return []
    evidence = []
    ids = chroma_results.get("ids", [[]])[0]
    distances = chroma_results.get("distances", [[]])[0]
    documents = chroma_results.get("documents", [[]])[0]
    metadatas = chroma_results.get("metadatas", [[]])[0]

    for i in range(len(ids)):
        score = 1.0 - distances[i] if i < len(distances) else 0.5
        meta = metadatas[i] if i < len(metadatas) else {}
        evidence.append({
            "id": ids[i],
            "text": documents[i] if i < len(documents) else "",
            "score": max(0.0, min(1.0, score)),
            "source_url": meta.get("source_url", ""),
            "model": meta.get("model", ""),
            "title": meta.get("title", ""),
            "datasheet_reference": meta.get("pdf_url", meta.get("source_url", ids[i])),
        })
    return evidence


# ---------------------------------------------------------------------------
# NODE 1: PLANNER
# ---------------------------------------------------------------------------

async def planner_node(state: OrchestratorState) -> Dict[str, Any]:
    user_query = state.get("user_query", "").strip()
    if not user_query:
        return {"planner_notes": ["ERROR: Empty user query"]}

    try:
        user_msg = f"Query del usuario:\n{user_query}\n\nExtrae los requisitos estructurados según las instrucciones."
        parsed = call_llm_structured(
            PLANNER_SYSTEM_PROMPT, user_msg, PlannerOutput,
            temperature=0.1, max_tokens=3072, retries=2,
        )
        logger.info(f"[PLANNER] Missing: {parsed.missing_fields}, confidence: {parsed.extraction_confidence}")
        return {
            "structured_requirements": parsed.structured_requirements,
            "missing_fields": parsed.missing_fields,
            "planner_notes": [
                f"Query: {user_query[:80]}...",
                f"Extracción: {len(parsed.structured_requirements)} campos, {len(parsed.missing_fields)} faltantes",
                f"Confianza: {parsed.extraction_confidence}",
                *parsed.planner_notes,
            ],
        }
    except Exception as e:
        logger.error(f"[PLANNER] Error: {e}", exc_info=True)
        return {"planner_notes": [f"ERROR en planner: {str(e)}"]}


# ---------------------------------------------------------------------------
# NODE 2: CLARIFIER
# ---------------------------------------------------------------------------

async def clarifier_node(state: OrchestratorState) -> Dict[str, Any]:
    missing_fields = state.get("missing_fields", [])
    if not missing_fields:
        return {"planner_notes": ["CLARIFIER: No missing fields to clarify"]}

    iteration_count = state.get("iteration_count", 0)
    if not validate_iteration_count(state):
        return {"planner_notes": [f"ERROR: Max iterations reached ({iteration_count})"]}

    try:
        structured_req = state.get("structured_requirements", {})
        system = (
            "Eres un asistente que genera preguntas de aclaración técnicas. "
            "Genera preguntas específicas para los campos faltantes.\n"
            "Responde SOLO con JSON: {\"questions\": [\"...\", \"...\"]}"
        )
        user_msg = (
            f"Campos faltantes: {missing_fields}\n\n"
            f"Requisitos actuales:\n{json.dumps(structured_req, indent=2, ensure_ascii=False)}"
        )
        parsed = call_llm_structured(
            system, user_msg, ClarifierOutput,
            temperature=0.3, max_tokens=2048, retries=1,
        )
        new_count = iteration_count + 1
        logger.info(f"[CLARIFIER] {len(parsed.questions)} preguntas (iter {new_count})")
        return {
            "clarification_questions": parsed.questions,
            "iteration_count": new_count,
            "planner_notes": [f"Iteración {new_count}: {len(parsed.questions)} preguntas"],
        }
    except Exception as e:
        logger.error(f"[CLARIFIER] Error: {e}", exc_info=True)
        return {"planner_notes": [f"ERROR en clarifier: {str(e)}"]}


# ---------------------------------------------------------------------------
# NODE 3: RETRIEVER / EVIDENCE BUILDER
# ---------------------------------------------------------------------------

async def retriever_node(state: OrchestratorState) -> Dict[str, Any]:
    structured_requirements = state.get("structured_requirements", {})
    if not structured_requirements:
        return {"candidates": [], "planner_notes": ["RETRIEVER: No requirements to query"]}

    try:
        query_text = (
            f"{structured_requirements.get('sensor_type', '')} "
            f"range {structured_requirements.get('range_min_m', '')}-"
            f"{structured_requirements.get('range_max_m', '')}m "
            f"precision {structured_requirements.get('precision', '')} "
            f"environment {structured_requirements.get('environment', [])}"
        ).strip()

        chroma_results = _query_chroma(query_text, top_k=10)
        evidence_fragments = _chroma_results_to_evidence(chroma_results) if chroma_results else []

        if evidence_fragments:
            models_found = list({e["model"] for e in evidence_fragments if e["model"]})
            logger.info(f"[RETRIEVER] {len(evidence_fragments)} fragments, {len(models_found)} models: {models_found}")

            extraction_prompt = (
                "Eres un extractor de datos estructurados de sensores SICK. "
                "A partir de fragmentos REALES de manuales técnicos, extrae "
                "información estructurada de cada modelo de sensor.\n\n"
                "Para cada modelo, genera un candidato con:\n"
                "- model: nombre del modelo\n"
                "- type: tipo de sensor\n"
                "- range: rango de operación (formato texto, ej '0.3-8m')\n"
                "- precision: precisión (ej '±50mm @ 8m')\n"
                "- ip_rating: si se menciona (ej IP67)\n"
                "- communication_interface: si se menciona\n"
                "- rango_distancia_mm: [min, max] en milímetros (inferir del texto)\n"
                "- ambientes_soportados: lista de condiciones ambientales\n"
                "- materiales_soportados: lista de materiales detectables\n"
                "- datasheet_reference: ID del datasheet\n"
                "- retrieval_score: score de relevancia (0-1)\n"
                "- source_section: fragmento textual de evidencia\n\n"
                "EXTRÍE SOLO lo que el texto soporte. No inventes specs.\n"
                "Responde JSON: {\"candidates\": [...], \"sources\": [...], \"extraction_notes\": [...]}"
            )

            evidence_summary = json.dumps(
                [{"model": e["model"], "text": e["text"][:500], "score": e["score"]}
                 for e in evidence_fragments[:5]],
                indent=2, ensure_ascii=False,
            )
            user_msg = f"Requisitos:\n{json.dumps(structured_requirements, indent=2, ensure_ascii=False)}\n\nEvidencia de Chroma:\n{evidence_summary}"
            parsed = call_llm_structured(
                extraction_prompt, user_msg, EvidenceExtractionOutput,
                temperature=0.1, max_tokens=8192, retries=2,
            )

            for c in parsed.candidates:
                c["retrieval_score"] = c.get("retrieval_score", 0.5)

            retrieval_context = {
                "total_candidates_found": len(parsed.candidates),
                "sources": parsed.sources,
                "evidence_fragments_count": len(evidence_fragments),
                "retrieval_notes": parsed.extraction_notes,
            }
            logger.info(f"[RETRIEVER] {len(parsed.candidates)} candidates from Chroma evidence")
            return {
                "candidates": parsed.candidates,
                "retrieval_context": retrieval_context,
                "planner_notes": [
                    f"Chroma: {len(evidence_fragments)} fragmentos, {len(parsed.candidates)} candidatos extraídos",
                    *(parsed.extraction_notes[:3]),
                ],
            }
        else:
            # Fallback: Chroma unavailable or empty → use LLM retrieval prompt
            logger.warning("[RETRIEVER] Chroma unavailable, using LLM retrieval fallback")
            user_msg = (
                f"Requisitos del usuario:\n{json.dumps(structured_requirements, indent=2, ensure_ascii=False)}\n\n"
                f"Busca en el catálogo SICK los sensores que coincidan."
            )
            parsed = call_llm_structured(
                RETRIEVER_SYSTEM_PROMPT, user_msg, EvidenceExtractionOutput,
                temperature=0.2, max_tokens=8192, retries=2,
            )
            retrieval_context = {
                "total_candidates_found": len(parsed.candidates),
                "sources": parsed.sources,
                "retrieval_notes": [*parsed.extraction_notes, "⚠️ Sin Chroma DB — datos simulados por LLM"],
            }
            logger.info(f"[RETRIEVER] {len(parsed.candidates)} candidates (LLM fallback)")
            return {
                "candidates": parsed.candidates,
                "retrieval_context": retrieval_context,
                "planner_notes": [
                    f"Recuperados {len(parsed.candidates)} candidatos (fallback LLM)",
                    "⚠️ Chroma DB no disponible, candidate data may be synthetic",
                    *(parsed.extraction_notes[:2]),
                ],
            }
    except Exception as e:
        logger.error(f"[RETRIEVER] Error: {e}", exc_info=True)
        return {"candidates": [], "planner_notes": [f"ERROR en retriever: {str(e)}"]}


# ---------------------------------------------------------------------------
# NODE 4: RULE VALIDATOR
# ---------------------------------------------------------------------------

async def validator_node(state: OrchestratorState) -> Dict[str, Any]:
    candidates = state.get("candidates", [])
    if not candidates:
        return {"validation_results": {}, "viable_candidates": [], "planner_notes": ["VALIDATOR: No candidates"]}

    structured_requirements = state.get("structured_requirements", {})
    try:
        requisitos = _to_requisitos_sensor(structured_requirements)
        logger.info(f"[VALIDATOR] RequisitosSensor: tipo={requisitos.tipo_objeto}, "
                    f"distancia={requisitos.distancia_mm}mm, "
                    f"ambiente={requisitos.ambiente}, "
                    f"faltantes={requisitos.campos_faltantes}")

        validation_results = {}
        viable_list = []
        discarded_list = []
        evaluaciones: list[ResultadoEvaluacion] = []

        for c in candidates:
            model = c.get("model", "unknown")
            candidato_data = _candidate_to_reglas_candidato(c)
            resultado = evaluar_familia(requisitos, candidato_data)
            evaluaciones.append(resultado)

            has_pass = any("NO soportado" not in r and "FUERA del rango" not in r for r in resultado.razones)

            passed_criteria = []
            failed_criteria = []
            for r in resultado.razones:
                if "NO soportado" in r or "FUERA del rango" in r:
                    failed_criteria.append(r)
                else:
                    passed_criteria.append(r)

            validation_results[model] = {
                "criteria": {
                    "range_coverage": {
                        "pass": resultado.veredicto != "descartada" or not any("distancia_mm" in r and "FUERA" in r for r in resultado.razones),
                        "detail": next((r for r in resultado.razones if "distancia_mm" in r), "no evaluado"),
                    },
                    "environment_fit": {
                        "pass": resultado.veredicto != "descartada" or not any("ambiente" in r and "NO soportado" in r for r in resultado.razones),
                        "detail": next((r for r in resultado.razones if "ambiente" in r), "no evaluado"),
                    },
                    "material_compatibility": {
                        "pass": resultado.veredicto != "descartada" or not any("material" in r and "NO soportado" in r for r in resultado.razones),
                        "detail": next((r for r in resultado.razones if "material" in r), "no evaluado"),
                    },
                },
                "overall_viability": resultado.veredicto == "viable",
                "viability_reason": resultado.veredicto,
                "veredicto": resultado.veredicto,
                "reglas_pasadas": resultado.reglas_pasadas,
                "reglas_falladas": resultado.reglas_falladas,
                "reglas_no_evaluadas": resultado.reglas_no_evaluadas,
                "razones": resultado.razones,
                "no_evaluables": resultado.reglas_no_evaluables,
                "claves_desconocidas": resultado.claves_desconocidas,
            }

            if resultado.veredicto == "viable":
                viable_list.append(c)
            else:
                discarded_list.append(c)

        logger.info(f"[VALIDATOR] {len(viable_list)} viable, {len(discarded_list)} discarded")

        return {
            "validation_results": validation_results,
            "viable_candidates": viable_list,
            "evaluaciones": [{
                "veredicto": ev.veredicto,
                "reglas_pasadas": ev.reglas_pasadas,
                "reglas_falladas": ev.reglas_falladas,
                "reglas_no_evaluadas": ev.reglas_no_evaluadas,
            } for ev in evaluaciones],
            "planner_notes": [
                f"Validados {len(candidates)} candidatos con evaluar_familia()",
                f"Viables: {len(viable_list)}, Descartados: {len(discarded_list)}",
            ],
        }
    except Exception as e:
        logger.error(f"[VALIDATOR] Error: {e}", exc_info=True)
        return {"validation_results": {}, "viable_candidates": [],
                "planner_notes": [f"ERROR en validator: {str(e)}"]}


# ---------------------------------------------------------------------------
# NODE 5: EVALUATOR
# ---------------------------------------------------------------------------

async def evaluator_node(state: OrchestratorState) -> Dict[str, Any]:
    viable_candidates = state.get("viable_candidates", [])
    missing_fields = state.get("missing_fields", [])
    iteration_count = state.get("iteration_count", 0)

    try:
        escalation_check = check_escalation_trigger(state)
        if escalation_check:
            return {
                "confidence": 0, "next_step": "responder",
                "escalation_reason": escalation_check,
                "planner_notes": [f"Escalado por: {escalation_check}"],
            }

        structured_req = state.get("structured_requirements", {})
        requisitos = _to_requisitos_sensor(structured_req)

        evidencia_raw = state.get("retrieval_context", {}).get("evidence_fragments_count", 0)
        evidencia = [{"fuente": "chroma", "texto": "evidence", "score": 0.8}] if evidencia_raw > 0 else []

        evaluaciones_raw = state.get("evaluaciones", [])
        evaluaciones: list[ResultadoEvaluacion] = [
            ResultadoEvaluacion(
                veredicto=e.get("veredicto", "ambigua"),
                razones=[],
                reglas_no_evaluables=[],
                reglas_pasadas=e.get("reglas_pasadas", 0),
                reglas_falladas=e.get("reglas_falladas", 0),
                reglas_no_evaluadas=e.get("reglas_no_evaluadas", 0),
            )
            for e in evaluaciones_raw
        ] if evaluaciones_raw else [
            ResultadoEvaluacion(
                veredicto="viable" if viable_candidates else "descartada",
                razones=[],
                reglas_no_evaluables=[],
                reglas_pasadas=len(viable_candidates) * 3,
                reglas_falladas=0,
                reglas_no_evaluadas=0,
            )
        ]

        confianza_result = calcular_confianza(
            requisitos=requisitos,
            evidencia=evidencia,
            evaluaciones=evaluaciones,
        )

        confidence_100 = _confianza_a_100(confianza_result.confianza)
        needs_human_review = confianza_result.debe_escalar and iteration_count >= 3

        if needs_human_review:
            next_step = "responder"
            escalation_reason = f"confianza_baja_{confianza_result.confianza:.2f}"
        elif confianza_result.confianza >= 0.6:
            next_step = "responder"
            escalation_reason = None
        elif iteration_count < 3 and missing_fields:
            next_step = "clarifier"
            escalation_reason = None
        else:
            next_step = "responder"
            escalation_reason = f"confianza_insuficiente_{confianza_result.confianza:.2f}"

        logger.info(f"[EVALUATOR] Confianza={confianza_result.confianza:.2f} ({confidence_100}/100), "
                    f"Next={next_step}, Escalar={confianza_result.debe_escalar}")

        return {
            "confidence": confidence_100,
            "evaluator_reasoning": (
                f"completitud={confianza_result.desglose['completitud']:.2f}, "
                f"calidad_evidencia={confianza_result.desglose['calidad_evidencia']:.2f}, "
                f"certeza_reglas={confianza_result.desglose['certeza_reglas']:.2f}"
            ),
            "next_step": next_step,
            "escalation_reason": escalation_reason,
            "needs_human_review": needs_human_review,
            "confianza_desglose": confianza_result.desglose,
            "planner_notes": [
                f"Confianza: {confianza_result.confianza:.2f} ({confidence_100}/100)",
                f"Desglose: {confianza_result.desglose}",
                f"Siguiente: {next_step}",
                f"Escalar: {needs_human_review}",
            ],
        }
    except Exception as e:
        logger.error(f"[EVALUATOR] Error: {e}", exc_info=True)
        return {
            "confidence": 0, "next_step": "responder",
            "escalation_reason": "evaluation_error",
            "planner_notes": [f"ERROR en evaluator: {str(e)}"],
        }


# ---------------------------------------------------------------------------
# NODE 6: FINAL RESPONSE BUILDER
# ---------------------------------------------------------------------------

async def responder_node(state: OrchestratorState) -> Dict[str, Any]:
    viable_candidates = state.get("viable_candidates", [])
    candidates = state.get("candidates", [])
    validation_results = state.get("validation_results", {})
    confidence_score = state.get("confidence", 0)
    next_step = state.get("next_step", "unknown")
    escalation_reason = state.get("escalation_reason")

    try:
        if escalation_reason:
            status = "escalated"
        elif confidence_score >= 60 and viable_candidates:
            status = "recommended"
        else:
            status = "needs_clarification"

        shortlist = []
        for c in viable_candidates[:5]:
            model = c.get("model", "unknown")
            vr = validation_results.get(model, {})
            shortlist.append({
                "model": model,
                "type": c.get("type", ""),
                "range": c.get("range", ""),
                "precision": c.get("precision", ""),
                "reasoning": vr.get("viability_reason", "Cumple criterios técnicos"),
                "veredicto": vr.get("veredicto", "viable"),
                "reglas_pasadas": vr.get("reglas_pasadas", 0),
                "reglas_falladas": vr.get("reglas_falladas", 0),
            })

        discards = []
        for c in candidates:
            model = c.get("model", "unknown")
            if model not in [s["model"] for s in shortlist]:
                vr = validation_results.get(model, {})
                razones = vr.get("razones", [])
                no_eval = vr.get("no_evaluables", [])
                reasons = [r for r in razones if "NO soportado" in r or "FUERA" in r]
                if not reasons:
                    reasons = no_eval or [vr.get("viability_reason", "No cumple criterios")]
                discards.append({
                    "model": model,
                    "reason": "; ".join(reasons),
                    "veredicto": vr.get("veredicto", "descartada"),
                })

        sources = []
        for c in candidates:
            model = c.get("model", "unknown")
            sources.append({
                "model": model,
                "datasheet": c.get("datasheet_reference", f"chroma:{model}"),
                "section": c.get("source_section", ""),
            })

        message = ""
        if status == "recommended":
            message = (
                f"Según el análisis técnico, {'el' if len(shortlist) == 1 else 'los'} "
                f"siguiente{'s' if len(shortlist) != 1 else ''} sensor{'es' if len(shortlist) == 1 else 'es'} "
                f"de SICK {'es' if len(shortlist) == 1 else 'son'} compatible{'s' if len(shortlist) != 1 else ''}"
                f" con tus requisitos."
            )
        elif status == "escalated":
            message = (
                f"No fue posible generar una recomendación con suficiente confianza "
                f"({confidence_score}/100). Se requiere revisión humana. "
                f"Motivo: {escalation_reason}"
            )
        else:
            message = "Faltan datos para completar la recomendación. Revisa los campos requeridos."

        final_answer = {
            "status": status,
            "message": message,
            "shortlist": shortlist,
            "discards": discards,
            "reasons": {
                "methodology": "ChromaDB RAG + evaluar_familia() determinístico + calcular_confianza()",
                "criteria_applied": ["rango_distancia_mm", "ambientes_soportados", "materiales_soportados"],
                "confidence_calculation": "Ponderada: completitud + calidad_evidencia + certeza_reglas",
            },
            "sources": sources,
            "confidence": {
                "score": confidence_score,
                "rationale": state.get("evaluator_reasoning", ""),
            },
            "escalation": escalation_reason,
        }

        logger.info(f"[RESPONDER] Status: {status}, Shortlist: {len(shortlist)}, Discards: {len(discards)}")
        return {
            "final_answer": final_answer,
            "status": status,
            "planner_notes": [
                f"Respuesta: {status}",
                f"Shortlist: {len(shortlist)} items",
                f"Método: ChromaDB + reglas determinísticas + confianza",
            ],
        }
    except Exception as e:
        logger.error(f"[RESPONDER] Error: {e}", exc_info=True)
        return {
            "final_answer": {
                "status": "escalated",
                "message": f"Error generando respuesta: {str(e)}",
                "shortlist": [], "discards": [], "sources": [],
                "reasons": {}, "confidence": {"score": 0, "rationale": "Error"},
                "escalation": str(e),
            },
            "status": "escalated",
            "planner_notes": [f"ERROR en responder: {str(e)}"],
        }
