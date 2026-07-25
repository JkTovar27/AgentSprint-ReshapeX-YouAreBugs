# Specification: LangGraph Orchestrator Nodes

**Document Version**: 1.0  
**Last Updated**: 2026-07-25  
**Reference**: `src/agents/orchestrator/nodes.py` | `docs/system_contract.md`

---

## Overview

The LangGraph Orchestrator implements 6 specialized nodes following the **Plan-and-Execute** pattern according to the system contract. Each node is an async function that:

1. **Receives**: `OrchestratorState` - the complete workflow state
2. **Processes**: Performs specialized logic with input validation
3. **Returns**: `Dict[str, Any]` - partial state updates only for fields this node manages
4. **Errors**: Logs and returns error information without crashing the graph

---

## Node Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                      ORCHESTRATOR WORKFLOW                      │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  user_query                                                     │
│      ↓                                                           │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │ 1. PLANNER NODE                                          │  │
│  │ Parse requirements, extract structured_requirements,    │  │
│  │ identify missing_fields                                 │  │
│  └──────────────────────────────────────────────────────────┘  │
│      ↓                                                           │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │ 2. CLARIFIER NODE (if missing_fields > 0)              │  │
│  │ Generate clarification_questions, increment iteration  │  │
│  │ Max 3 iterations → escalate if still incomplete        │  │
│  └──────────────────────────────────────────────────────────┘  │
│      ↓                                                           │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │ 3. RETRIEVER NODE                                        │  │
│  │ Query Chroma for sensor candidates matching            │  │
│  │ structured_requirements                                 │  │
│  └──────────────────────────────────────────────────────────┘  │
│      ↓                                                           │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │ 4. VALIDATOR NODE                                        │  │
│  │ Validate candidates against technical rules,           │  │
│  │ mark viable_candidates, compute validation_results     │  │
│  └──────────────────────────────────────────────────────────┘  │
│      ↓                                                           │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │ 5. EVALUATOR NODE                                        │  │
│  │ Compute confidence score, check guardrails,             │  │
│  │ decide: recommend | escalate | clarify                  │  │
│  └──────────────────────────────────────────────────────────┘  │
│      ↓                                                           │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │ 6. RESPONDER NODE                                        │  │
│  │ Assemble final output: shortlist, discards, sources    │  │
│  │ confidence, escalation_reason → final_answer            │  │
│  └──────────────────────────────────────────────────────────┘  │
│      ↓                                                           │
│  final_answer (JSON-serializable)                              │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## Node Specifications Table

| # | Name | Purpose | Input Fields | Output Fields | Max Errors |
|---|------|---------|--------------|---------------|-----------|
| 1 | **planner_node** | Parse natural language query into structured requirements | `user_query`, `session_id` | `structured_requirements`, `missing_fields` | 1 |
| 2 | **clarifier_node** | Generate clarification questions for missing critical fields | `missing_fields`, `iteration_count` | `clarification_questions`, `iteration_count` | 1 |
| 3 | **retriever_node** | Retrieve sensor candidates from Chroma RAG | `structured_requirements` | `candidates`, `retrieval_context` | 1 |
| 4 | **validator_node** | Validate candidates against technical rules | `candidates`, `structured_requirements` | `viable_candidates`, `validation_results` | 1 |
| 5 | **evaluator_node** | Calculate confidence and route to next step | `viable_candidates`, `missing_fields`, `iteration_count` | `confidence`, `next_step`, `escalation_reason` | 1 |
| 6 | **responder_node** | Assemble final output response | All state fields | `final_answer`, `status`, `shortlist`, `discards` | 1 |

---

## Detailed Node Specifications

### Node 1: PLANNER NODE

**Location**: `src/agents/orchestrator/nodes.py::planner_node`

**Purpose**:  
Parses the user's natural language query and extracts structured requirements according to system_contract section 4.1 (mandatory fields). Identifies which critical fields are missing.

