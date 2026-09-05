"""
Domain models for the AI Revenue Recovery platform.

Financial truth (whether money actually moved) never lives here. These
tables record the AI-workflow's *reasoning and intent* — diagnosis,
economics, proposed plan, execution attempts. The one field that reflects
reality is RecoveryCase.status once a step has actually been attempted via
the PaymentSystemClient, and even that is a workflow-state mirror, not a
ledger. Financial truth belongs to whatever system executes the payment
(currently MockPaymentSystemClient; later the real Java platform's ledger).
"""
from __future__ import annotations

import enum
import uuid
from datetime import datetime, timezone

from sqlalchemy import (
    Boolean,
    DateTime,
    Enum as SAEnum,
    ForeignKey,
    Integer,
    Numeric,
    String,
    Text,
)
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, relationship


def _uuid() -> str:
    return str(uuid.uuid4())


def _now() -> datetime:
    return datetime.now(timezone.utc)


class Base(DeclarativeBase):
    pass


class RevenueSourceType(str, enum.Enum):
    ONE_OFF_PAYMENT = "ONE_OFF_PAYMENT"
    RECURRING_COLLECTION = "RECURRING_COLLECTION"


class FailureType(str, enum.Enum):
    TRANSIENT_PROVIDER_FAILURE = "TRANSIENT_PROVIDER_FAILURE"
    PROVIDER_DEGRADED = "PROVIDER_DEGRADED"
    INSUFFICIENT_FUNDS = "INSUFFICIENT_FUNDS"
    EXPIRED_PAYMENT_METHOD = "EXPIRED_PAYMENT_METHOD"
    INVALID_PAYMENT_METHOD = "INVALID_PAYMENT_METHOD"
    BUSINESS_DECLINE = "BUSINESS_DECLINE"
    MISSED_COLLECTION = "MISSED_COLLECTION"
    OTHER = "OTHER"


class CaseStatus(str, enum.Enum):
    DETECTED = "DETECTED"
    ANALYZING = "ANALYZING"
    PLAN_PROPOSED = "PLAN_PROPOSED"
    VALIDATED = "VALIDATED"
    EXECUTING = "EXECUTING"
    RECOVERED = "RECOVERED"
    FAILED_RECOVERY = "FAILED_RECOVERY"
    STOPPED = "STOPPED"


class RecoveryActionType(str, enum.Enum):
    RETRY_NOW = "RETRY_NOW"
    RETRY_LATER = "RETRY_LATER"
    REROUTE = "REROUTE"
    NOTIFY = "NOTIFY"
    REQUEST_PAYMENT_UPDATE = "REQUEST_PAYMENT_UPDATE"
    WAIT = "WAIT"
    ESCALATE = "ESCALATE"
    NO_ACTION = "NO_ACTION"


class TimingWindow(str, enum.Enum):
    IMMEDIATE = "IMMEDIATE"
    WAIT_SHORT = "WAIT_SHORT"
    WAIT_MEDIUM = "WAIT_MEDIUM"
    WAIT_LONG = "WAIT_LONG"
    NEXT_BILLING_WINDOW = "NEXT_BILLING_WINDOW"
    CUSTOMER_ACTION_REQUIRED = "CUSTOMER_ACTION_REQUIRED"


class PlanStepStatus(str, enum.Enum):
    PLANNED = "PLANNED"
    PROPOSED = "PROPOSED"
    EXECUTING = "EXECUTING"
    SUCCESS = "SUCCESS"
    FAILED = "FAILED"
    SKIPPED = "SKIPPED"


class DecisionSource(str, enum.Enum):
    """Distinguishes a real model decision from a deterministic fallback.
    The UI must always be able to show which one produced a given plan —
    never blur the two together (spec §14)."""

    AI = "AI"
    FALLBACK = "FALLBACK"


