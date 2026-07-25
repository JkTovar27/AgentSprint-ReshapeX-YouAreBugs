import json
import logging
from typing import Dict, Any, List
from pydantic import BaseModel, Field

from .state import OrchestratorState, check_escalation_trigger, validate_iteration_count
from .prompts import PLANNER_SYSTEM_PROMPT, RETRIEVER_SYSTEM_PROMPT, EVALUATOR_SYSTEM_PROMPT
from src.utils.llm import call_llm_structured

logger = logging.getLogger(__name__)


class PlannerOutput(BaseModel):
    structured_requirements: dict = Field(default_factory=dict)
    missing_fields: list = Field(default_factory=list)
    extraction_confidence: float = 0.5
    planner_notes: list = Field(default_factory=list)


class ClarifierOutput(BaseModel):
    questions: list = Field(default_factory=list)


class RetrieverOutput(BaseModel):
    candidates: list = Field(default_factory=list)
    sources: list = Field(default_factory=list)
    retrieval_notes: list = Field(default_factory=list)


class ValidatorOutput(BaseModel):
    validation_results: Dict[str, Any] = Field(default_factory=dict)
    viable_candidates: list = Field(default_factory=list)
    discarded_candidates: list = Field(default_factory=list)
    validator_notes: list = Field(default_factory=list)


class EvaluatorOutput(BaseModel):
    confidence: int = 50
    reasoning: str = ""
    needs_human_review: bool = False
    evaluator_notes: list = Field(default_factory=list)


class ResponderOutput(BaseModel):
    status: str = "needs_clarification"
    message: str = ""
    shortlist: list = Field(default_factory=list)
    discards: list = Field(default_factory=list)
    citations: list = Field(default_factory=list)
    confidence: dict = Field(default_factory=lambda: {"score": 0, "rationale": ""})


async def planner_node(state: OrchestratorState) -> Dict[str, Any]:
    user_query = state.get("user_query", "").strip()
    if not user_query:
        return {"planner_notes": ["ERROR: Empty user query"]}

    try:
        user_msg = f"Query del usuario:\n{user_query}\n\nExtrae los requisitos estructurados según las instrucciones."
        parsed = call_llm_structured(PLANNER_SYSTEM_PROMPT, user_msg, PlannerOutput, temperature=0.1, max_tokens=3072, retries=2)
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
        user_msg = f"Campos faltantes: {missing_fields}\n\nRequisitos actuales:\n{json.dumps(structured_req, indent=2, ensure_ascii=False)}"
        parsed = call_llm_structured(system, user_msg, ClarifierOutput, temperature=0.3, max_tokens=2048, retries=1)
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


async def retriever_node(state: OrchestratorState) -> Dict[str, Any]:
    structured_requirements = state.get("structured_requirements", {})
    if not structured_requirements:
        return {"candidates": [], "planner_notes": ["RETRIEVER: No requirements to query"]}

    try:
        user_msg = (
            f"Requisitos del usuario:\n{json.dumps(structured_requirements, indent=2, ensure_ascii=False)}\n\n"
            f"Busca en el catálogo SICK los sensores que coincidan."
        )
        parsed = call_llm_structured(RETRIEVER_SYSTEM_PROMPT, user_msg, RetrieverOutput, temperature=0.2, max_tokens=8192, retries=2)
        retrieval_context = {
            "total_candidates_found": len(parsed.candidates),
            "sources": parsed.sources,
            "retrieval_notes": parsed.retrieval_notes,
        }
        logger.info(f"[RETRIEVER] {len(parsed.candidates)} candidates")
        return {
            "candidates": parsed.candidates,
            "retrieval_context": retrieval_context,
            "planner_notes": [
                f"Recuperados {len(parsed.candidates)} candidatos",
                *parsed.retrieval_notes[:3],
            ],
        }
    except Exception as e:
        logger.error(f"[RETRIEVER] Error: {e}", exc_info=True)
        return {"candidates": [], "planner_notes": [f"ERROR en retriever: {str(e)}"]}