**Trigger Condition**:  
Called at orchestrator start or when user provides clarification.

**Input Contract**:
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `user_query` | str | ✅ | Original user query in natural language |
| `session_id` | str | ✅ | Unique session identifier |

**Output Contract** (returns dict with keys):
| Field | Type | Description |
|-------|------|-------------|
| `structured_requirements` | dict | Extracted fields: sensor_type, range_min_m, range_max_m, precision, environment, ip_rating, temperature_range, communication_interface |
| `missing_fields` | list[str] | Mandatory fields not provided (subset of: sensor_type, range, precision, environment) |
| `planner_notes` | list[str] | Internal notes about parsing (debugging) |

**Guardrails**:
- ✅ Validates `user_query` is not empty
- ✅ Logs query length and parsing metadata
- ✅ Returns empty `structured_requirements` on error (no crash)

**Errors May Raise**:
| Error Type | Condition | HTTP/Log Level |
|------------|-----------|---|
| ValueError | Empty or null `user_query` | DEBUG |
| LogicError | Query parsing fails unexpectedly | ERROR |

**Example Usage**:
```python
state = {
    "user_query": "Sensor de distancia 0-10m, ambiente polvoriento",
    "session_id": "sess_123"
}
result = await planner_node(state)
# Returns:
# {
#     "structured_requirements": {"sensor_type": None, "range_min_m": 0, ...},
#     "missing_fields": ["sensor_type", "ip_rating"],
#     "planner_notes": ["Query length: 55 chars", "Missing 2 mandatory fields: ..."]
# }
```

---

### Node 2: CLARIFIER NODE

**Location**: `src/agents/orchestrator/nodes.py::clarifier_node`

**Purpose**:  
Generates specific, actionable clarification questions to help the user provide missing mandatory fields. Enforces the max 3 iterations guardrail per system_contract section 5.1.

**Trigger Condition**:  
When `missing_fields` is non-empty AND `iteration_count < 3`.

**Input Contract**:
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `missing_fields` | list[str] | ✅ | Fields missing from planner output |
| `structured_requirements` | dict | ✅ | Current parsed requirements (context) |
| `iteration_count` | int | ✅ | Number of clarification iterations so far |

**Output Contract** (returns dict with keys):
| Field | Type | Description |
|-------|------|-------------|
| `clarification_questions` | list[str] | 3-5 specific questions for user (field-specific) |
| `iteration_count` | int | Incremented by 1 (used for guardrail checking) |
| `planner_notes` | list[str] | Internal notes about clarification |

**Guardrails**:
- ✅ Checks `iteration_count <= 3` before proceeding
- ✅ Returns error if iteration limit exceeded
- ✅ Limits questions to 5 per iteration
- ✅ Question templates are field-specific (not generic)

**Errors May Raise**:
| Error Type | Condition | HTTP/Log Level |
|------------|-----------|---|
| LogicError | `iteration_count > 3` (should escalate) | ERROR |
| ValueError | `missing_fields` is empty (no questions to generate) | DEBUG |

**Example Usage**:
```python
state = {
    "missing_fields": ["sensor_type", "precision"],
    "structured_requirements": {...},
    "iteration_count": 1
}
result = await clarifier_node(state)
# Returns:
# {
#     "clarification_questions": [
#         "¿Qué tipo de sensor necesitas? (ej: distancia, presencia, ...)",
#         "¿Qué precisión o resolución necesitas? (ej: ±5cm, ±5%)"
#     ],
#     "iteration_count": 2,
#     "planner_notes": ["Iteration 2: Generating 2 questions", ...]
# }
```

---

### Node 3: RETRIEVER NODE

**Location**: `src/agents/orchestrator/nodes.py::retriever_node`

**Purpose**:  
Queries the Chroma vector database using semantic search to find SICK sensor candidates matching the structured requirements. Uses embeddings for fuzzy matching.

**Trigger Condition**:  
After requirements are complete (no more missing mandatory fields).