class RecoveryCase(Base):
    __tablename__ = "recovery_case"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=_uuid)
    external_reference: Mapped[str] = mapped_column(String(120), unique=True)
    source_type: Mapped[RevenueSourceType] = mapped_column(SAEnum(RevenueSourceType))
    amount: Mapped[float] = mapped_column(Numeric(15, 2))
    currency: Mapped[str] = mapped_column(String(3), default="INR")
    failure_type: Mapped[FailureType] = mapped_column(SAEnum(FailureType))
    failure_reason: Mapped[str | None] = mapped_column(Text, nullable=True)
    provider: Mapped[str | None] = mapped_column(String(60), nullable=True)
    attempt_number: Mapped[int] = mapped_column(Integer, default=1)
    status: Mapped[CaseStatus] = mapped_column(SAEnum(CaseStatus), default=CaseStatus.DETECTED)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_now)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=_now, onupdate=_now
    )
    recovered_amount: Mapped[float | None] = mapped_column(Numeric(15, 2), nullable=True)

    analyses: Mapped[list["RecoveryAnalysis"]] = relationship(
        back_populates="case", cascade="all, delete-orphan"
    )
    plans: Mapped[list["RecoveryPlan"]] = relationship(
        back_populates="case", cascade="all, delete-orphan"
    )


class RecoveryAnalysis(Base):
    __tablename__ = "recovery_analysis"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=_uuid)
    case_id: Mapped[str] = mapped_column(ForeignKey("recovery_case.id"))
    diagnosis: Mapped[str] = mapped_column(String(80))
    recovery_probability: Mapped[float] = mapped_column(Numeric(4, 3))
    organic_recovery_probability: Mapped[float] = mapped_column(Numeric(4, 3))
    expected_incremental_value: Mapped[float] = mapped_column(Numeric(15, 2))
    intervention_cost: Mapped[float] = mapped_column(Numeric(15, 2))
    expected_net_value: Mapped[float] = mapped_column(Numeric(15, 2))
    reasoning: Mapped[str] = mapped_column(Text)
    confidence: Mapped[float] = mapped_column(Numeric(4, 3))
    decision_source: Mapped[DecisionSource] = mapped_column(SAEnum(DecisionSource))
    model_version: Mapped[str | None] = mapped_column(String(60), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_now)

    case: Mapped[RecoveryCase] = relationship(back_populates="analyses")


class RecoveryPlan(Base):
    __tablename__ = "recovery_plan"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=_uuid)
    case_id: Mapped[str] = mapped_column(ForeignKey("recovery_case.id"))
    status: Mapped[CaseStatus] = mapped_column(SAEnum(CaseStatus), default=CaseStatus.PLAN_PROPOSED)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_now)

    case: Mapped[RecoveryCase] = relationship(back_populates="plans")
    steps: Mapped[list["RecoveryPlanStep"]] = relationship(
        back_populates="plan", cascade="all, delete-orphan", order_by="RecoveryPlanStep.step_number"
    )


class RecoveryPlanStep(Base):
    __tablename__ = "recovery_plan_step"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=_uuid)
    plan_id: Mapped[str] = mapped_column(ForeignKey("recovery_plan.id"))
    step_number: Mapped[int] = mapped_column(Integer)
    timing: Mapped[TimingWindow] = mapped_column(SAEnum(TimingWindow))
    action: Mapped[RecoveryActionType] = mapped_column(SAEnum(RecoveryActionType))
    target_provider: Mapped[str | None] = mapped_column(String(60), nullable=True)
    status: Mapped[PlanStepStatus] = mapped_column(SAEnum(PlanStepStatus), default=PlanStepStatus.PLANNED)
    executed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    outcome: Mapped[str | None] = mapped_column(String(120), nullable=True)

    plan: Mapped[RecoveryPlan] = relationship(back_populates="steps")


class ScheduledCollection(Base):
    __tablename__ = "scheduled_collection"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=_uuid)
    customer_ref: Mapped[str] = mapped_column(String(120))
    amount: Mapped[float] = mapped_column(Numeric(15, 2))
    currency: Mapped[str] = mapped_column(String(3), default="INR")
    expected_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    status: Mapped[str] = mapped_column(String(20), default="PENDING")
    payment_reference: Mapped[str | None] = mapped_column(String(120), nullable=True)

