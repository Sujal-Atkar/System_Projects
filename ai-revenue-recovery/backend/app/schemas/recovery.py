from __future__ import annotations

from datetime import datetime

from pydantic import BaseModel, ConfigDict

from app.models.recovery import (
    CaseStatus,
    DecisionSource,
    FailureType,
    PlanStepStatus,
    RecoveryActionType,
    RevenueSourceType,
    TimingWindow,
)


class PlanStepOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    step_number: int
    timing: TimingWindow
    action: RecoveryActionType
    target_provider: str | None
    status: PlanStepStatus
    outcome: str | None
    executed_at: datetime | None


class PlanOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: str
    status: CaseStatus
    created_at: datetime
    steps: list[PlanStepOut]


class AnalysisOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    diagnosis: str
    recovery_probability: float
    organic_recovery_probability: float
    expected_incremental_value: float
    intervention_cost: float
    expected_net_value: float
    reasoning: str
    confidence: float
    decision_source: DecisionSource
    model_version: str | None
    created_at: datetime


class CaseSummaryOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: str
    external_reference: str
    source_type: RevenueSourceType
    amount: float
    currency: str
    failure_type: FailureType
    status: CaseStatus
    recovered_amount: float | None
    created_at: datetime


class CaseDetailOut(CaseSummaryOut):
    failure_reason: str | None
    provider: str | None
    attempt_number: int
    analyses: list[AnalysisOut]
    plans: list[PlanOut]


class AnalyticsSummaryOut(BaseModel):
    batch_size: int
    revenue_at_risk: float
    revenue_recovered: float
    recovery_rate: float
    intervention_cost_total: float
    net_recovered_revenue: float
    decision_distribution: dict[str, int]
    failure_type_breakdown: dict[str, int]
    baseline_do_nothing: float
    baseline_blind_retry: float
    ai_recovered: float