**Input Contract**:
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `structured_requirements` | dict | ✅ | Parsed requirements (all mandatory fields should be present) |
| `session_id` | str | ✅ | For logging/tracing |

**Output Contract** (returns dict with keys):
| Field | Type | Description |
|-------|------|-------------|
| `candidates` | list[dict] | Retrieved sensors with fields: model, type, range, precision, ip_rating, temperature_range, communication_interface, datasheet_reference, retrieval_score |
| `retrieval_context` | dict | Metadata: query_embedding_dims, chroma_query_time_ms, total_candidates_found, top_k |
| `planner_notes` | list[str] | Internal notes about retrieval |

**Guardrails**:
- ✅ Validates `structured_requirements` is not empty
- ✅ Logs retrieval score statistics
- ✅ Returns empty candidate list on error (no crash)
- ✅ Preserves datasheet_reference for traceability

**Errors May Raise**:
| Error Type | Condition | HTTP/Log Level |
|------------|-----------|---|
| ConnectionError | Chroma service unavailable | ERROR |
| ValueError | No `structured_requirements` provided | DEBUG |

**Example Usage**:
```python
state = {
    "structured_requirements": {
        "sensor_type": "Distance",
        "range_min_m": 0,
        "range_max_m": 10,
        "precision": "±5cm",
        ...
    },
    "session_id": "sess_123"
}
result = await retriever_node(state)
# Returns:
# {
#     "candidates": [
#         {
#             "model": "SICK S300 Professional",
#             "type": "3D Time-of-Flight Camera",
#             "range": "0.3-8m",
#             "precision": "±50mm @ 8m",
#             "retrieval_score": 0.89,
#             "datasheet_reference": "sick_s300_v2.1_2023",
#             ...
#         },
#         ...
#     ],
#     "retrieval_context": {"total_candidates_found": 5, "chroma_query_time_ms": 45, ...},
#     "planner_notes": ["Retrieved 5 sensor candidates from Chroma", ...]
# }
```

---

### Node 4: VALIDATOR NODE

**Location**: `src/agents/orchestrator/nodes.py::validator_node`

**Purpose**:  
Validates each retrieved candidate sensor against the structured requirements using technical rules. Marks candidates as viable or not with detailed per-criterion reasoning.

**Trigger Condition**:  
After candidate retrieval.

**Input Contract**:
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `candidates` | list[dict] | ✅ | Retrieved sensor candidates |
| `structured_requirements` | dict | ✅ | Requirements to validate against |

**Output Contract** (returns dict with keys):
| Field | Type | Description |
|-------|------|-------------|
| `viable_candidates` | list[dict] | Candidates that passed all validation checks |
| `validation_results` | dict | Per-candidate validation: {model_name: {criterion: {pass: bool, detail: str}}} |
| `validation_errors` | list[str] | Any validation errors encountered |
| `planner_notes` | list[str] | Internal notes about validation |

**Guardrails**:
- ✅ Validates each candidate against: range_match, precision_match, ip_rating, environment_rating
- ✅ Sets `overall_viability` flag based on AND of all criteria
- ✅ Preserves detailed reasoning for each check
- ✅ Returns empty viable_candidates on error (no crash)

**Errors May Raise**:
| Error Type | Condition | HTTP/Log Level |
|------------|-----------|---|
| LogicError | Validation logic fails unexpectedly | ERROR |
| ValueError | No `candidates` provided | DEBUG |