async def validator_node(state: OrchestratorState) -> Dict[str, Any]:
    candidates = state.get("candidates", [])
    if not candidates:
        return {"validation_results": {}, "viable_candidates": [], "planner_notes": ["VALIDATOR: No candidates"]}

    structured_requirements = state.get("structured_requirements", {})
    try:
        system = (
            "Eres un validador técnico de sensores SICK. "
            "Evalúa cada candidato contra los requisitos: range_coverage, precision_match, environment_fit."
        )
        user_msg = (
            f"Requisitos:\n{json.dumps(structured_requirements, indent=2, ensure_ascii=False)}\n\n"
            f"Candidatos:\n{json.dumps(candidates, indent=2, ensure_ascii=False)}"
        )
        parsed = call_llm_structured(system, user_msg, ValidatorOutput, temperature=0.1, max_tokens=4096, retries=2)
        viable_models = [c for c in candidates if c.get("model") in (parsed.viable_candidates or [])]
        logger.info(f"[VALIDATOR] {len(viable_models)} viable")
        return {
            "validation_results": parsed.validation_results or {},
            "viable_candidates": viable_models,
            "planner_notes": [
                f"Validados {len(candidates)} candidatos",
                f"Viables: {len(viable_models)}, Descartados: {len(parsed.discarded_candidates or [])}",
                *(parsed.validator_notes or []),
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
                "confidence": 0, "next_step": "responder",
                "escalation_reason": escalation_check,
                "planner_notes": [f"Escalado por: {escalation_check}"],
            }

        system = "Eres el evaluador de calidad. Revisa si hay evidencia suficiente y decide: recommend, clarify, o escalate."
        user_msg = (
            f"Candidatos viables: {len(viable_candidates)}\n"
            f"Campos faltantes: {missing_fields}\n"
            f"Iteraciones: {iteration_count}/3\n"
            f"Confianza actual: {state.get('confidence', 0)}"
        )
        parsed = call_llm_structured(system, user_msg, EvaluatorOutput, temperature=0.1, max_tokens=2048, retries=2)

        if parsed.needs_human_review:
            next_step, escalation = "responder", "human_review_requested"
        elif parsed.confidence >= 70:
            next_step, escalation = "responder", None
        elif iteration_count < 3:
            next_step, escalation = "clarifier", None
        else:
            next_step, escalation = "responder", "confidence_too_low"

        logger.info(f"[EVALUATOR] Confidence: {parsed.confidence}, Next: {next_step}")
        return {
            "confidence": parsed.confidence,
            "evaluator_reasoning": parsed.reasoning,
            "next_step": next_step,
            "escalation_reason": escalation,
            "needs_human_review": parsed.needs_human_review,
            "planner_notes": [
                f"Confianza: {parsed.confidence}",
                f"Siguiente: {next_step}",
                f"Revisión humana: {parsed.needs_human_review}",
                *parsed.evaluator_notes,
            ],
        }
    except Exception as e:
        logger.error(f"[EVALUATOR] Error: {e}", exc_info=True)
        return {"confidence": 0, "next_step": "responder", "escalation_reason": "evaluation_error",
                "planner_notes": [f"ERROR en evaluator: {str(e)}"]}


async def responder_node(state: OrchestratorState) -> Dict[str, Any]:
    viable_candidates = state.get("viable_candidates", [])
    candidates = state.get("candidates", [])
    validation_results = state.get("validation_results", {})
    confidence_score = state.get("confidence", 0)
    next_step = state.get("next_step", "unknown")
    escalation_reason = state.get("escalation_reason")

    try:
        status = "escalated" if escalation_reason else ("recommended" if next_step == "responder" and confidence_score >= 70 and viable_candidates else "needs_clarification")
        discards = [
            {"model": c.get("model"), "reason": f"Failed: {[k for k, v in validation_results.get(c.get('model', ''), {}).get('criteria', {}).items() if not v.get('pass')]}"}
            for c in candidates if c.get("model") not in [v.get("model") for v in viable_candidates]
        ]
        user_msg = f"Estado: {status}\nConfianza: {confidence_score}\nViables: {json.dumps(viable_candidates, indent=2)}\nDescartes: {json.dumps(discards, indent=2)}\nEscalado: {escalation_reason}"
        system = "Eres el asistente final SICK. Genera respuesta clara en español con JSON."
        parsed = call_llm_structured(system, user_msg, ResponderOutput, temperature=0.3, max_tokens=4096, retries=2)

        final_answer = {
            "status": parsed.status or status,
            "message": parsed.message,
            "shortlist": parsed.shortlist or [],
            "discards": parsed.discards or discards,
            "sources": parsed.citations or [],
            "confidence": parsed.confidence or {"score": confidence_score, "rationale": ""},
            "escalation": escalation_reason,
        }
        logger.info(f"[RESPONDER] Status: {final_answer['status']}")
        return {
            "final_answer": final_answer,
            "status": final_answer["status"],
            "planner_notes": [f"Respuesta: {final_answer['status']}", f"Shortlist: {len(final_answer['shortlist'])}"],
        }
    except Exception as e:
        logger.error(f"[RESPONDER] Error: {e}", exc_info=True)
        return {"final_answer": {"status": "escalated", "message": f"Error: {str(e)}", "shortlist": [], "discards": [], "sources": [], "confidence": {"score": 0, "rationale": "Error"}, "escalation": str(e)}, "status": "escalated", "planner_notes": [f"ERROR: {str(e)}"]}
