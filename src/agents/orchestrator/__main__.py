"""
Test and validation script for SICK Sensor Orchestrator graph.

This module provides an executable skeleton that:
- Verifies graph compiles without errors
- Tests basic structure: state → planner → flow
- Uses dummy/stub implementations (no real LLM)
- Validates iteration_count and conditional routing

Run with: python -m src.agents.orchestrator
"""

import asyncio
import sys
import logging
from typing import Dict, Any

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s"
)
logger = logging.getLogger(__name__)

# Import orchestrator components
from .graph import build_orchestrator_graph, validate_graph_structure
from .state import create_initial_state
from .memory import session_context, get_relevant_context, clear_session


def test_graph_structure() -> bool:
    """
    Test 1: Verify graph compiles and has correct structure.
    
    Checks:
    - Graph builds without exceptions
    - All 6 nodes present
    - All conditional edges present
    - Entry point is 'planner'
    
    Returns:
        True if all checks pass
    """
    print("\n" + "="*70)
    print("TEST 1: Graph Structure Validation")
    print("="*70)
    
    try:
        # Build graph
        print("Building graph...")
        graph = build_orchestrator_graph()
        print("✓ Graph compiled successfully")
        
        # Validate structure
        print("\nValidating structure...")
        validation = validate_graph_structure()
        
        if not validation["is_valid"]:
            print(f"✗ Validation failed: {validation['message']}")
            return False
        
        print(f"✓ {validation['message']}")
        print(f"  Nodes: {validation['nodes']}")
        print(f"  Entry point: {validation['entry_point']}")
        
        return True
        
    except Exception as e:
        print(f"✗ Test failed: {e}")
        import traceback
        traceback.print_exc()
        return False


def test_state_initialization() -> bool:
    """
    Test 2: Verify state initialization and schema.
    
    Checks:
    - Initial state has all required fields
    - iteration_count starts at 0
    - confidence starts at 0
    - next_step is "parse"
    
    Returns:
        True if all checks pass
    """
    print("\n" + "="*70)
    print("TEST 2: State Initialization")
    print("="*70)
    
    try:
        print("Creating initial state...")
        state = create_initial_state(
            user_query="Sensor de distancia 0-10m, ambiente polvoriento",
            session_id="test_sess_001"
        )
        
        # Verify key fields
        checks = {
            "user_query": state.get("user_query") != "",
            "session_id": state.get("session_id") == "test_sess_001",
            "iteration_count": state.get("iteration_count") == 0,
            "confidence": state.get("confidence") == 0,
            "next_step": state.get("next_step") == "parse",
            "structured_requirements": isinstance(state.get("structured_requirements"), dict),
            "missing_fields": isinstance(state.get("missing_fields"), list),
            "candidates": isinstance(state.get("candidates"), list),
        }
        
        all_passed = True
        for field, check in checks.items():
            status = "✓" if check else "✗"
            print(f"  {status} {field}: {check}")
            all_passed = all_passed and check
        
        if all_passed:
            print("\n✓ State initialization passed")
        else:
            print("\n✗ Some state fields invalid")
            return False
        
        return True
        
    except Exception as e:
        print(f"✗ Test failed: {e}")
        import traceback
        traceback.print_exc()
        return False


def test_iteration_count_guardrail() -> bool:
    """
    Test 3: Verify iteration_count guardrail (max 3).
    
    Checks:
    - iteration_count increments correctly
    - iteration_count is capped at 3 (no overshoot)
    
    Returns:
        True if guardrail works correctly
    """
    print("\n" + "="*70)
    print("TEST 3: Iteration Count Guardrail")
    print("="*70)
    
    try:
        from .routing import increment_iteration_count
        
        state = create_initial_state("Test query", "test_sess_002")
        
        print("Testing iteration count increments...")
        print(f"  Initial: iteration_count = {state['iteration_count']}")
        
        # Increment 5 times (should cap at 3)
        for i in range(5):
            increment_iteration_count(state)
            count = state['iteration_count']
            print(f"  After increment {i+1}: iteration_count = {count}")
        
        # Verify capped at 3
        if state['iteration_count'] != 3:
            print(f"✗ Iteration count not capped: {state['iteration_count']} != 3")
            return False
        
        print("\n✓ Iteration count guardrail working (capped at 3)")
        return True
        
    except Exception as e:
        print(f"✗ Test failed: {e}")
        import traceback
        traceback.print_exc()
        return False