**Example Usage**:
```python
state = {
    "candidates": [
        {"model": "SICK S300", "range": "0.3-8m", "precision": "±50mm @ 8m", ...},
        {"model": "SICK S100", "range": "0.3-5m", "precision": "±30mm @ 5m", ...}
    ],
    "structured_requirements": {
        "range_min_m": 0,
        "range_max_m": 10,
        "precision": "±5cm",
        ...
    }
}
result = await validator_node(state)
# Returns:
# {
#     "viable_candidates": [{"model": "SICK S300", ...}],  # S100 failed (range too low)
#     "validation_results": {
#         "SICK S300": {
#             "range_match": {"pass": True, "detail": "8m ≥ 10m required"},
#             "precision_match": {"pass": True, "detail": "±50mm < ±5cm required"},
#             "overall_viability": True
#         },
#         "SICK S100": {
#             "range_match": {"pass": False, "detail": "5m < 10m required"},
#             "overall_viability": False
#         }
#     },
#     "planner_notes": ["Validated 2 candidates", "1 viable after checks", ...]
# }
```

---

### Node 5: EVALUATOR NODE

**Location**: `src/agents/orchestrator/nodes.py::evaluator_node`

**Purpose**:  
Calculates the confidence score (0-100) based on multiple factors and determines the next orchestration step: **recommend**, **escalate**, or **clarify**. Applies system_contract guardrails.

**Trigger Condition**:  
After validation.

**Input Contract**:
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `viable_candidates` | list[dict] | ✅ | Candidates that passed validation |
| `missing_fields` | list[str] | ✅ | Remaining missing fields |
| `iteration_count` | int | ✅ | Number of clarification iterations |
| `structured_requirements` | dict | ✅ | Requirements for context |

**Output Contract** (returns dict with keys):
| Field | Type | Description |
|-------|------|-------------|
| `confidence` | int | Confidence score 0-100 (threshold: 70+ to recommend) |
| `evaluator_reasoning` | str | Explanation of confidence calculation |
| `next_step` | str | Routing decision: "recommend", "escalate", "clarify" |
| `escalation_reason` | str \| None | If escalating, the reason code |
| `planner_notes` | list[str] | Internal notes |

**Guardrails** (per system_contract section 5.1 & 7):
- ✅ Checks `iteration_count <= 3` (escalate if exceeded)
- ✅ Routes to "clarify" if `confidence < 70` AND `iteration_count < 3`
- ✅ Routes to "escalate" if `confidence < 70` AND `iteration_count >= 3`
- ✅ Routes to "recommend" if `confidence >= 70` AND `viable_candidates > 0`
- ✅ Routes to "escalate" if no viable candidates
- ✅ Applies penalties for missing recommended fields (IP rating -15, temperature -10, interface -10)

**Confidence Calculation**:
```
base = 50 (completeness)
+ min(30, len(viable_candidates) * 10)  # 10 per candidate, max 30
+ max(0, 20 - (iteration_count * 5))    # -5 per iteration
- penalties for missing recommended fields
= confidence (0-100)
```

**Errors May Raise**:
| Error Type | Condition | HTTP/Log Level |
|------------|-----------|---|
| LogicError | Decision logic fails | ERROR |

**Example Usage**:
```python
state = {
    "viable_candidates": [{"model": "SICK S300", ...}],
    "missing_fields": [],
    "iteration_count": 0,
    "confidence": None  # Will be calculated
}
result = await evaluator_node(state)
# Returns:
# {
#     "confidence": 85,
#     "evaluator_reasoning": "Confidence 85 >= 70; 1 viable candidate found",
#     "next_step": "recommend",
#     "escalation_reason": None,
#     "planner_notes": ["Confidence factors: {...}", "Final confidence: 85", "Next step: recommend"]
# }
```

---

### Node 6: RESPONDER NODE

**Location**: `src/agents/orchestrator/nodes.py::responder_node`

**Purpose**:  
Assembles the complete final response JSON according to system_contract section 3.1. Includes shortlist, discards, reasoning, sources, and confidence. This is the last opportunity to validate output integrity before returning to user.

**Trigger Condition**:  
After evaluation (always runs, handles all routing outcomes).

