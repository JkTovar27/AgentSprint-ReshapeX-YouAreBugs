"""
Memory and context management for SICK Sensor Orchestrator.

This module handles session state, context filtering, and debugging logs
WITHOUT persistent storage (session-scoped only).

Strategy:
- Keep full state in memory during session (no database)
- Filter context by relevance before passing to LLM (avoid token bloat)
- Log state transitions for debugging (not for persistence)
- Clear on session end
"""

import logging
from typing import Dict, Any, List, Optional, ContextManager
from contextlib import contextmanager
from dataclasses import dataclass, field
from datetime import datetime

logger = logging.getLogger(__name__)


@dataclass
class SessionMemory:
    """
    In-memory session storage for a single orchestration session.
    
    Scope: Session only (created at START, cleared at END)
    Content: Full state, conversation history, decision log
    
    Attributes:
        session_id: Unique session identifier
        created_at: Session start timestamp
        state_history: Snapshots of state at key transitions
        decision_log: Audit trail of routing decisions
        messages_log: User-orchestrator conversation history
    """
    session_id: str
    created_at: datetime = field(default_factory=datetime.now)
    state_history: List[Dict[str, Any]] = field(default_factory=list)
    decision_log: List[Dict[str, str]] = field(default_factory=list)
    messages_log: List[Dict[str, Any]] = field(default_factory=list)
    
    def record_state(self, state: Dict[str, Any], phase: str) -> None:
        """
        Record state snapshot at a phase transition.
        
        Args:
            state: Current OrchestratorState
            phase: Phase name (e.g., "after_planner", "before_clarifier")
        """
        snapshot = {
            "phase": phase,
            "timestamp": datetime.now().isoformat(),
            "iteration_count": state.get("iteration_count", 0),
            "confidence": state.get("confidence", 0),
            "candidates_count": len(state.get("candidates", [])),
            "missing_fields": state.get("missing_fields", []),
        }
        self.state_history.append(snapshot)
        logger.debug(f"State recorded: {phase} - {snapshot}")
    
    def record_decision(self, from_node: str, to_node: str, reason: str) -> None:
        """
        Record a routing decision for audit trail.
        
        Args:
            from_node: Source node name
            to_node: Destination node name
            reason: Human-readable decision reason
        """
        decision = {
            "timestamp": datetime.now().isoformat(),
            "from": from_node,
            "to": to_node,
            "reason": reason,
        }
        self.decision_log.append(decision)
        logger.info(f"Routing: {from_node} → {to_node} ({reason})")
    
    def record_message(self, role: str, content: str, metadata: Optional[Dict] = None) -> None:
        """
        Record user or system message.
        
        Args:
            role: "user" or "system"
            content: Message content
            metadata: Optional metadata (e.g., iterations, confidence)
        """
        msg = {
            "timestamp": datetime.now().isoformat(),
            "role": role,
            "content": content,
            "metadata": metadata or {},
        }
        self.messages_log.append(msg)
    
    def get_conversation_history(self) -> str:
        """
        Get formatted conversation history for context.
        
        Returns:
            Formatted string of conversation for LLM context
        """
        lines = []
        for msg in self.messages_log:
            role = msg["role"].upper()
            content = msg["content"]
            lines.append(f"[{role}] {content}")
        return "\n".join(lines)


# Global session storage (in-memory, session-scoped)
_active_sessions: Dict[str, SessionMemory] = {}


@contextmanager
def session_context(session_id: str):
    """
    Context manager for session-scoped memory.
    
    Usage:
        with session_context("sess_123") as memory:
            memory.record_decision("planner", "retriever", "requirements complete")
            # Session-scoped state available during context
    
    Cleanup:
        On exit, session is kept until explicitly cleared via clear_session()
    
    Args:
        session_id: Unique session identifier
        
    Yields:
        SessionMemory instance for this session
    """
    if session_id not in _active_sessions:
        _active_sessions[session_id] = SessionMemory(session_id=session_id)
        logger.info(f"Session started: {session_id}")
    
    memory = _active_sessions[session_id]
    yield memory


