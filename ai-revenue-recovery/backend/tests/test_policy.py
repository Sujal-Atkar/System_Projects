from app.models.recovery import FailureType, RecoveryActionType
from app.policies import recovery_policy as policy


def test_reconciliation_open_blocks_everything_except_wait():
    result = policy.evaluate(
        failure_type=FailureType.TRANSIENT_PROVIDER_FAILURE,
        amount=1000.0,
        attempt_number=1,
        reconciliation_open=True,
        provider_health={"MOCK_PROVIDER_A": "HEALTHY"},
        current_provider="MOCK_PROVIDER_A",
    )
    assert result.blocked is True
    assert result.block_reason == "RECONCILIATION_OPEN"
    assert RecoveryActionType.RETRY_NOW not in result.allowed_actions
    assert RecoveryActionType.REROUTE not in result.allowed_actions


def test_max_attempts_blocks_retry():
    result = policy.evaluate(
        failure_type=FailureType.TRANSIENT_PROVIDER_FAILURE,
        amount=1000.0,
        attempt_number=3,
        reconciliation_open=False,
        provider_health={"MOCK_PROVIDER_A": "HEALTHY"},
        current_provider="MOCK_PROVIDER_A",
    )
    assert result.blocked is True
    assert result.block_reason == "MAX_ATTEMPTS_REACHED"
    assert RecoveryActionType.RETRY_NOW not in result.allowed_actions


def test_business_failure_never_offers_retry_or_reroute():
    for failure in (FailureType.INSUFFICIENT_FUNDS, FailureType.BUSINESS_DECLINE):
        result = policy.evaluate(
            failure_type=failure,
            amount=1000.0,
            attempt_number=1,
            reconciliation_open=False,
            provider_health={"MOCK_PROVIDER_A": "HEALTHY", "MOCK_PROVIDER_B": "HEALTHY"},
            current_provider="MOCK_PROVIDER_A",
        )
        assert RecoveryActionType.RETRY_NOW not in result.allowed_actions
        assert RecoveryActionType.REROUTE not in result.allowed_actions
        assert RecoveryActionType.NOTIFY in result.allowed_actions


def test_below_economic_floor_forces_no_action_only():
    result = policy.evaluate(
        failure_type=FailureType.TRANSIENT_PROVIDER_FAILURE,
        amount=50.0,
        attempt_number=1,
        reconciliation_open=False,
        provider_health={"MOCK_PROVIDER_A": "HEALTHY"},
        current_provider="MOCK_PROVIDER_A",
        economic_floor=100.0,
    )
    assert result.blocked is True
    assert result.allowed_actions == [RecoveryActionType.NO_ACTION]
    assert result.economic_floor_applied is True


def test_reroute_only_offered_when_healthy_alternate_exists():
    result = policy.evaluate(
        failure_type=FailureType.PROVIDER_DEGRADED,
        amount=1000.0,
        attempt_number=1,
        reconciliation_open=False,
        provider_health={"MOCK_PROVIDER_A": "DOWN", "MOCK_PROVIDER_B": "HEALTHY"},
        current_provider="MOCK_PROVIDER_A",
    )
    assert RecoveryActionType.RETRY_NOW not in result.allowed_actions
    assert RecoveryActionType.REROUTE in result.allowed_actions
    assert result.recommended_fallback_action == RecoveryActionType.REROUTE


def test_no_healthy_provider_falls_back_to_notify():
    result = policy.evaluate(
        failure_type=FailureType.TRANSIENT_PROVIDER_FAILURE,
        amount=1000.0,
        attempt_number=1,
        reconciliation_open=False,
        provider_health={"MOCK_PROVIDER_A": "DOWN", "MOCK_PROVIDER_B": "DOWN"},
        current_provider="MOCK_PROVIDER_A",
    )
    assert result.allowed_actions == [RecoveryActionType.NOTIFY, RecoveryActionType.NO_ACTION]
    assert result.recommended_fallback_action == RecoveryActionType.NOTIFY


def test_validate_ai_action_rejects_disallowed_choice():
    evaluation = policy.evaluate(
        failure_type=FailureType.INSUFFICIENT_FUNDS,
        amount=1000.0,
        attempt_number=1,
        reconciliation_open=False,
        provider_health={"MOCK_PROVIDER_A": "HEALTHY"},
        current_provider="MOCK_PROVIDER_A",
    )
    # Model hallucinates RETRY_NOW despite it not being allowed for a
    # business failure -> must be rejected and replaced with the fallback.
    validated = policy.validate_ai_action(RecoveryActionType.RETRY_NOW, evaluation)
    assert validated == evaluation.recommended_fallback_action
    assert validated != RecoveryActionType.RETRY_NOW


def test_validate_ai_action_accepts_allowed_choice():
    evaluation = policy.evaluate(
        failure_type=FailureType.TRANSIENT_PROVIDER_FAILURE,
        amount=1000.0,
        attempt_number=1,
        reconciliation_open=False,
        provider_health={"MOCK_PROVIDER_A": "HEALTHY"},
        current_provider="MOCK_PROVIDER_A",
    )
    validated = policy.validate_ai_action(RecoveryActionType.RETRY_NOW, evaluation)
    assert validated == RecoveryActionType.RETRY_NOW
