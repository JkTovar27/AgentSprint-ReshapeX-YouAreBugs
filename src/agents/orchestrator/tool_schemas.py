"""
Pydantic models and TypedDict definitions for tool input/output schemas.

This module defines the contract for each tool in the orchestrator:
- extract_requirements
- retrieve_sick_docs
- validate_sensor_fit
- build_final_recommendation

All models are validated on tool execution.

Reference: docs/system_contract.md
"""

from typing import Dict, List, Any, Optional, Literal
from pydantic import BaseModel, Field, validator


# ============================================================================
# TOOL 1: extract_requirements
# ============================================================================

class StructuredRequirements(BaseModel):
    """Requirements extracted from user query."""
    
    sensor_type: str = Field(
        ...,
        description="Type of sensor needed (e.g., 'Distance Sensor', '3D Camera')"
    )
    range_min_m: float = Field(
        ...,
        ge=0.0,
        description="Minimum detection range in meters"
    )
    range_max_m: float = Field(
        ...,
        gt=0.0,
        description="Maximum detection range in meters"
    )
    precision: str = Field(
        ...,
        description="Required accuracy (e.g., '±5cm', '±1%', '±50mm @ max_range')"
    )
    environment: List[str] = Field(
        default_factory=list,
        description="Operating conditions (e.g., ['dusty', 'outdoor', 'high_temp'])"
    )
    ip_rating: Optional[str] = Field(
        default=None,
        description="Protection level (IP54, IP67, etc.)"
    )
    temperature_range: Optional[str] = Field(
        default=None,
        description="Operating temperature range"
    )
    communication_interface: Optional[str] = Field(
        default=None,
        description="Protocol required (USB, Ethernet, CAN, etc.)"
    )
    response_time: Optional[str] = Field(
        default=None,
        description="Required latency (e.g., '<100ms')"
    )
    power_budget: Optional[str] = Field(
        default=None,
        description="Max power consumption"
    )
    cost_range: Optional[str] = Field(
        default=None,
        description="Budget constraint if applicable"
    )
    
    @validator("range_max_m")
    def validate_range_order(cls, v, values):
        """Ensure range_max >= range_min."""
        if "range_min_m" in values and v < values["range_min_m"]:
            raise ValueError("range_max_m must be >= range_min_m")
        return v


class ExtractRequirementsInput(BaseModel):
    """Input to extract_requirements tool."""
    
    query: str = Field(
        ...,
        min_length=10,
        description="User's natural language query"
    )
    context: Optional[Dict[str, Any]] = Field(
        default=None,
        description="Optional context (previous conversation, session data)"
    )


class ExtractRequirementsOutput(BaseModel):
    """Output from extract_requirements tool."""
    
    structured_requirements: StructuredRequirements
    missing_fields: List[str] = Field(
        default_factory=list,
        description="Mandatory fields that couldn't be extracted"
    )
    extraction_confidence: float = Field(
        ge=0.0,
        le=1.0,
        description="Confidence in extraction (1.0=all fields clear, 0.2=mostly unclear)"
    )
    planner_notes: List[str] = Field(
        default_factory=list,
        description="Internal reasoning notes"
    )


# ============================================================================
# TOOL 2: retrieve_sick_docs
# ============================================================================

class Candidate(BaseModel):
    """A sensor candidate retrieved from datasheets."""
    
    model: str = Field(..., description="Exact SICK sensor model name")
    type: str = Field(..., description="Sensor classification/type")
    range: str = Field(..., description="Operating range from datasheet")
    precision: str = Field(..., description="Accuracy specification")
    ip_rating: Optional[str] = Field(None, description="Protection level")
    communication_interface: Optional[str] = Field(None, description="Protocol support")
    power_budget: Optional[str] = Field(None, description="Power consumption")
    response_time: Optional[str] = Field(None, description="Response time spec")
    temperature_range: Optional[str] = Field(None, description="Operating temperature")
    datasheet_reference: str = Field(
        ...,
        description="ID to retrieve full datasheet from Chroma"
    )
    retrieval_score: float = Field(
        ge=0.0,
        le=1.0,
        description="Relevance score from vector search"
    )
    source_section: Optional[str] = Field(
        None,
        description="Section of datasheet where specs found (e.g., 'Technical Specs, p. 12')"
    )


class DatasheetSource(BaseModel):
    """Reference to a datasheet source."""
    
    datasheet_id: str = Field(..., description="Unique datasheet ID")
    model: str = Field(..., description="Model name from datasheet")
    last_updated: Optional[str] = Field(None, description="Last datasheet update date")
    confidence_in_source: float = Field(
        ge=0.0,
        le=1.0,
        description="Confidence that source is current/accurate"
    )


class RetrieveSickDocsInput(BaseModel):
    """Input to retrieve_sick_docs tool."""
    
    requirements: StructuredRequirements = Field(
        ...,
        description="Structured requirements to search for"
    )
    max_candidates: int = Field(
        default=10,
        ge=1,
        le=50,
        description="Maximum number of candidates to return"
    )
    min_retrieval_score: float = Field(
        default=0.5,
        ge=0.0,
        le=1.0,
        description="Minimum relevance threshold for candidates"
    )


