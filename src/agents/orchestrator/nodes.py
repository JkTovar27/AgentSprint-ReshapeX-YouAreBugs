import json
import logging
from typing import Dict, Any

from .state import OrchestratorState, check_escalation_trigger, validate_iteration_count
from .prompts import PLANNER_SYSTEM_PROMPT, RETRIEVER_SYSTEM_PROMPT, EVALUATOR_SYSTEM_PROMPT
from src.utils.llm import call_llm, call_llm_structured, extract_json
from pydantic import BaseModel, Field

logger = logging.getLogger(__name__)


async def planner_node(state: OrchestratorState) -> Dict[str, Any]:
    user_query = state.get("user_query", "").strip()
    if not user_query:
        return {"planner_notes": ["ERROR: Empty user query"]}

    try:
        user_msg = (
            f"Query del usuario:\n{user_query}\n\n"
            f"Extrae los requisitos estructurados según las instrucciones."
        )
        raw = call_llm(PLANNER_SYSTEM_PROMPT, user_msg, temperature=0.1, max_tokens=2048)
        parsed = json.loads(extract_json(raw))

        structured_requirements = parsed.get("structured_requirements", {})
        missing_fields = parsed.get("missing_fields", [])
        extraction_confidence = parsed.get("extraction_confidence", 0.5)
        notes = parsed.get("planner_notes", [])

        logger.info(f"[PLANNER] Missing: {missing_fields}, confidence: {extraction_confidence}")

        return {
            "structured_requirements": structured_requirements,
            "missing_fields": missing_fields,
            "planner_notes": [
                f"Query: {user_query[:80]}...",
                f"Extracción: {len(structured_requirements)} campos, {len(missing_fields)} faltantes",
                f"Confianza extracción: {extraction_confidence}",
                *notes,
            ],
        }
    except Exception as e:
        logger.error(f"[PLANNER] Error: {e}", exc_info=True)
        return {"planner_notes": [f"ERROR en planner: {str(e)}"]}


async def clarifier_node(state: OrchestratorState) -> Dict[str, Any]:
    missing_fields = state.get("missing_fields", [])
    if not missing_fields:
        return {"planner_notes": ["CLARIFIER: No missing fields to clarify"]}

    iteration_count = state.get("iteration_count", 0)
    if not validate_iteration_count(state):
        logger.error(f"[CLARIFIER] Iteration limit exceeded ({iteration_count} > 3)")
        return {
            "planner_notes": [f"ERROR: Max iterations reached ({iteration_count})"],
        }

    try:
        structured_req = state.get("structured_requirements", {})
        system = (
            "Eres un asistente que genera preguntas de aclaración técnicas y concretas. "
            "Dada una lista de campos faltantes y los requisitos actuales, genera "
            "preguntas específicas que ayuden al usuario a completar la información.\n\n"
            "REGLAS:\n"
            "- Una pregunta por campo faltante\n"
            "- Pregunta con ejemplos concretos\n"
            "- No más de 5 preguntas\n"
            "- Responde SOLO con un JSON: {\"questions\": [\"...\", \"...\"]}"
        )
        user_msg = (
            f"Campos faltantes: {missing_fields}\n\n"
            f"Requisitos actuales:\n{json.dumps(structured_req, indent=2, ensure_ascii=False)}"
        )
        raw = call_llm(system, user_msg, temperature=0.3, max_tokens=1024)
        parsed = json.loads(extract_json(raw))
        questions = parsed.get("questions", [])

        new_iteration_count = iteration_count + 1
        logger.info(f"[CLARIFIER] {len(questions)} preguntas generadas (iter {new_iteration_count})")

        return {
            "clarification_questions": questions,
            "iteration_count": new_iteration_count,
            "planner_notes": [
                f"Iteración {new_iteration_count}: {len(questions)} preguntas para {missing_fields}",
            ],
        }
    except Exception as e:
        logger.error(f"[CLARIFIER] Error: {e}", exc_info=True)
        return {"planner_notes": [f"ERROR en clarifier: {str(e)}"]}