def get_session_memory(session_id: str) -> Optional[SessionMemory]:
    """
    Retrieve active session memory.
    
    Args:
        session_id: Session identifier
        
    Returns:
        SessionMemory if active, None otherwise
    """
    return _active_sessions.get(session_id)


def clear_session(session_id: str) -> None:
    """
    Clear session memory (cleanup on session end).
    
    Args:
        session_id: Session to clear
    """
    if session_id in _active_sessions:
        memory = _active_sessions.pop(session_id)
        logger.info(f"Session cleared: {session_id} (duration: {len(memory.state_history)} phases)")


def clear_all_sessions() -> None:
    """
    Clear all active sessions (for testing or shutdown).
    """
    count = len(_active_sessions)
    _active_sessions.clear()
    logger.info(f"All sessions cleared ({count} sessions)")


def get_relevant_context(state: Dict[str, Any]) -> Dict[str, Any]:
    """
    Filter state to include only fields relevant for LLM context.
    
    Strategy:
    - Include: user_query, structured_requirements, missing_fields, confidence
    - Exclude: planner_notes, internal metadata, full candidate datasheets
    - This keeps LLM context lean and focused
    
    Args:
        state: Full OrchestratorState
        
    Returns:
        Filtered context dict suitable for LLM prompts
        
    Example:
        ```python
        full_state = {
            "user_query": "...",
            "structured_requirements": {...},
            "missing_fields": ["ip_rating"],
            "planner_notes": [...],  # filtered out
            "candidates": [...]  # full list filtered
        }
        context = get_relevant_context(full_state)
        # context excludes planner_notes, contains only top 3 candidates
        ```
    """
    candidates = state.get("candidates", [])
    
    # Include only top 5 candidates (avoid token bloat)
    compact_candidates = []
    for c in candidates[:5]:
        compact_candidates.append({
            "model": c.get("model", "unknown"),
            "type": c.get("type", ""),
            "range": c.get("range", ""),
            "precision": c.get("precision", ""),
            "ip_rating": c.get("ip_rating", ""),
            "preliminary_score": c.get("preliminary_score", 0),
        })
    
    # Build filtered context
    relevant_context = {
        "user_query": state.get("user_query", ""),
        "structured_requirements": state.get("structured_requirements", {}),
        "missing_fields": state.get("missing_fields", []),
        "iteration_count": state.get("iteration_count", 0),
        "confidence": state.get("confidence", 0),
        "candidates_count": len(candidates),
        "top_candidates": compact_candidates,
        "validation_results": state.get("validation_results", {}),
        "needs_human_review": state.get("needs_human_review", False),
    }
    
    return relevant_context


def log_state_transition(
    state: Dict[str, Any],
    from_node: str,
    to_node: str,
    session_id: Optional[str] = None
) -> None:
    """
    Log state transition for debugging.
    
    Args:
        state: Current state
        from_node: Source node
        to_node: Destination node
        session_id: Optional session ID for recording
    """
    log_entry = {
        "transition": f"{from_node} → {to_node}",
        "iteration": state.get("iteration_count", 0),
        "confidence": state.get("confidence", 0),
        "missing_fields_count": len(state.get("missing_fields", [])),
    }
    
    if session_id:
        memory = get_session_memory(session_id)
        if memory:
            memory.record_decision(from_node, to_node, str(log_entry))
    
    logger.debug(f"Transition: {log_entry}")


def get_session_summary(session_id: str) -> Dict[str, Any]:
    """
    Get summary of session activity.
    
    Useful for debugging and understanding flow.
    
    Args:
        session_id: Session identifier
        
    Returns:
        Summary dict with phase count, decisions, message count
    """
    memory = get_session_memory(session_id)
    if not memory:
        return {"error": "Session not found"}
    
    return {
        "session_id": session_id,
        "created_at": memory.created_at.isoformat(),
        "phases_visited": len(memory.state_history),
        "routing_decisions": len(memory.decision_log),
        "messages_exchanged": len(memory.messages_log),
        "decision_log": memory.decision_log[-10:] if memory.decision_log else [],  # Last 10
    }
