"""
OrchestratorState TypedDict for LangGraph SICK Sensor Orchestrator.

This module defines the complete state structure for the orchestration workflow,
including all fields required by the system contract and validation guardrails.

Reference: docs/system_contract.md v1.0
"""

from typing import TypedDict, Optional, List, Dict, Any
from typing_extensions import NotRequired


class OrchestratorState(TypedDict):
    """
    Complete state definition for the SICK Sensor Orchestrator LangGraph.
    
    This state controls the entire flow from user query to final recommendation,
    with built-in guardrails for iteration limits and validation requirements.
    
    Example:
        ```python
        initial_state = OrchestratorState(
            user_query="Necesito sensor de distancia 0-10m, ambiente polvoriento",
            session_id="sess_123456",
            iteration_count=0,
            structured_requirements={},
            missing_fields=[],
            retrieval_context=None,
            candidates=[],
            validation_results={},
            confidence=0,
            next_step="parse",
            needs_human_review=False,
            final_answer=None,
            citations=[],
            planner_notes=[]
        )
        ```
    """

    # ========== CORE INPUT ==========
    user_query: str
    """
    Original user query in natural language.
    
    Example: "Necesito un sensor de distancia para un ambiente polvoriento, 
    rango 0-10m, precisión ±5cm"
    """

    session_id: str
    """
    Unique session identifier for tracking and audit purposes.
    
    Format: sess_<uuid> or similar persistent identifier
    """

    # ========== PARSING & VALIDATION ==========
    structured_requirements: NotRequired[Dict[str, Any]]
    """
    Parsed requirements extracted from user_query.
    
    Example:
    ```python
    {
        "sensor_type": "Time-of-Flight Distance",
        "range_min_m": 0.0,
        "range_max_m": 10.0,
        "precision": "±5cm",
        "environment": ["dusty"],
        "ip_rating": None,
        "temperature_range": None,
        "communication_interface": None
    }
    ```
    """

    missing_fields: NotRequired[List[str]]
    """
    List of mandatory fields that are missing from the structured_requirements.
    
    Mandatory fields per system_contract:
    - sensor_type: What the sensor detects
    - range: Operating range (min-max in meters)
    - precision: Required accuracy
    - environment: Operating conditions
    
    Example: ["ip_rating", "communication_interface"]
    """

    # ========== RETRIEVAL ==========
    retrieval_context: NotRequired[Optional[Dict[str, Any]]]
    """
    Context retrieved from RAG/Chroma vector database.
    
    Contains matching sensor datasheets and technical specs based on
    semantic search of structured_requirements.
    
    Example:
    ```python
    {
        "query_embedding": [...],
        "matched_documents": [
            {
                "datasheet_id": "sick_s300_v2.1",
                "model": "SICK S300 Professional",
                "sections": ["Technical Specifications", "Environmental Ratings"]
            }
        ],
        "retrieval_score": 0.89
    }
    ```
    """

    # ========== CANDIDATE EVALUATION ==========
    candidates: NotRequired[List[Dict[str, Any]]]
    """
    List of candidate sensors after retrieval and structured extraction.
    
    Each candidate includes:
    - model: Sensor model name
    - type: Sensor classification
    - range: Operating range
    - precision: Accuracy specification
    - ip_rating: Protection level
    - datasheet_reference: ID or URL to source
    - retrieval_score: Relevance score from Chroma (0-1)
    - rango_distancia_mm: [min, max] in mm for rule validation
    - ambientes_soportados: list of supported environment conditions
    - materiales_soportados: list of detectable materials
    """

    validation_results: NotRequired[Dict[str, Any]]
    """
    Detailed validation results from evaluar_familia() deterministic rules.
    
    Each entry contains:
    - criteria: dict with range_coverage, environment_fit, material_compatibility
    - overall_viability: bool
    - veredicto: "viable" | "descartada" | "ambigua"
    - reglas_pasadas: int
    - reglas_falladas: int
    - reglas_no_evaluadas: int
    - razones: list[str]
    - no_evaluables: list[str]
    """

    evaluaciones: NotRequired[List[Dict[str, Any]]]
    """
    Compact evaluation results from validator, consumed by evaluator.
    
    Each entry:
    - veredicto: str
    - reglas_pasadas: int
    - reglas_falladas: int
    - reglas_no_evaluadas: int
    """

    confianza_desglose: NotRequired[Dict[str, float]]
    """
    Breakdown of confidence calculation from calcular_confianza().
    
    Keys: completitud, calidad_evidencia, certeza_reglas
    Values: 0.0-1.0
    """

    # ========== CONFIDENCE & ROUTING ==========
    confidence: NotRequired[int]
    """
    Confidence score for the current recommendation state: 0-100.
    
    Thresholds per system_contract:
    - ≥ 70: Can recommend
    - < 70: Must escalate or request clarification
    
    Scoring factors:
    - Completeness of requirements
    - Number of viable candidates
    - Data freshness (datasheet currency)
    - Alignment with structured_requirements
    """

    next_step: NotRequired[str]
    """
    Controls routing to next orchestration step.
    
    Valid values:
    - "parse": Initial parsing of user_query
    - "clarify": Request more information from user
    - "retrieve": Query Chroma for candidate sensors
    - "validate": Validate candidates against requirements
    - "evaluate": Calculate confidence and final ranking
    - "respond": Generate final output
    - "escalate": Escalate to human review
    """

    # ========== ITERATION CONTROL ==========
    iteration_count: NotRequired[int]
    """
    Counter for clarification iterations.
    
    Guardrail: Maximum 3 iterations. After 3 clarifications:
    - If still ambiguous → must escalate
    - If still < 70% confidence → must escalate
    
    This prevents infinite loops and ensures timely escalation.
    """

    # ========== HUMAN REVIEW ==========
    needs_human_review: NotRequired[bool]
    """
    Flag indicating if human review is required.
    
    True when:
    - Multiple viable candidates with similar scores
    - Domain expertise needed for trade-offs
    - User has conflicting requirements
    - Confidence < 70% after all clarifications
    """

    # ========== FINAL OUTPUT ==========
    final_answer: NotRequired[Optional[Dict[str, Any]]]
    """
    Complete final recommendation output (JSON-serializable).
    
    Populated only when status is "recommended" or "escalated".
    Structure per system_contract Output Contract (section 3.1):
    
    ```python
    {
        "status": "recommended" | "escalated" | "needs_clarification",
        "shortlist": [...],
        "discards": [...],
        "reasons": {...},
        "sources": [...],
        "confidence": {"score": int, "rationale": str},
        "escalation": None | str
    }
    ```
    """

    citations: NotRequired[List[Dict[str, Any]]]
    """
    References to datasheets and technical sources.
    
    Maintains complete traceability from recommendation back to source data.
    
    Example:
    ```python
    [
        {
            "model": "SICK S300 Professional",
            "datasheet_id": "sick_s300_v2.1_2023",
            "section": "3. Technical Specifications",
            "pages": "12-15",
            "key_specs": {
                "range": "0.3-8m",
                "precision": "±50mm @ 8m"
            }
        }
    ]
    ```
    """

    # ========== INTERNAL NOTES ==========
    planner_notes: NotRequired[List[str]]
    """
    Internal notes and reasoning captured during orchestration.
    
    Used for debugging, audit trails, and understanding decision paths.
    NOT part of user-facing output.
    
    Example:
    ["Parsed 4 mandatory fields", "Retrieved 12 candidates from Chroma", 
     "Filtered to 3 viable after validation", "Ready for recommendation"]
    """