**Input Contract**:
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `viable_candidates` | list[dict] | ✅ | Approved candidates |
| `candidates` | list[dict] | ✅ | All candidates (for computing discards) |
| `validation_results` | dict | ✅ | Validation details per candidate |
| `confidence` | int | ✅ | Confidence score from evaluator |
| `next_step` | str | ✅ | Routing: "recommend", "escalate", or "clarify" |
| `escalation_reason` | str \| None | ✅ | Escalation details |
| `structured_requirements` | dict | ✅ | Original requirements (context) |

**Output Contract** (returns dict with keys):
| Field | Type | Description |
|-------|------|-------------|
| `status` | str | "recommended" \| "escalated" \| "needs_clarification" |
| `shortlist` | list[dict] | Top 3 viable candidates with reasoning |
| `discards` | list[dict] | Rejected candidates with failure reasons |
| `reasons` | dict | Methodology and criteria_applied |
| `sources` | list[dict] | Citations to datasheets (traceability) |
| `confidence` | dict | {score: int, rationale: str} |
| `final_answer` | dict | Complete output JSON per system_contract section 3.1 |
| `planner_notes` | list[str] | Internal notes |

**Output Structure** (per system_contract):
```json
{
  "status": "recommended" | "escalated" | "needs_clarification",
  "shortlist": [
    {
      "model": "SICK S300 Professional",
      "type": "3D Time-of-Flight Camera",
      "range": "0.3-8m",
      "precision": "±50mm @ 8m",
      "reasoning": "Meets all requirements"
    }
  ],
  "discards": [
    {
      "model": "SICK S100",
      "reason": "Failed validation checks: [range_match]"
    }
  ],
  "reasons": {
    "methodology": "Semantic search matching (Chroma) + technical validation",
    "criteria_applied": ["range_match", "precision_match", "ip_rating_validation", ...]
  },
  "sources": [
    {
      "model": "SICK S300",
      "datasheet": "sick_s300_v2.1_2023",
      "section": "Technical Specifications"
    }
  ],
  "confidence": {
    "score": 92,
    "rationale": "1 viable candidate found; confidence sufficient for recommendation"
  },
  "escalation": null | "reason if escalated"
}
```

**Guardrails**:
- ✅ Validates all required output fields are populated
- ✅ Preserves datasheet references for traceability
- ✅ Limits shortlist to top 3 candidates
- ✅ Ensures status matches routing decision
- ✅ Falls back to graceful error response if assembly fails

**Errors May Raise**:
| Error Type | Condition | HTTP/Log Level |
|------------|-----------|---|
| ValueError | Response integrity check fails | ERROR |
| LogicError | Output assembly fails unexpectedly | ERROR |

**Example Usage**:
```python
state = {
    "viable_candidates": [{"model": "SICK S300", "datasheet_reference": "sick_s300_v2.1_2023", ...}],
    "candidates": [{"model": "SICK S300", ...}, {"model": "SICK S100", ...}],
    "confidence": 92,
    "next_step": "recommend",
    "escalation_reason": None,
    ...
}
result = await responder_node(state)
# Returns:
# {
#     "status": "recommended",
#     "shortlist": [{...}],
#     "discards": [{...}],
#     "confidence": {"score": 92, "rationale": "..."},
#     "final_answer": {complete JSON},
#     "planner_notes": ["Response status: recommended", ...]
# }
```

---

## State Field Read/Write Matrix

