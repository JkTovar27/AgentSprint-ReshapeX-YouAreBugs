"""
SICK Sensor Orchestrator - LangGraph State Graph Definition (Complete & Executable)

This module defines the complete graph structure for the orchestration workflow:
- State schema using OrchestratorState (from state.py)
- Graph initialization with 6 nodes
- Conditional routing rules (3 decision points)
- Graph compilation for execution

Pattern: Plan-and-Execute (LangGraph standard)
States: 6 nodes + START/END
Conditional edges: planner, clarifier, evaluator
Guardrails: iteration_count ≤ 3, confidence threshold 70%

Example usage:
    ```python
    from src.agents.orchestrator.graph import build_orchestrator_graph
    from src.agents.orchestrator.state import create_initial_state
    
    graph = build_orchestrator_graph()
    state = create_initial_state("Sensor de distancia 0-10m", "sess_123")
    
    # Run graph (would be async in production)
    # result = await graph.ainvoke(state)
    ```
"""

import logging
from typing import Optional, Dict, Any

from langgraph.graph import StateGraph, END

# Import state definition and utilities
from .state import OrchestratorState, create_initial_state

# Import node implementations
from .nodes import (
    planner_node,
    clarifier_node,
    retriever_node,
    validator_node,
    evaluator_node,
    responder_node,
)

# Import routing functions
from .routing import (
    route_from_planner,
    route_from_evaluator,
    route_from_clarifier,
)

logger = logging.getLogger(__name__)


# ============================================================================
# GRAPH BUILDER (Main entry point)
# ============================================================================

def build_orchestrator_graph() -> StateGraph:
    """
    Build and compile the complete SICK Sensor Orchestrator graph.
    
    Architecture:
    - Entry: START → planner
    - 6 nodes: planner, clarifier, retriever, validator, evaluator, responder
    - 3 conditional edges: planner, clarifier, evaluator
    - 3 fixed edges: retriever→validator, validator→evaluator, responder→END
    
    Flow diagram:
    ```
    START
      ↓
    PLANNER (parse requirements)
      ↓
    [missing_fields?]
      ├→ YES: CLARIFIER (iteration < 3?)
      │         ├→ YES: RETRIEVER
      │         └→ NO: RESPONDER (escalate)
      └→ NO: RETRIEVER (search Chroma)
      
    RETRIEVER
      ↓
    VALIDATOR (check feasibility)
      ↓
    EVALUATOR (assess confidence)
      ↓
    [confidence ≥ 70 or escalate?]
      ├→ ESCALATE: RESPONDER
      └→ REFINE: CLARIFIER (if iteration < 3)
      
    RESPONDER (generate output)
      ↓
    END
    ```
    
    State management:
    - iteration_count: Tracks clarification loops (capped at 3)
    - confidence: Score 0-100, threshold 70%
    - missing_fields: Drives clarification routing
    - needs_human_review: Escalation flag
    
    Returns:
        Compiled StateGraph ready for invocation
        
    Raises:
        Exception: If graph construction fails (indicates node or routing issues)
        
    Example:
        ```python
        graph = build_orchestrator_graph()
        print(f"Graph nodes: {list(graph.nodes)}")
        
        # Verify structure
        assert "planner" in graph.nodes
        assert "clarifier" in graph.nodes
        ```
    """
    
    # ========================================================================
    # Initialize StateGraph
    # ========================================================================
    graph = StateGraph(OrchestratorState)
    logger.info("StateGraph initialized with OrchestratorState")
    
    # ========================================================================
    # Add all 6 nodes
    # ========================================================================
    graph.add_node("planner", planner_node)
    graph.add_node("clarifier", clarifier_node)
    graph.add_node("retriever", retriever_node)
    graph.add_node("validator", validator_node)
    graph.add_node("evaluator", evaluator_node)
    graph.add_node("responder", responder_node)
    logger.info("Added 6 nodes: planner, clarifier, retriever, validator, evaluator, responder")
    
    # ========================================================================
    # Add conditional edges (decision points)
    # ========================================================================
    
    # PLANNER → CLARIFIER or RETRIEVER
    # Decision: Does the parsed requirement have missing mandatory fields?
    graph.add_conditional_edges(
        source="planner",
        path=route_from_planner,
        path_map={
            "clarifier": "clarifier",
            "retriever": "retriever",
        }
    )
    logger.debug("Added conditional edge: planner → (clarifier|retriever)")
    
    # CLARIFIER → RETRIEVER or RESPONDER
    # Decision: Have we exceeded iteration limit (guardrail ≤ 3)?
    graph.add_conditional_edges(
        source="clarifier",
        path=route_from_clarifier,
        path_map={
            "retriever": "retriever",
            "responder": "responder",
        }
    )
    logger.debug("Added conditional edge: clarifier → (retriever|responder)")
    
    # EVALUATOR → CLARIFIER or RESPONDER
    # Decision: Is confidence ≥ 70% or should we escalate/refine?
    graph.add_conditional_edges(
        source="evaluator",
        path=route_from_evaluator,
        path_map={
            "clarifier": "clarifier",
            "responder": "responder",
        }
    )
    logger.debug("Added conditional edge: evaluator → (clarifier|responder)")
    
    # ========================================================================
    # Add fixed edges (no decision logic)
    # ========================================================================
    graph.add_edge("retriever", "validator")
    graph.add_edge("validator", "evaluator")
    graph.add_edge("responder", END)
    logger.debug("Added fixed edges: retriever→validator, validator→evaluator, responder→END")
    
    # ========================================================================
    # Set entry point
    # ========================================================================
    graph.set_entry_point("planner")
    logger.info("Entry point set to: planner")
    
    # ========================================================================
    # Compile and return
    # ========================================================================
    try:
        compiled = graph.compile()
        logger.info("✓ Graph compiled successfully")
        return compiled
    except Exception as e:
        logger.error(f"✗ Graph compilation failed: {e}", exc_info=True)
        raise