def create_initial_state(user_query: str, session_id: str) -> OrchestratorState:
    """
    Factory function to create an initial OrchestratorState.
    
    Args:
        user_query: The user's original query in natural language
        session_id: Unique session identifier
    
    Returns:
        OrchestratorState with sensible defaults for orchestration start
    
    Example:
        ```python
        state = create_initial_state(
            user_query="Sensor de distancia 0-10m, ambiente polvoriento",
            session_id="sess_abc123"
        )
        assert state["iteration_count"] == 0
        assert state["confidence"] == 0
        assert state["next_step"] == "parse"
        ```
    """
    return OrchestratorState(
        user_query=user_query,
        session_id=session_id,
        structured_requirements={},
        missing_fields=[],
        retrieval_context=None,
        candidates=[],
        validation_results={},
        confidence=0,
        next_step="parse",
        iteration_count=0,
        needs_human_review=False,
        final_answer=None,
        citations=[],
        planner_notes=["State initialized", f"Session: {session_id}"]
    )


def validate_iteration_count(state: OrchestratorState) -> bool:
    """
    Validate that iteration count is within acceptable limits.
    
    Guardrail: If iteration_count > 3, the orchestrator MUST escalate
    to prevent infinite loops and ensure timely resolution.
    
    Args:
        state: Current OrchestratorState
    
    Returns:
        True if iteration count is acceptable (≤ 3), False otherwise
    
    Side effect:
        Caller should escalate when this returns False.
    
    Example:
        ```python
        state["iteration_count"] = 2
        assert validate_iteration_count(state) == True
        
        state["iteration_count"] = 4
        assert validate_iteration_count(state) == False
        # → Orchestrator must escalate now
        ```
    """
    MAX_CLARIFICATION_ITERATIONS = 3
    iteration_count = state.get("iteration_count", 0)
    return iteration_count <= MAX_CLARIFICATION_ITERATIONS


