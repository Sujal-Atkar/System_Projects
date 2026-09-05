"""
Turns the AI's probability estimates into a bounded financial decision.
The LLM never decides "is this worth it" — this module does, using a
formula anyone can audit, not a model's confidence.
"""
from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class EconomicEvaluation:
    incremental_recovery_probability: float
    expected_incremental_value: float
    intervention_cost: float
    expected_net_value: float
    worth_intervening: bool


def evaluate_economics(
    *,
    revenue_at_risk: float,
    recovery_probability: float,
    organic_recovery_probability: float,
    intervention_cost: float,
) -> EconomicEvaluation:
    """
    incremental_recovery_probability = P(recover | intervene) - P(recover | do nothing)
    expected_incremental_value       = incremental_probability * revenue_at_risk
    expected_net_value               = expected_incremental_value - intervention_cost

    If expected_net_value <= 0, the caller (RecoveryService) must produce
    NO_ACTION regardless of what the model recommended — this function
    reports the number, it does not get overridden by confidence or
    reasoning text.
    """
    incremental = max(0.0, recovery_probability - organic_recovery_probability)
    expected_incremental_value = incremental * revenue_at_risk
    expected_net_value = expected_incremental_value - intervention_cost

    return EconomicEvaluation(
        incremental_recovery_probability=round(incremental, 4),
        expected_incremental_value=round(expected_incremental_value, 2),
        intervention_cost=round(intervention_cost, 2),
        expected_net_value=round(expected_net_value, 2),
        worth_intervening=expected_net_value > 0,
    )
