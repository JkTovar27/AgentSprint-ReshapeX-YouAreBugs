"""
Conditional routing functions for SICK Sensor Orchestrator graph.

Each router function receives state and returns the next node name
based on specific decision criteria.
"""

from typing import Optional, Dict, Any, List


def route_from_planner(state: Dict[str, Any]) -> str:
    """
    Routes from planner node.
    
    Decision logic:
    - If missing_fields present → "clarifier"
    - Else → "retriever"
    
    Args:
        state: Current agent state containing missing_fields
        
    Returns:
        Next node name: "clarifier" or "retriever"
    """
    missing_fields: List[str] = state.get("missing_fields", [])
    
    if missing_fields:
        return "clarifier"
    else:
        return "retriever"


def route_from_evaluator(state: Dict[str, Any]) -> str:
    """
    Routes from evaluator node.
    
    Decision logic:
    1. If needs_human_review=True → "responder" (escalation)
    2. Else if missing_fields AND iteration_count < 3 → "clarifier"
    3. Else → "responder"
    
    Args:
        state: Current agent state containing:
            - needs_human_review: bool
            - missing_fields: List[str]
            - iteration_count: int
        
    Returns:
        Next node name: "clarifier" or "responder"
    """
    needs_human_review: bool = state.get("needs_human_review", False)
    missing_fields: List[str] = state.get("missing_fields", [])
    iteration_count: int = state.get("iteration_count", 0)
    
    # Rule 1: Escalation flag set by evaluator (e.g., confidence < 70)
    if needs_human_review:
        return "responder"
    
    # Rule 2: Can refine requirements if under limit
    if missing_fields and iteration_count < 3:
        return "clarifier"
    
    # Rule 3: Default to responder (may contain partial results or escalation)
    return "responder"


def route_from_clarifier(state: Dict[str, Any]) -> str:
    """
    Routes from clarifier node.
    
    Decision logic:
    1. If iteration_count >= 3 → "responder" (escalation, iteration limit reached)
    2. Else → "retriever" (attempt retrieval with refined requirements)
    
    Guardrail: iteration_count never exceeds 3.
    If iteration == 3 and still missing_fields, clarifier should set:
    - needs_human_review=True
    - Include flag in state for evaluator to detect escalation
    
    Args:
        state: Current agent state containing iteration_count
        
    Returns:
        Next node name: "retriever" or "responder"
    """
    iteration_count: int = state.get("iteration_count", 0)
    
    # Guardrail: If we've tried 3 times, escalate
    if iteration_count >= 3:
        return "responder"
    
    # Otherwise try retrieval with refined requirements
    return "retriever"


# Guardrail helpers (used by orchestrator/graph.py)

def should_escalate_at_clarification_limit(state: Dict[str, Any]) -> bool:
    """
    Check if clarifier should mark escalation due to iteration limit.
    
    Returns True if iteration_count reached max and still missing_fields.
    Orchestrator should set needs_human_review=True when this is True.
    """
    iteration_count: int = state.get("iteration_count", 0)
    missing_fields: List[str] = state.get("missing_fields", [])
    
    return iteration_count >= 3 and bool(missing_fields)


def increment_iteration_count(state: Dict[str, Any]) -> None:
    """
    Safely increments iteration_count, capping at 3.
    
    This ensures iteration_count never exceeds the guardrail limit.
    Call this in clarifier node after attempting clarification.
    """
    current: int = state.get("iteration_count", 0)
    state["iteration_count"] = min(current + 1, 3)
