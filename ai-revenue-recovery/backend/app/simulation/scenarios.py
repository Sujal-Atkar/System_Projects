"""
Produces a reproducible mixture of failure scenarios for batch evaluation.
Same seed -> same batch, every time, so the numbers in the dashboard's
batch analytics are re-runnable rather than a one-off lucky sample.
"""
from __future__ import annotations

import random
from dataclasses import dataclass

from app.models.recovery import FailureType, RevenueSourceType

# Roughly reflects the root-cause mix described in real-world payment
# failure literature: technical/provider issues are common but a minority
# next to funds/method problems, which is exactly why blind-retry-everything
# baselines perform worse than a policy that knows which failures to leave alone.
DEFAULT_MIX = {
    FailureType.TRANSIENT_PROVIDER_FAILURE: 0.22,
    FailureType.PROVIDER_DEGRADED: 0.10,
    FailureType.INSUFFICIENT_FUNDS: 0.28,
    FailureType.EXPIRED_PAYMENT_METHOD: 0.12,
    FailureType.INVALID_PAYMENT_METHOD: 0.08,
    FailureType.BUSINESS_DECLINE: 0.10,
    FailureType.MISSED_COLLECTION: 0.10,
}

PROVIDERS = ["MOCK_PROVIDER_A", "MOCK_PROVIDER_B"]


@dataclass(frozen=True)
class ScenarioCase:
    external_reference: str
    source_type: RevenueSourceType
    amount: float
    failure_type: FailureType
    provider: str | None


def generate_batch(size: int, seed: int = 42) -> list[ScenarioCase]:
    rng = random.Random(seed)
    failure_types = list(DEFAULT_MIX.keys())
    weights = list(DEFAULT_MIX.values())

    cases: list[ScenarioCase] = []
    for i in range(size):
        failure_type = rng.choices(failure_types, weights=weights, k=1)[0]

        # Amount distribution: mostly small-to-mid consumer payments, a
        # long tail of larger ones, and a deliberate share below the
        # economic floor so the "we don't chase tiny amounts" gate has
        # something real to prove in the batch numbers.
        roll = rng.random()
        if roll < 0.12:
            amount = round(rng.uniform(10, 95), 2)          # below floor
        elif roll < 0.75:
            amount = round(rng.uniform(150, 3000), 2)        # typical
        else:
            amount = round(rng.uniform(3000, 25000), 2)      # high value tail

        is_recurring = failure_type == FailureType.MISSED_COLLECTION
        provider = None if is_recurring else rng.choice(PROVIDERS)

        cases.append(ScenarioCase(
            external_reference=f"batch_{seed}_{i:04d}",
            source_type=RevenueSourceType.RECURRING_COLLECTION if is_recurring
            else RevenueSourceType.ONE_OFF_PAYMENT,
            amount=amount,
            failure_type=failure_type,
            provider=provider,
        ))
    return cases
