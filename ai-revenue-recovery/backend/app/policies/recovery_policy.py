"""
The deterministic safety boundary. This module — not the AI — decides what
is legally executable. The AI (app/ai/gemini_client.py) may only choose
from what compute_allowed_actions() returns, and validate_ai_output() is
the hard re-check every model decision passes through before anything is
persisted or executed.

Mirrors the same principle already proven in the Java platform's
RecoveryPolicy: policy computes the allowed set, AI chooses from it,
policy re-validates the choice before execution.
"""
from __future__ import annotations

from dataclasses import dataclass, field

from app.models.recovery import FailureType, RecoveryActionType

# Failure types that must never be automatically retried — same principle
# as RecoveryPolicy.isBusinessFailure() in the Java platform.
BUSINESS_FAILURE_TYPES = frozenset({
    FailureType.INSUFFICIENT_FUNDS,
    FailureType.BUSINESS_DECLINE,
})

# Below this amount, no automated intervention is worth its own cost.
DEFAULT_ECONOMIC_FLOOR = 100.0

MAX_ATTEMPTS = 3


@dataclass(frozen=True)
class StrategyMetadata:
    strategy: RecoveryActionType
    description: str
    eligible_failure_types: frozenset[FailureType]
    requires_provider: bool = False
    supports_delay: bool = False
    requires_customer_action: bool = False
    estimated_cost: float = 0.0


STRATEGY_CATALOGUE: dict[RecoveryActionType, StrategyMetadata] = {
    RecoveryActionType.RETRY_NOW: StrategyMetadata(
        strategy=RecoveryActionType.RETRY_NOW,
        description="Immediately retry on the same provider.",
        eligible_failure_types=frozenset({FailureType.TRANSIENT_PROVIDER_FAILURE}),
        estimated_cost=1.0,
    ),
    RecoveryActionType.RETRY_LATER: StrategyMetadata(
        strategy=RecoveryActionType.RETRY_LATER,
        description="Retry after a delay, same provider.",
        eligible_failure_types=frozenset({
            FailureType.TRANSIENT_PROVIDER_FAILURE, FailureType.PROVIDER_DEGRADED,
        }),
        supports_delay=True,
        estimated_cost=1.0,
    ),
    RecoveryActionType.REROUTE: StrategyMetadata(
        strategy=RecoveryActionType.REROUTE,
        description="Retry via an alternate healthy provider.",
        eligible_failure_types=frozenset({
            FailureType.TRANSIENT_PROVIDER_FAILURE, FailureType.PROVIDER_DEGRADED,
        }),
        requires_provider=True,
        estimated_cost=1.0,
    ),
    RecoveryActionType.NOTIFY: StrategyMetadata(
        strategy=RecoveryActionType.NOTIFY,
        description="Flag the case for merchant follow-up. No message is sent by this platform.",
        eligible_failure_types=frozenset({
            FailureType.EXPIRED_PAYMENT_METHOD, FailureType.INVALID_PAYMENT_METHOD,
            FailureType.INSUFFICIENT_FUNDS, FailureType.MISSED_COLLECTION,
        }),
        estimated_cost=4.0,
    ),
    RecoveryActionType.REQUEST_PAYMENT_UPDATE: StrategyMetadata(
        strategy=RecoveryActionType.REQUEST_PAYMENT_UPDATE,
        description="Ask the customer to update their payment method.",
        eligible_failure_types=frozenset({
            FailureType.EXPIRED_PAYMENT_METHOD, FailureType.INVALID_PAYMENT_METHOD,
        }),
        requires_customer_action=True,
        estimated_cost=4.0,
    ),
    RecoveryActionType.WAIT: StrategyMetadata(
        strategy=RecoveryActionType.WAIT,
        description="Hold before the next step — no action taken yet.",
        eligible_failure_types=frozenset(FailureType),
        supports_delay=True,
        estimated_cost=0.0,
    ),
    RecoveryActionType.ESCALATE: StrategyMetadata(
        strategy=RecoveryActionType.ESCALATE,
        description="Flag for human review — high value or repeated ambiguous failure.",
        eligible_failure_types=frozenset(FailureType),
        estimated_cost=8.0,
    ),
    RecoveryActionType.NO_ACTION: StrategyMetadata(
        strategy=RecoveryActionType.NO_ACTION,
        description="Do not intervene.",
        eligible_failure_types=frozenset(FailureType),
        estimated_cost=0.0,
    ),
}


@dataclass
class PolicyEvaluation:
    allowed_actions: list[RecoveryActionType]
    blocked: bool
    block_reason: str | None = None
    recommended_fallback_action: RecoveryActionType = RecoveryActionType.NO_ACTION
    economic_floor_applied: bool = False
    healthy_providers: list[str] = field(default_factory=list)