class RetrieveSickDocsOutput(BaseModel):
    """Output from retrieve_sick_docs tool."""
    
    candidates: List[Candidate] = Field(
        default_factory=list,
        description="Retrieved sensor candidates"
    )
    sources: List[DatasheetSource] = Field(
        default_factory=list,
        description="References to datasheets used"
    )
    retrieval_notes: List[str] = Field(
        default_factory=list,
        description="Notes on retrieval process"
    )


# ============================================================================
# TOOL 3: validate_sensor_fit
# ============================================================================

class CriterionResult(BaseModel):
    """Result of evaluating a single criterion."""
    
    pass_: bool = Field(
        ...,
        alias="pass",
        description="Whether criterion passed"
    )
    detail: str = Field(
        ...,
        description="Explanation of pass/fail"
    )


class ValidationDetail(BaseModel):
    """Validation details for a single sensor."""
    
    criteria: Dict[str, CriterionResult] = Field(
        ...,
        description="Results for each evaluation criterion"
    )
    overall_viability: bool = Field(
        ...,
        description="True if sensor meets all mandatory criteria"
    )
    viability_reason: Optional[str] = Field(
        None,
        description="Explanation of viability decision"
    )
    confidence_per_sensor: float = Field(
        ge=0.0,
        le=100.0,
        description="Confidence score for this sensor"
    )


class ValidateSensorFitInput(BaseModel):
    """Input to validate_sensor_fit tool."""
    
    requirements: StructuredRequirements = Field(
        ...,
        description="Structured requirements to validate against"
    )
    candidates: List[Candidate] = Field(
        ...,
        min_items=1,
        description="Sensor candidates to validate"
    )


class ValidateSensorFitOutput(BaseModel):
    """Output from validate_sensor_fit tool."""
    
    validation_results: Dict[str, ValidationDetail] = Field(
        ...,
        description="Validation details per candidate model"
    )
    viable_candidates: List[str] = Field(
        default_factory=list,
        description="List of viable model names"
    )
    discarded_candidates: List[str] = Field(
        default_factory=list,
        description="List of discarded model names"
    )
    overall_confidence: float = Field(
        ge=0.0,
        le=100.0,
        description="Overall confidence in recommendation"
    )
    confidence_rationale: str = Field(
        ...,
        description="Explanation of confidence score"
    )
    needs_human_review: bool = Field(
        default=False,
        description="True if human review recommended"
    )
    human_review_reason: Optional[str] = Field(
        None,
        description="Reason for human review flag"
    )
    evaluator_notes: List[str] = Field(
        default_factory=list,
        description="Internal reasoning notes"
    )


# ============================================================================
# TOOL 4: build_final_recommendation
# ============================================================================

class ShortlistItem(BaseModel):
    """Recommended sensor in final shortlist."""
    
    model: str = Field(..., description="Sensor model name")
    rank: int = Field(ge=1, description="Ranking in shortlist (1=best)")
    match_score: float = Field(
        ge=0.0,
        le=100.0,
        description="How well sensor matches requirements"
    )
    key_specs: Dict[str, Any] = Field(
        ...,
        description="Important specifications"
    )
    advantages: List[str] = Field(
        default_factory=list,
        description="Why this sensor is good fit"
    )
    limitations: List[str] = Field(
        default_factory=list,
        description="Any limitations or trade-offs"
    )


class DiscardedItem(BaseModel):
    """Sensor that was considered but rejected."""
    
    model: str = Field(..., description="Sensor model name")
    reason: str = Field(..., description="Why it was discarded")


class RecommendationConfidence(BaseModel):
    """Confidence assessment."""
    
    score: int = Field(
        ge=0,
        le=100,
        description="Confidence percentage"
    )
    rationale: str = Field(
        ...,
        description="Why we have this level of confidence"
    )


class Citation(BaseModel):
    """Reference to source material."""
    
    model: str = Field(..., description="Sensor model")
    datasheet_id: str = Field(..., description="Datasheet ID")
    section: Optional[str] = Field(None, description="Section/page reference")
    key_specs: Dict[str, Any] = Field(
        default_factory=dict,
        description="Key specs quoted from source"
    )


class FinalRecommendation(BaseModel):
    """Final recommendation output structure."""
    
    status: Literal["recommended", "escalated", "needs_clarification"] = Field(
        ...,
        description="Recommendation status"
    )
    shortlist: List[ShortlistItem] = Field(
        default_factory=list,
        description="Ranked list of recommended sensors"
    )
    discards: List[DiscardedItem] = Field(
        default_factory=list,
        description="Sensors considered but rejected"
    )
    reasons: Dict[str, Any] = Field(
        default_factory=dict,
        description="Explanation of recommendation logic"
    )
    sources: List[Citation] = Field(
        default_factory=list,
        description="Citations to datasheets"
    )
    confidence: RecommendationConfidence = Field(
        ...,
        description="Confidence assessment"
    )
    escalation: Optional[str] = Field(
        None,
        description="Escalation reason if status='escalated'"
    )


