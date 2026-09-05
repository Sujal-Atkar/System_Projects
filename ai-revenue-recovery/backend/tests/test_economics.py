from app.services.economics import evaluate_economics


def test_worth_intervening_when_net_value_positive():
    result = evaluate_economics(
        revenue_at_risk=4999.0,
        recovery_probability=0.72,
        organic_recovery_probability=0.18,
        intervention_cost=4.0,
    )
    assert result.incremental_recovery_probability == 0.54
    assert result.expected_incremental_value == round(0.54 * 4999.0, 2)
    assert result.worth_intervening is True


def test_not_worth_intervening_when_cost_exceeds_value():
    result = evaluate_economics(
        revenue_at_risk=50.0,
        recovery_probability=0.4,
        organic_recovery_probability=0.35,
        intervention_cost=8.0,
    )
    # incremental = 0.05, expected value = 2.5, cost = 8 -> net negative
    assert result.worth_intervening is False
    assert result.expected_net_value < 0


def test_negative_incremental_probability_is_floored_at_zero():
    # AI/organic probabilities should never produce a negative incremental
    # probability even if organic > recovery (model uncertainty).
    result = evaluate_economics(
        revenue_at_risk=1000.0,
        recovery_probability=0.2,
        organic_recovery_probability=0.5,
        intervention_cost=1.0,
    )
    assert result.incremental_recovery_probability == 0.0
    assert result.worth_intervening is False


def test_zero_net_value_is_not_worth_intervening():
    result = evaluate_economics(
        revenue_at_risk=100.0,
        recovery_probability=0.11,
        organic_recovery_probability=0.10,
        intervention_cost=1.0,
    )
    # incremental = 0.01, expected value = 1.0, cost = 1.0 -> net exactly 0
    assert result.expected_net_value == 0.0
    assert result.worth_intervening is False