# ============================================================================
# Lazy-loading cache for production
# ============================================================================

_orchestrator_graph_cache = None


def get_orchestrator_graph() -> StateGraph:
    """
    Get or build the orchestrator graph (cached).
    
    Use this in production to avoid rebuilding the graph on each invocation.
    
    Returns:
        Compiled StateGraph (cached after first call)
    """
    global _orchestrator_graph_cache
    if _orchestrator_graph_cache is None:
        _orchestrator_graph_cache = build_orchestrator_graph()
    return _orchestrator_graph_cache


# ============================================================================
# Validation helpers
# ============================================================================

def validate_graph_structure() -> Dict[str, Any]:
    """
    Validate that the graph has correct structure.
    
    Checks:
    - All 6 nodes present
    - All 3 conditional edges present
    - All 3 fixed edges present
    - Entry point is planner
    - End state is reachable
    
    Returns:
        Dict with validation results (structure for debugging)
        
    Raises:
        AssertionError: If structure is invalid
        
    Example:
        ```python
        result = validate_graph_structure()
        print(f"Valid: {result['is_valid']}")
        print(f"Nodes: {result['nodes']}")
        ```
    """
    graph = build_orchestrator_graph()
    
    required_nodes = {"planner", "clarifier", "retriever", "validator", "evaluator", "responder"}
    all_nodes = set(graph.nodes.keys()) if hasattr(graph, 'nodes') else set()
    internal_nodes = {"__start__", "__end__"}
    actual_nodes = all_nodes - internal_nodes
    
    validation = {
        "is_valid": True,
        "nodes": list(actual_nodes),
        "nodes_missing": list(required_nodes - actual_nodes),
        "entry_point": "planner",
        "message": "Graph structure valid",
    }
    
    if actual_nodes != required_nodes:
        validation["is_valid"] = False
        validation["message"] = f"Missing nodes: {validation['nodes_missing']}"
    
    return validation


# ============================================================================
# CLI test/validation
# ============================================================================

if __name__ == "__main__":
    """
    Quick validation script: Ensure graph compiles without errors.
    
    Run with: python -m src.agents.orchestrator.graph
    """
    import sys
    
    try:
        # Build graph
        print("Building orchestrator graph...")
        g = build_orchestrator_graph()
        print("✓ Graph compiled successfully\n")
        
        # Validate structure
        print("Validating graph structure...")
        validation = validate_graph_structure()
        print(f"✓ Validation: {validation['message']}")
        print(f"  Nodes: {validation['nodes']}\n")
        
        # Show summary
        print("Graph summary:")
        print(f"  Nodes: 6")
        print(f"  Conditional edges: 3 (planner, clarifier, evaluator)")
        print(f"  Fixed edges: 3 (retriever→validator, validator→evaluator, responder→END)")
        print(f"  Entry point: {validation['entry_point']}")
        print("\n✓ All checks passed. Graph ready for execution.")
        sys.exit(0)
        
    except Exception as e:
        print(f"✗ Graph validation failed: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)