class BuildFinalRecommendationInput(BaseModel):
    """Input to build_final_recommendation tool."""
    
    structured_requirements: StructuredRequirements = Field(
        ...,
        description="Original structured requirements"
    )
    viable_candidates: List[Candidate] = Field(
        ...,
        description="Viable sensor candidates"
    )
    validation_results: Dict[str, ValidationDetail] = Field(
        ...,
        description="Validation details for each candidate"
    )
    overall_confidence: float = Field(
        ge=0.0,
        le=100.0,
        description="Overall confidence score from evaluator"
    )
    needs_human_review: bool = Field(
        default=False,
        description="Whether human review was recommended"
    )
    citations: List[Citation] = Field(
        default_factory=list,
        description="References to use in output"
    )


class BuildFinalRecommendationOutput(BaseModel):
    """Output from build_final_recommendation tool."""
    
    recommendation: FinalRecommendation = Field(
        ...,
        description="Complete final recommendation"
    )
    builder_notes: List[str] = Field(
        default_factory=list,
        description="Notes on how recommendation was built"
    )


# ============================================================================
# ERROR MODELS
# ============================================================================

class ToolError(BaseModel):
    """Standard error response from any tool."""
    
    tool_name: str = Field(..., description="Which tool errored")
    error_type: Literal[
        "validation_error",
        "not_found",
        "database_error",
        "invalid_input",
        "timeout",
        "unknown"
    ] = Field(..., description="Type of error")
    message: str = Field(..., description="Error message")
    details: Optional[Dict[str, Any]] = Field(None, description="Additional error details")


# ============================================================================
# STATE EXAMPLES (for documentation)
# ============================================================================

EXAMPLE_STATE_AFTER_PLANNER = {
    "user_query": "Sensor de distancia 0-10m, ambiente polvoriento, muy preciso",
    "session_id": "sess_abc123",
    "structured_requirements": {
        "sensor_type": "Distance Sensor",
        "range_min_m": 0.0,
        "range_max_m": 10.0,
        "precision": "high",
        "environment": ["dusty"],
        "ip_rating": None,
        "temperature_range": None,
        "communication_interface": None,
        "response_time": None,
        "power_budget": None,
        "cost_range": None
    },
    "missing_fields": ["precision_value", "communication_interface", "ip_rating"],
    "iteration_count": 0,
    "next_step": "clarify",
    "confidence": 0,
    "planner_notes": ["Extracted sensor_type and range", "Precision level vague", "Need to clarify IP rating"]
}

EXAMPLE_STATE_AFTER_RETRIEVER = {
    **EXAMPLE_STATE_AFTER_PLANNER,
    "candidates": [
        {
            "model": "SICK S300 Professional",
            "type": "3D Time-of-Flight Camera",
            "range": "0.3-8m",
            "precision": "±50mm @ 8m",
            "ip_rating": "IP67",
            "communication_interface": "USB 3.0",
            "datasheet_reference": "sick_s300_v2.1_2023",
            "retrieval_score": 0.94
        },
        {
            "model": "SICK TIM781S",
            "type": "2D Laser Scanner",
            "range": "0.0-25m",
            "precision": "±25mm",
            "ip_rating": "IP67",
            "communication_interface": "Ethernet",
            "datasheet_reference": "sick_tim781s_v3.2_2023",
            "retrieval_score": 0.87
        }
    ],
    "next_step": "validate",
    "confidence": 40,
    "planner_notes": ["Retrieved 2 candidates matching range and environment"]
}

EXAMPLE_STATE_AFTER_EVALUATOR = {
    **EXAMPLE_STATE_AFTER_RETRIEVER,
    "validation_results": {
        "SICK S300 Professional": {
            "criteria": {
                "range_coverage": {"pass": False, "detail": "8m < 10m max"},
                "precision_match": {"pass": True, "detail": "±50mm acceptable"},
                "environment_fit": {"pass": True, "detail": "IP67 supports dusty"}
            },
            "overall_viability": False,
            "viability_reason": "Range 0.3-8m does not cover 10m maximum",
            "confidence_per_sensor": 65
        },
        "SICK TIM781S": {
            "criteria": {
                "range_coverage": {"pass": True, "detail": "0-25m covers 0-10m"},
                "precision_match": {"pass": True, "detail": "±25mm acceptable"},
                "environment_fit": {"pass": True, "detail": "IP67 supports dusty"}
            },
            "overall_viability": True,
            "viability_reason": "Meets all mandatory criteria",
            "confidence_per_sensor": 92
        }
    },
    "viable_candidates": ["SICK TIM781S"],
    "overall_confidence": 78,
    "needs_human_review": False,
    "next_step": "respond",
    "confidence": 78,
    "planner_notes": ["1 viable candidate found with 92% confidence", "Ready to recommend"]
}