def evaluate(
    *,
    failure_type: FailureType,
    amount: float,
    attempt_number: int,
    reconciliation_open: bool,
    provider_health: dict[str, str],
    current_provider: str | None,
    economic_floor: float = DEFAULT_ECONOMIC_FLOOR,
) -> PolicyEvaluation:
    """The single authority on what's legally executable for a case. AI
    never sees this function; it only ever sees its output via
    RecoveryContext.allowed_actions."""

    # Gate 1 — ambiguous outcome protection. Mirrors the Java platform's
    # UNKNOWN/reconciliation-open hard block. Never allow an execute-money
    # action while it's still unclear whether an earlier attempt charged
    # the customer.
    if reconciliation_open:
        return PolicyEvaluation(
            allowed_actions=[RecoveryActionType.WAIT, RecoveryActionType.NO_ACTION],
            blocked=True,
            block_reason="RECONCILIATION_OPEN",
        )

    # Gate 2 — attempt cap.
    if attempt_number >= MAX_ATTEMPTS:
        return PolicyEvaluation(
            allowed_actions=[RecoveryActionType.NOTIFY, RecoveryActionType.NO_ACTION],
            blocked=True,
            block_reason="MAX_ATTEMPTS_REACHED",
            recommended_fallback_action=RecoveryActionType.NOTIFY,
        )

    # Gate 3 — business failure. No automated retry ever recovers these;
    # only customer-facing or human-facing actions make sense.
    if failure_type in BUSINESS_FAILURE_TYPES:
        allowed = [RecoveryActionType.NOTIFY, RecoveryActionType.NO_ACTION]
        return PolicyEvaluation(
            allowed_actions=allowed,
            blocked=False,
            recommended_fallback_action=RecoveryActionType.NOTIFY,
        )

    # Gate 4 — economic floor. Not worth an LLM call or an intervention
    # below this amount, regardless of failure type.
    if amount < economic_floor:
        return PolicyEvaluation(
            allowed_actions=[RecoveryActionType.NO_ACTION],
            blocked=True,
            block_reason="BELOW_ECONOMIC_FLOOR",
            economic_floor_applied=True,
        )

    # Gate 5 — payment-method problems: no retry/reroute makes sense
    # against a dead card; only customer action can fix it.
    if failure_type in (FailureType.EXPIRED_PAYMENT_METHOD, FailureType.INVALID_PAYMENT_METHOD):
        return PolicyEvaluation(
            allowed_actions=[RecoveryActionType.REQUEST_PAYMENT_UPDATE, RecoveryActionType.NOTIFY,
                              RecoveryActionType.NO_ACTION],
            blocked=False,
            recommended_fallback_action=RecoveryActionType.REQUEST_PAYMENT_UPDATE,
        )

    if failure_type == FailureType.MISSED_COLLECTION:
        return PolicyEvaluation(
            allowed_actions=[RecoveryActionType.RETRY_LATER, RecoveryActionType.NOTIFY,
                              RecoveryActionType.NO_ACTION],
            blocked=False,
            recommended_fallback_action=RecoveryActionType.RETRY_LATER,
        )

    # Gate 6 — provider health / circuit gate. Same-provider retry is not
    # offered into a provider that's currently down; reroute only if a
    # healthy alternate actually exists.
    healthy = [p for p, h in provider_health.items() if h == "HEALTHY"]
    current_healthy = current_provider is not None and provider_health.get(current_provider) == "HEALTHY"

    allowed: list[RecoveryActionType] = []
    if current_healthy:
        allowed.append(RecoveryActionType.RETRY_NOW)
    alternates = [p for p in healthy if p != current_provider]
    if alternates:
        allowed.append(RecoveryActionType.REROUTE)
    if not allowed:
        allowed.append(RecoveryActionType.NOTIFY)
    allowed.append(RecoveryActionType.NO_ACTION)

    fallback = RecoveryActionType.RETRY_NOW if current_healthy else (
        RecoveryActionType.REROUTE if alternates else RecoveryActionType.NOTIFY
    )

    return PolicyEvaluation(
        allowed_actions=allowed,
        blocked=False,
        recommended_fallback_action=fallback,
        healthy_providers=alternates,
    )


def validate_ai_action(action: RecoveryActionType, evaluation: PolicyEvaluation) -> RecoveryActionType:
    """The hard re-check. If the model's choice isn't in the authoritative
    allowed set computed *now* (not the set it was shown when the prompt
    was built), it never reaches execution — fall back to the
    deterministic recommendation instead."""
    if action in evaluation.allowed_actions:
        return action
    return evaluation.recommended_fallback_action