def test_memory_context() -> bool:
    """
    Test 4: Verify memory and context management.
    
    Checks:
    - session_context creates and retrieves memory
    - get_relevant_context filters state correctly
    - Context is smaller than full state
    
    Returns:
        True if memory management works
    """
    print("\n" + "="*70)
    print("TEST 4: Memory and Context Management")
    print("="*70)
    
    try:
        print("Testing session memory...")
        session_id = "test_sess_003"
        
        with session_context(session_id) as memory:
            print(f"✓ Session context created: {session_id}")
            
            # Record a decision
            memory.record_decision("planner", "retriever", "requirements complete")
            print(f"✓ Decision recorded")
            
            # Record state
            state = create_initial_state("Test", session_id)
            memory.record_state(state, "after_planner")
            print(f"✓ State recorded")
        
        # Test context filtering
        print("\nTesting context filtering...")
        state = create_initial_state("Test", session_id)
        state["candidates"] = [
            {"model": "SICK S300", "type": "Distance", "range": "0-8m",
             "precision": "±50mm", "preliminary_score": 0.85},
            {"model": "SICK TiM", "type": "Scanner", "range": "0-25m",
             "precision": "±30mm", "preliminary_score": 0.92},
            {"model": "SICK LMS", "type": "Scanner", "range": "0-30m",
             "precision": "±20mm", "preliminary_score": 0.78},
        ]
        
        full_size = len(str(state))
        context = get_relevant_context(state)
        context_size = len(str(context))
        
        print(f"  Full state size: {full_size} chars")
        print(f"  Filtered context size: {context_size} chars")
        print(f"  Reduction: {100 * (1 - context_size/full_size):.0f}%")
        
        if context_size < full_size:
            print("✓ Context filtering reduces token usage")
        else:
            print("✗ Context not reduced")
            return False
        
        # Cleanup
        clear_session(session_id)
        print("✓ Session cleared")
        
        return True
        
    except Exception as e:
        print(f"✗ Test failed: {e}")
        import traceback
        traceback.print_exc()
        return False


def test_conditional_routing() -> bool:
    """
    Test 5: Verify conditional routing functions.
    
    Checks:
    - route_from_planner returns correct node
    - route_from_clarifier respects iteration limit
    - route_from_evaluator routes based on needs_human_review
    
    Returns:
        True if routing works correctly
    """
    print("\n" + "="*70)
    print("TEST 5: Conditional Routing")
    print("="*70)
    
    try:
        from .routing import (
            route_from_planner,
            route_from_clarifier,
            route_from_evaluator,
        )
        
        # Test route_from_planner
        print("Testing route_from_planner...")
        state_with_missing = {"missing_fields": ["ip_rating"], "candidates": []}
        route = route_from_planner(state_with_missing)
        print(f"  With missing_fields: → {route}")
        if route != "clarifier":
            print(f"✗ Expected 'clarifier', got '{route}'")
            return False
        
        state_complete = {"missing_fields": [], "candidates": []}
        route = route_from_planner(state_complete)
        print(f"  Without missing_fields: → {route}")
        if route != "retriever":
            print(f"✗ Expected 'retriever', got '{route}'")
            return False
        
        # Test route_from_clarifier (iteration limit)
        print("\nTesting route_from_clarifier...")
        state_under_limit = {"iteration_count": 1}
        route = route_from_clarifier(state_under_limit)
        print(f"  iteration_count=1: → {route}")
        if route != "retriever":
            print(f"✗ Expected 'retriever', got '{route}'")
            return False
        
        state_at_limit = {"iteration_count": 3}
        route = route_from_clarifier(state_at_limit)
        print(f"  iteration_count=3: → {route}")
        if route != "responder":
            print(f"✗ Expected 'responder', got '{route}'")
            return False
        
        # Test route_from_evaluator
        print("\nTesting route_from_evaluator...")
        state_escalate = {
            "needs_human_review": True,
            "missing_fields": [],
            "iteration_count": 1
        }
        route = route_from_evaluator(state_escalate)
        print(f"  needs_human_review=True: → {route}")
        if route != "responder":
            print(f"✗ Expected 'responder', got '{route}'")
            return False
        
        print("\n✓ All routing tests passed")
        return True
        
    except Exception as e:
        print(f"✗ Test failed: {e}")
        import traceback
        traceback.print_exc()
        return False


def main() -> int:
    """
    Run all tests and report results.
    
    Returns:
        Exit code: 0 if all tests pass, 1 if any fail
    """
    print("\n" + "="*70)
    print("SICK SENSOR ORCHESTRATOR - SKELETON TEST SUITE")
    print("="*70)
    print("\nNo LLM calls, no external dependencies.")
    print("Pure structural validation of graph and state management.")
    
    tests = [
        ("Graph Structure", test_graph_structure),
        ("State Initialization", test_state_initialization),
        ("Iteration Guardrail", test_iteration_count_guardrail),
        ("Memory Management", test_memory_context),
        ("Conditional Routing", test_conditional_routing),
    ]
    
    results = []
    for test_name, test_func in tests:
        try:
            passed = test_func()
            results.append((test_name, passed))
        except Exception as e:
            logger.exception(f"Test {test_name} crashed: {e}")
            results.append((test_name, False))
    
    # Print summary
    print("\n" + "="*70)
    print("TEST SUMMARY")
    print("="*70)
    
    passed_count = sum(1 for _, p in results if p)
    total_count = len(results)
    
    for test_name, passed in results:
        status = "✓ PASS" if passed else "✗ FAIL"
        print(f"{status}: {test_name}")
    
    print(f"\nTotal: {passed_count}/{total_count} tests passed")
    
    if passed_count == total_count:
        print("\n✓ ALL TESTS PASSED - GRAPH SKELETON READY FOR HACKATHON")
        return 0
    else:
        print(f"\n✗ {total_count - passed_count} test(s) failed")
        return 1


if __name__ == "__main__":
    exit_code = main()
    sys.exit(exit_code)