def check_escalation_trigger(state: OrchestratorState) -> Optional[str]:
    """
    Determine if current state meets escalation criteria.
    
    Returns escalation reason if triggered, None otherwise.
    
    Escalation triggers per system_contract section 5.1:
    1. iteration_count > 3
    2. confidence < 70 after all processing
    3. Missing 2+ mandatory fields (only checked after initial parsing)
    4. No viable candidates found
    5. Out of domain (non-SICK sensors)
    
    Args:
        state: Current OrchestratorState
    
    Returns:
        Escalation reason string, or None if no escalation needed
    
    Example:
        ```python
        state["iteration_count"] = 4
        reason = check_escalation_trigger(state)
        assert reason == "iteration_count_exceeded"
        
        state["iteration_count"] = 2
        state["confidence"] = 45
        reason = check_escalation_trigger(state)
        assert reason == "confidence_too_low"
        ```
    """
    iteration_count = state.get("iteration_count", 0)
    confidence = state.get("confidence", 0)
    candidates = state.get("candidates", [])
    next_step = state.get("next_step", "parse")
    
    # Check iteration limit (highest priority)
    if iteration_count > 3:
        return "iteration_count_exceeded"
    
    # Check confidence threshold when at max iterations
    if iteration_count >= 3 and confidence < 70:
        return "confidence_too_low_after_max_iterations"
    
    # Check mandatory fields only after parsing phase
    if next_step not in ["parse"] and iteration_count > 0:
        mandatory_fields = {"sensor_type", "range_min_m", "range_max_m", "precision", "environment"}
        structured = state.get("structured_requirements", {})
        missing_mandatory = [f for f in mandatory_fields if f not in structured or not structured[f]]
        if len(missing_mandatory) >= 2:
            return "insufficient_mandatory_fields"
    
    # Check no viable candidates after validation phase
    if next_step in ["validate", "evaluate", "respond"]:
        validation = state.get("validation_results", {})
        viable_count = sum(1 for v in validation.values() if isinstance(v, dict) and v.get("overall_viability"))
        if candidates and viable_count == 0:
            return "no_viable_candidates"
    
    return None