async def retriever_node(state: OrchestratorState) -> Dict[str, Any]:
    structured_requirements = state.get("structured_requirements", {})
    if not structured_requirements:
        return {"candidates": [], "planner_notes": ["RETRIEVER: No requirements to query"]}

    try:
        user_msg = (
            f"Requisitos del usuario:\n{json.dumps(structured_requirements, indent=2, ensure_ascii=False)}\n\n"
            f"Busca en el catálogo SICK los sensores que coincidan."
        )
        raw = call_llm(RETRIEVER_SYSTEM_PROMPT, user_msg, temperature=0.2, max_tokens=3072)
        parsed = json.loads(extract_json(raw))

        candidates = parsed.get("candidates", [])
        sources = parsed.get("sources", [])
        retrieval_notes = parsed.get("retrieval_notes", [])

        retrieval_context = {
            "total_candidates_found": len(candidates),
            "sources": sources,
            "retrieval_notes": retrieval_notes,
        }

        logger.info(f"[RETRIEVER] {len(candidates)} candidates retrieved")

        return {
            "candidates": candidates,
            "retrieval_context": retrieval_context,
            "planner_notes": [
                f"Recuperados {len(candidates)} candidatos",
                *retrieval_notes[:3],
            ],
        }
    except Exception as e:
        logger.error(f"[RETRIEVER] Error: {e}", exc_info=True)
        return {"candidates": [], "planner_notes": [f"ERROR en retriever: {str(e)}"]}


async def validator_node(state: OrchestratorState) -> Dict[str, Any]:
    candidates = state.get("candidates", [])
    if not candidates:
        return {"validation_results": {}, "planner_notes": ["VALIDATOR: No candidates received"]}

    structured_requirements = state.get("structured_requirements", {})

    class ValidatorOutput(BaseModel):
        validation_results: Dict[str, Any] = Field(default_factory=dict)
        viable_candidates: list = Field(default_factory=list)
        discarded_candidates: list = Field(default_factory=list)
        validator_notes: list = Field(default_factory=list)

    try:
        system = (
            "Eres un validador técnico de sensores SICK. "
            "Evalúa cada candidato contra los requisitos:\n"
            "- range_coverage: cubre el rango solicitado?\n"
            "- precision_match: cumple la precisión?\n"
            "- environment_fit: soporta las condiciones ambientales?"
        )
        user_msg = (
            f"Requisitos:\n{json.dumps(structured_requirements, indent=2, ensure_ascii=False)}\n\n"
            f"Candidatos:\n{json.dumps(candidates, indent=2, ensure_ascii=False)}"
        )
        parsed = call_llm_structured(system, user_msg, ValidatorOutput, temperature=0.1, max_tokens=3072, retries=2)

        validation_results = parsed.validation_results or {}
        viable_candidates = parsed.viable_candidates or []
        discarded_candidates = parsed.discarded_candidates or []
        validator_notes = parsed.validator_notes or []

        viable_models = [
            c for c in candidates if c.get("model") in viable_candidates
        ]

        logger.info(f"[VALIDATOR] {len(viable_models)} viable, {len(discarded_candidates)} discarded")

        return {
            "validation_results": validation_results,
            "viable_candidates": viable_models,
            "planner_notes": [
                f"Validados {len(candidates)} candidatos",
                f"Viables: {len(viable_models)}, Descartados: {len(discarded_candidates)}",
                *validator_notes,
            ],
        }
    except Exception as e:
        logger.error(f"[VALIDATOR] Error: {e}", exc_info=True)
        return {"validation_results": {}, "viable_candidates": [], "planner_notes": [f"ERROR en validator: {str(e)}"]}


async def evaluator_node(state: OrchestratorState) -> Dict[str, Any]:
    viable_candidates = state.get("viable_candidates", [])
    missing_fields = state.get("missing_fields", [])
    iteration_count = state.get("iteration_count", 0)

    try:
        escalation_check = check_escalation_trigger(state)
        if escalation_check:
            return {
                "confidence": 0,
                "evaluator_reasoning": f"Escalation triggered: {escalation_check}",
                "next_step": "responder",
                "escalation_reason": escalation_check,
                "planner_notes": [f"Escalado por: {escalation_check}"],
            }

        system = (
            "Eres el evaluador de un sistema de recomendación de sensores SICK. "
            "Revisa el estado actual y decide el siguiente paso.\n\n"
            "Responde SOLO con JSON:\n"
            "{\n"
            '  "confidence": 0-100,\n'
            '  "reasoning": "...",\n'
            '  "needs_human_review": false,\n'
            '  "evaluator_notes": ["..."]\n'
            "}"
        )
        user_msg = (
            f"Candidatos viables: {len(viable_candidates)}\n"
            f"Campos faltantes: {missing_fields}\n"
            f"Iteraciones: {iteration_count}/3\n"
            f"Confianza actual: {state.get('confidence', 0)}"
        )
        raw = call_llm(system, user_msg, temperature=0.1, max_tokens=1024)
        parsed = json.loads(extract_json(raw))

        confidence = parsed.get("confidence", 50)
        needs_human_review = parsed.get("needs_human_review", False)
        reasoning = parsed.get("reasoning", "")
        notes = parsed.get("evaluator_notes", [])

        if needs_human_review:
            next_step = "responder"
            escalation_reason = "human_review_requested"
        elif confidence >= 70:
            next_step = "responder"
            escalation_reason = None
        elif iteration_count < 3:
            next_step = "clarifier"
            escalation_reason = None
        else:
            next_step = "responder"
            escalation_reason = "confidence_too_low_after_max_iterations"

        logger.info(f"[EVALUATOR] Confidence: {confidence}, Next: {next_step}")

        return {
            "confidence": confidence,
            "evaluator_reasoning": reasoning,
            "next_step": next_step,
            "escalation_reason": escalation_reason,
            "needs_human_review": needs_human_review,
            "planner_notes": [
                f"Confianza: {confidence}",
                f"Siguiente paso: {next_step}",
                f"Revisión humana: {needs_human_review}",
                *notes,
            ],
        }
    except Exception as e:
        logger.error(f"[EVALUATOR] Error: {e}", exc_info=True)
        return {
            "confidence": 0, "next_step": "responder",
            "escalation_reason": "evaluation_error",
            "planner_notes": [f"ERROR en evaluator: {str(e)}"],
        }


