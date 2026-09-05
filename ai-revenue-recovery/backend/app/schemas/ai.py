"""
The AI boundary contract.

RecoveryContext is everything the model is allowed to see. GeminiPlanStep /
GeminiRecoveryOutput is everything the model is allowed to say. Nothing
outside these two shapes crosses the boundary in either direction — the
policy layer (app/policies/recovery_policy.py) is what turns "what the
model said" into "what the system is actually permitted to do."
"""
from __future__ import annotations

from pydantic import BaseModel, ConfigDict, Field, field_validator
from pydantic.alias_generators import to_camel

from app.models.recovery import (
    FailureType,
    RecoveryActionType,
    TimingWindow,
)


class RecoveryContext(BaseModel):
    """Everything the AI is given. Deliberately does not include payment
    credentials, raw provider payloads, or any other merchant's data —
    only what's needed to reason about *this* case."""

    revenue_source: str
    external_reference: str
    amount: float
    currency: str = "INR"
    failure_type: FailureType
    failure_reason: str | None = None
    provider: str | None = None
    provider_health: str | None = None
    attempt_number: int = 1
    previous_attempts: int = 0
    reconciliation_status: str | None = None
    time_since_failure_seconds: int | None = None
    scheduled_collection: bool = False
    allowed_actions: list[RecoveryActionType]
    estimated_intervention_cost: float


class _CamelModel(BaseModel):
    """Gemini's structured-output schema (RESPONSE_SCHEMA in
    app/ai/gemini_client.py) is declared with camelCase property names,
    matching the JSON the API actually returns. This base class accepts
    either camelCase (from the API) or snake_case (constructed in Python,
    e.g. in tests) for the same fields."""

    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class GeminiPlanStep(_CamelModel):
    step: int
    timing: TimingWindow
    action: RecoveryActionType
    status: str = "PROPOSED"


class GeminiCustomerNotification(_CamelModel):
    required: bool = False
    channel: str | None = None


class GeminiRecoveryOutput(_CamelModel):
    """The strict contract the model must return. `recoveryProbability` and
    `organicRecoveryProbability` are the model's *estimates* — the economic
    layer (app/services/economics.py) is what turns them into a bounded
    financial decision, they are never used directly as a green light."""

    diagnosis: str = Field(max_length=80)
    recovery_probability: float = Field(ge=0.0, le=1.0)
    organic_recovery_probability: float = Field(ge=0.0, le=1.0)
    recommended_action: RecoveryActionType
    timing: TimingWindow
    target_provider: str | None = None
    customer_notification: GeminiCustomerNotification = GeminiCustomerNotification()
    reasoning: str = Field(max_length=600)
    confidence: float = Field(ge=0.0, le=1.0)
    plan: list[GeminiPlanStep] = Field(default_factory=list, max_length=6)

    @field_validator("plan")
    @classmethod
    def _plan_not_absurdly_long(cls, v: list[GeminiPlanStep]) -> list[GeminiPlanStep]:
        # A recovery plan longer than this is a sign the model is
        # improvising rather than proposing a bounded intervention.
        if len(v) > 6:
            raise ValueError("recovery plan must not exceed 6 steps")
        return v


class AssistantRequest(BaseModel):
    message: str = Field(min_length=1, max_length=500)


class AssistantResponse(BaseModel):
    answer: str
    source: str
    case_id: str
