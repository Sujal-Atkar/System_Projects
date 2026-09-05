"""
The orchestrator. This is the only place the whole loop is wired together:

    DETECT -> ANALYZE -> PLAN -> VALIDATE -> EXECUTE ONE STEP -> OBSERVE

Every other module in this project (policy, economics, AI client, payment
client) is a dependency this class composes — none of them call each other
directly, which is what keeps "AI proposes, economics evaluate, rules
constrain, the financial system executes" enforceable rather than aspirational.
"""
from __future__ import annotations

import logging

from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.ai.gemini_client import GeminiRecoveryClient
from app.clients.payment_system_client import PaymentSystemClient
from app.models.recovery import (
    CaseStatus,
    DecisionSource,
    FailureType,
    PlanStepStatus,
    RecoveryActionType,
    RecoveryAnalysis,
    RecoveryCase,
    RecoveryPlan,
    RecoveryPlanStep,
    RevenueSourceType,
    TimingWindow,
)
from app.policies import recovery_policy
from app.schemas.ai import RecoveryContext

logger = logging.getLogger(__name__)


class RecoveryService:
    def __init__(
        self,
        db: Session,
        payment_client: PaymentSystemClient,
        ai_client: GeminiRecoveryClient,
        economic_floor: float | None = None,
    ) -> None:
        self.db = db
        self.payment_client = payment_client
        self.ai_client = ai_client
        self.economic_floor = economic_floor or recovery_policy.DEFAULT_ECONOMIC_FLOOR

    # ---- DETECT ---------------------------------------------------------

    def detect(
        self,
        *,
        external_reference: str,
        source_type: RevenueSourceType,
        amount: float,
        currency: str,
        failure_type: FailureType,
        failure_reason: str | None,
        provider: str | None,
    ) -> tuple[RecoveryCase, bool]:
        existing = self.db.query(RecoveryCase).filter_by(external_reference=external_reference).first()
        if existing:
            return existing, False

        case = RecoveryCase(
            external_reference=external_reference,
            source_type=source_type,
            amount=amount,
            currency=currency,
            failure_type=failure_type,
            failure_reason=failure_reason,
            provider=provider,
            status=CaseStatus.DETECTED,
        )
        try:
            self.db.add(case)
            self.db.commit()
            self.db.refresh(case)
            return case, True
        except IntegrityError:
            self.db.rollback()
            existing = self.db.query(RecoveryCase).filter_by(external_reference=external_reference).first()
            if existing:
                return existing, False
            raise


    # ---- ANALYZE ----------------------------------------------------------

    async def analyze(
        self,
        case: RecoveryCase,
        *,
        provider_health: dict[str, str],
        reconciliation_open: bool = False,
    ) -> RecoveryAnalysis:
        case.status = CaseStatus.ANALYZING
        self.db.add(case)
        self.db.commit()

        evaluation = recovery_policy.evaluate(
            failure_type=case.failure_type,
            amount=float(case.amount),
            attempt_number=case.attempt_number,
            reconciliation_open=reconciliation_open,
            provider_health=provider_health,
            current_provider=case.provider,
            economic_floor=self.economic_floor,
        )

        intervention_cost = recovery_policy.STRATEGY_CATALOGUE[
            evaluation.recommended_fallback_action
        ].estimated_cost or 1.0

        context = RecoveryContext(
            revenue_source=case.source_type.value,
            external_reference=case.external_reference,
            amount=float(case.amount),
            currency=case.currency,
            failure_type=case.failure_type,
            failure_reason=case.failure_reason,
            provider=case.provider,
            provider_health=provider_health.get(case.provider) if case.provider else None,
            attempt_number=case.attempt_number,
            allowed_actions=evaluation.allowed_actions,
            estimated_intervention_cost=intervention_cost,
        )

        analysis_kwargs: dict
        if evaluation.blocked and evaluation.allowed_actions == [RecoveryActionType.NO_ACTION]:
            # Below economic floor or otherwise hard-blocked with nothing
            # eligible — skip the LLM call entirely. No value in paying
            # for a decision that's already made.
            analysis_kwargs = self._deterministic_no_action(evaluation, case, intervention_cost)
        else:
            decision = await self.ai_client.get_decision(context)
            if decision.succeeded and decision.output is not None:
                validated_action = recovery_policy.validate_ai_action(
                    decision.output.recommended_action, evaluation
                )
                econ = self._economics(
                    amount=float(case.amount),
                    recovery_probability=decision.output.recovery_probability,
                    organic_probability=decision.output.organic_recovery_probability,
                    intervention_cost=intervention_cost,
                )
                final_action = validated_action if econ.worth_intervening else RecoveryActionType.NO_ACTION
                analysis_kwargs = dict(
                    diagnosis=decision.output.diagnosis,
                    recovery_probability=decision.output.recovery_probability,
                    organic_recovery_probability=decision.output.organic_recovery_probability,
                    expected_incremental_value=econ.expected_incremental_value,
                    intervention_cost=econ.intervention_cost,
                    expected_net_value=econ.expected_net_value,
                    reasoning=decision.output.reasoning,
                    confidence=decision.output.confidence,
                    decision_source=DecisionSource.AI,
                    model_version=self.ai_client.model_name,
                    _recommended_action=final_action,
                    _target_provider=decision.output.target_provider,
                    _timing=decision.output.timing,
                    _plan_steps=decision.output.plan,
                )
            else:
                logger.info(
                    "Falling back to deterministic recommendation for case %s (reason: %s)",
                    case.id, decision.failure_reason,
                )
                analysis_kwargs = self._deterministic_fallback(evaluation, case, intervention_cost)

        plan_steps = analysis_kwargs.pop("_plan_steps", [])
        recommended_action = analysis_kwargs.pop("_recommended_action")
        target_provider = analysis_kwargs.pop("_target_provider", None)
        timing = analysis_kwargs.pop("_timing", TimingWindow.IMMEDIATE)

        analysis = RecoveryAnalysis(case_id=case.id, **analysis_kwargs)
        self.db.add(analysis)
        self.db.commit()
        self.db.refresh(analysis)

        self._store_pending_plan(case, recommended_action, target_provider, timing, plan_steps)
        return analysis

    def _economics(self, *, amount, recovery_probability, organic_probability, intervention_cost):
        from app.services.economics import evaluate_economics
        return evaluate_economics(
            revenue_at_risk=amount,
            recovery_probability=recovery_probability,
            organic_recovery_probability=organic_probability,
            intervention_cost=intervention_cost,
        )

    def _deterministic_no_action(self, evaluation, case, intervention_cost) -> dict:
        return dict(
            diagnosis="BELOW_ECONOMIC_FLOOR" if evaluation.economic_floor_applied else (evaluation.block_reason or "BLOCKED"),
            recovery_probability=0.0,
            organic_recovery_probability=0.0,
            expected_incremental_value=0.0,
            intervention_cost=0.0,
            expected_net_value=0.0,
            reasoning="Amount is below the economic floor for automated intervention."
            if evaluation.economic_floor_applied
            else f"Recovery blocked: {evaluation.block_reason}.",
            confidence=1.0,
            decision_source=DecisionSource.FALLBACK,
            model_version=None,
            _recommended_action=RecoveryActionType.NO_ACTION,
            _target_provider=None,
            _timing=TimingWindow.IMMEDIATE,
            _plan_steps=[],
        )

    def _deterministic_fallback(self, evaluation, case, intervention_cost) -> dict:
        action = evaluation.recommended_fallback_action
        return dict(
            diagnosis=case.failure_type.value,
            recovery_probability=0.5,
            organic_recovery_probability=0.1,
            expected_incremental_value=round(0.4 * float(case.amount), 2),
            intervention_cost=intervention_cost,
            expected_net_value=round(0.4 * float(case.amount) - intervention_cost, 2),
            reasoning=f"AI decision unavailable; used deterministic policy recommendation ({action.value}).",
            confidence=0.5,
            decision_source=DecisionSource.FALLBACK,
            model_version=None,
            _recommended_action=action,
            _target_provider=evaluation.healthy_providers[0] if evaluation.healthy_providers else None,
            _timing=TimingWindow.IMMEDIATE,
            _plan_steps=[],
        )

    # ---- PLAN -------------------------------------------------------------

    def _store_pending_plan(self, case, recommended_action, target_provider, timing, plan_steps) -> RecoveryPlan:
        plan = RecoveryPlan(case_id=case.id, status=CaseStatus.PLAN_PROPOSED)
        self.db.add(plan)
        self.db.flush()

        if plan_steps:
            for s in plan_steps:
                self.db.add(RecoveryPlanStep(
                    plan_id=plan.id, step_number=s.step, timing=s.timing,
                    action=s.action, status=PlanStepStatus.PLANNED,
                ))
        else:
            self.db.add(RecoveryPlanStep(
                plan_id=plan.id, step_number=1, timing=timing,
                action=recommended_action, target_provider=target_provider,
                status=PlanStepStatus.PROPOSED,
            ))

        case.status = CaseStatus.PLAN_PROPOSED
        self.db.add(case)
        self.db.commit()
        self.db.refresh(plan)
        return plan

    # ---- VALIDATE + EXECUTE ------------------------------------------------

    async def validate_and_execute_next(
        self, case: RecoveryCase, *, provider_health: dict[str, str], reconciliation_open: bool = False,
    ) -> RecoveryCase:
        plan = case.plans[-1] if case.plans else None
        if plan is None:
            raise ValueError("No plan exists for this case")

        next_step = next(
            (s for s in plan.steps if s.status in (PlanStepStatus.PLANNED, PlanStepStatus.PROPOSED)),
            None,
        )
        if next_step is None:
            case.status = CaseStatus.STOPPED
            self.db.add(case)
            self.db.commit()
            return case

        # VALIDATE — re-check against policy right now, not against the
        # allowed set computed when analyze() ran. State may have changed.
        fresh_evaluation = recovery_policy.evaluate(
            failure_type=case.failure_type,
            amount=float(case.amount),
            attempt_number=case.attempt_number,
            reconciliation_open=reconciliation_open,
            provider_health=provider_health,
            current_provider=case.provider,
            economic_floor=self.economic_floor,
        )
        validated_action = recovery_policy.validate_ai_action(next_step.action, fresh_evaluation)

        case.status = CaseStatus.VALIDATED
        self.db.add(case)
        self.db.commit()

        if validated_action == RecoveryActionType.NO_ACTION:
            next_step.status = PlanStepStatus.SKIPPED
            next_step.outcome = "BLOCKED_AT_EXECUTION_TIME"
            case.status = CaseStatus.STOPPED
            self.db.add_all([next_step, case])
            self.db.commit()
            return case

        # EXECUTE ONE STEP
        case.status = CaseStatus.EXECUTING
        next_step.status = PlanStepStatus.EXECUTING
        self.db.add_all([case, next_step])
        self.db.commit()

        result = await self.payment_client.execute_recovery_action(case.external_reference, validated_action, str(next_step.id), next_step.target_provider)

        # OBSERVE
        if result.success:
            next_step.status = PlanStepStatus.SUCCESS
            next_step.outcome = result.outcome_code
            next_step.executed_at = result.executed_at
            case.status = CaseStatus.RECOVERED
            case.recovered_amount = result.recovered_amount
        else:
            next_step.status = PlanStepStatus.FAILED
            next_step.outcome = result.outcome_code
            next_step.executed_at = result.executed_at
            case.status = CaseStatus.FAILED_RECOVERY

        case.attempt_number += 1
        self.db.add_all([case, next_step])
        self.db.commit()
        self.db.refresh(case)
        return case



    # ---- BACKGROUND PIPELINE -----------------------------------------------

    async def process_background_recovery(self, case_id: str) -> None:
        case = self.db.query(RecoveryCase).filter_by(id=case_id).first()
        if not case:
            logger.warning("Case %s not found for background recovery processing", case_id)
            return

        # Fetch authoritative context from Java payment system
        try:
            auth_ctx = await self.payment_client.get_recovery_context(case.external_reference)
            logger.info("Authoritative context fetched for %s: %s", case.external_reference, auth_ctx)
            # Synchronize authoritative context details
            if "amount" in auth_ctx and auth_ctx["amount"] is not None:
                case.amount = float(auth_ctx["amount"])
            if "currency" in auth_ctx and auth_ctx["currency"]:
                case.currency = str(auth_ctx["currency"])
            if "provider" in auth_ctx and auth_ctx["provider"]:
                case.provider = str(auth_ctx["provider"])
            self.db.add(case)
            self.db.commit()
            self.db.refresh(case)
        except Exception as ex:
            logger.error(
                "Could not obtain authoritative recovery context for %s (%s). In real integration mode, aborting money-moving recovery.",
                case.external_reference, ex,
            )
            case.status = CaseStatus.STOPPED
            self.db.add(case)
            self.db.commit()
            return

        # Provider health snapshot
        provider_health = self.payment_client.get_provider_health_snapshot()

        # Step 1: Analyze & Plan
        await self.analyze(case, provider_health=provider_health)

        # Step 2: Validate & Execute
        await self.validate_and_execute_next(case, provider_health=provider_health)