async def responder_node(state: OrchestratorState) -> Dict[str, Any]:
    viable_candidates = state.get("viable_candidates", [])
    candidates = state.get("candidates", [])
    validation_results = state.get("validation_results", {})
    confidence_score = state.get("confidence", 0)
    next_step = state.get("next_step", "unknown")
    escalation_reason = state.get("escalation_reason")

    try:
        if next_step == "responder":
            if escalation_reason:
                status = "escalated"
            elif confidence_score >= 70 and viable_candidates:
                status = "recommended"
            else:
                status = "needs_clarification"
        else:
            status = "needs_clarification"

        system = (
            "Eres el asistente final de un sistema de recomendación SICK. "
            "Genera una respuesta clara para el usuario con la información disponible.\n\n"
            "Responde SOLO con JSON:\n"
            "{\n"
            '  "status": "recommended|escalated|needs_clarification",\n'
            '  "message": "Mensaje para el usuario en español",\n'
            '  "shortlist": [{"model": "...", "reason": "..."}],\n'
            '  "discards": [{"model": "...", "reason": "..."}],\n'
            '  "citations": [{"model": "...", "datasheet": "..."}],\n'
            '  "confidence": {"score": 0, "rationale": "..."}\n'
            "}"
        )

        discards = []
        for c in candidates:
            if c.get("model") not in [v.get("model") for v in viable_candidates]:
                failed = [
                    k for k, v in validation_results.get(c.get("model", ""), {}).get("criteria", {}).items()
                    if not v.get("pass")
                ]
                discards.append({
                    "model": c.get("model", "unknown"),
                    "reason": f"Failed: {failed}" if failed else "Not selected",
                })

        user_msg = (
            f"Estado: {status}\n"
            f"Confianza: {confidence_score}\n"
            f"Viables: {json.dumps(viable_candidates, indent=2, ensure_ascii=False)}\n"
            f"Descartes: {json.dumps(discards, indent=2, ensure_ascii=False)}\n"
            f"Escalado: {escalation_reason}"
        )
        raw = call_llm(system, user_msg, temperature=0.3, max_tokens=3072)
        parsed = json.loads(extract_json(raw))

        final_answer = {
            "status": parsed.get("status", status),
            "message": parsed.get("message", ""),
            "shortlist": parsed.get("shortlist", []),
            "discards": parsed.get("discards", discards),
            "sources": parsed.get("citations", []),
            "confidence": parsed.get("confidence", {"score": confidence_score, "rationale": ""}),
            "escalation": escalation_reason,
        }

        logger.info(f"[RESPONDER] Final answer assembled - Status: {final_answer['status']}")

        return {
            "final_answer": final_answer,
            "status": final_answer["status"],
            "planner_notes": [
                f"Respuesta generada: {final_answer['status']}",
                f"Shortlist: {len(final_answer['shortlist'])} items",
            ],
        }
    except Exception as e:
        logger.error(f"[RESPONDER] Error: {e}", exc_info=True)
        return {
            "final_answer": {
                "status": "escalated", "message": f"Error generando respuesta: {str(e)}",
                "shortlist": [], "discards": [], "sources": [],
                "confidence": {"score": 0, "rationale": "Error"},
                "escalation": f"System error: {str(e)}",
            },
            "status": "escalated",
            "planner_notes": [f"ERROR en responder: {str(e)}"],
        }