| Field | Planner | Clarifier | Retriever | Validator | Evaluator | Responder |
|-------|---------|-----------|-----------|-----------|-----------|-----------|
| **user_query** | 🔴 R | - | - | - | - | - |
| **session_id** | 🔴 R | - | 🔴 R | - | - | - |
| **structured_requirements** | 🟢 W | 🔴 R | 🔴 R | 🔴 R | 🔴 R | 🔴 R |
| **missing_fields** | 🟢 W | 🔴 R | - | - | 🔴 R | - |
| **clarification_questions** | - | 🟢 W | - | - | - | - |
| **iteration_count** | - | 🟢 W | - | - | 🔴 R | - |
| **candidates** | - | - | 🟢 W | 🔴 R | - | 🔴 R |
| **retrieval_context** | - | - | 🟢 W | - | - | - |
| **validation_results** | - | - | - | 🟢 W | - | 🔴 R |
| **viable_candidates** | - | - | - | 🟢 W | 🔴 R | 🔴 R |
| **validation_errors** | - | - | - | 🟢 W | - | - |
| **confidence** | - | - | - | - | 🟢 W | 🔴 R |
| **evaluator_reasoning** | - | - | - | - | 🟢 W | - |
| **next_step** | - | - | - | - | 🟢 W | 🔴 R |
| **escalation_reason** | - | - | - | - | 🟢 W | 🔴 R |
| **final_answer** | - | - | - | - | - | 🟢 W |
| **status** | - | - | - | - | - | 🟢 W |
| **shortlist** | - | - | - | - | - | 🟢 W |
| **discards** | - | - | - | - | - | 🟢 W |
| **reasons** | - | - | - | - | - | 🟢 W |
| **sources** | - | - | - | - | - | 🟢 W |
| **planner_notes** | 🟢 W | 🟢 W | 🟢 W | 🟢 W | 🟢 W | 🟢 W |
| **error_messages** | 🟢 W | 🟢 W | 🟢 W | 🟢 W | 🟢 W | 🟢 W |

**Legend**:
- 🔴 **R** = Reads (consumes field)
- 🟢 **W** = Writes (creates/updates field)
- **-** = Not used

---

## Error Handling Strategy

### Per-Node Error Handling

Each node follows a consistent error handling pattern:

1. **Validate inputs** at entry
2. **Log debug information** before processing
3. **Try/catch** only critical operations
4. **Collect errors** in `error_messages` field
5. **Return gracefully** (never crash the graph)
6. **Preserve state** (don't corrupt what works)

### Global Escalation Triggers

| Trigger | Condition | Handler |
|---------|-----------|---------|
| **Iteration Limit** | `iteration_count > 3` | EVALUATOR escalates |
| **Low Confidence** | `confidence < 70` after clarification | EVALUATOR escalates |
| **No Candidates** | Retriever finds 0 matches | EVALUATOR escalates |
| **No Viable** | Validator marks all candidates non-viable | EVALUATOR escalates |
| **Missing Mandatory** | 2+ mandatory fields still missing | EVALUATOR escalates |
| **Unrecoverable Error** | Any unhandled exception | RESPONDER returns escalation |

---

## Guarantees

✅ **Deterministic**: Same input state → same node behavior  
✅ **Idempotent**: Running node twice → same result  
✅ **Atomic**: Node either fully succeeds or returns error (no partial state)  
✅ **Traceable**: All outputs logged, field-by-field documentation  
✅ **Graceful Degradation**: Errors never crash the orchestrator  

---

## Testing Strategy

Each node can be tested with stub implementations (no LLM):

```python
# Example: Testing planner_node in isolation
async def test_planner_with_complete_query():
    state = {
        "user_query": "Sensor de distancia 0-10m, ambiente polvoriento",
        "session_id": "test_sess_1"
    }
    result = await planner_node(state)
    assert "structured_requirements" in result
    assert "missing_fields" in result

# Example: Testing validator with mock candidates
async def test_validator_with_viable_candidates():
    state = {
        "candidates": [
            {"model": "SICK S300", "range": "0.3-8m", "precision": "±50mm @ 8m", ...}
        ],
        "structured_requirements": {
            "range_max_m": 10,
            "precision": "±5cm",
            ...
        }
    }
    result = await validator_node(state)
    assert len(result["viable_candidates"]) > 0
```

---

## Migration & Versioning

**Current Version**: 1.0  
**Backward Compatibility**: N/A (initial version)  
**Future Upgrades**:
- Add multi-language support to clarifier_node
- Integrate actual Chroma client in retriever_node
- Add ML-based confidence scoring in evaluator_node
- Implement LLM-based parsing in planner_node

---

**Document End**
