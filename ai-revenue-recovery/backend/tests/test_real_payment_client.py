import pytest
import respx
import httpx
from datetime import datetime, timezone
from app.clients.real_payment_system_client import RealPaymentSystemClient
from app.models.recovery import RecoveryActionType

@pytest.fixture
def settings_mock(monkeypatch):
    monkeypatch.setenv("JAVA_PAYMENT_API_BASE_URL", "http://localhost:8081")
    monkeypatch.setenv("JAVA_PAYMENT_API_KEY", "dev-secret")
    monkeypatch.setenv("JAVA_PAYMENT_API_TIMEOUT", "2")
    return RealPaymentSystemClient()

@pytest.mark.asyncio
@respx.mock
async def test_get_recovery_context_success(settings_mock):
    respx.get("http://localhost:8081/api/internal/payments/test-ref-123/recovery-context").mock(
        return_value=httpx.Response(200, json={
            "paymentId": "test-ref-123",
            "amount": 500.0,
            "currency": "INR",
            "failureType": "INSUFFICIENT_FUNDS",
            "failureReason": "Issuer declined",
            "provider": "MOCK_PROVIDER_A",
            "providerHealth": "HEALTHY",
            "attemptNumber": 1,
            "previousAttempts": 0,
            "reconciliationOpen": False
        })
    )

    ctx = await settings_mock.get_recovery_context("test-ref-123")
    
    assert ctx["external_reference"] == "test-ref-123"
    assert ctx["amount"] == 500.0
    assert ctx["failure_type"] == "INSUFFICIENT_FUNDS"

@pytest.mark.asyncio
@respx.mock
async def test_get_recovery_context_not_found(settings_mock):
    respx.get("http://localhost:8081/api/internal/payments/not-found/recovery-context").mock(
        return_value=httpx.Response(404)
    )

    with pytest.raises(KeyError, match="No known case for reference not-found"):
        await settings_mock.get_recovery_context("not-found")

@pytest.mark.asyncio
@respx.mock
async def test_execute_recovery_action_success(settings_mock):
    route = respx.post("http://localhost:8081/api/internal/payments/test-ref-123/recovery-execute").mock(
        return_value=httpx.Response(200, json={
            "success": True,
            "providerUsed": "MOCK_PROVIDER_B",
            "outcomeCode": "SUCCESS",
            "recoveredAmount": 500.0
        })
    )

    result = await settings_mock.execute_recovery_action(
        "test-ref-123",
        RecoveryActionType.REROUTE,
        "stable-idemp-123",
        "MOCK_PROVIDER_B"
    )

    assert result.success is True
    assert result.outcome_code == "SUCCESS"
    assert result.recovered_amount == 500.0
    
    # Assert Idempotency-Key propagation
    request = route.calls.last.request
    assert request.headers["Idempotency-Key"] == "stable-idemp-123"
    assert request.headers["X-Internal-Secret"] == "dev-secret"

@pytest.mark.asyncio
@respx.mock
async def test_execute_recovery_action_stable_idempotency_key(settings_mock):
    # Verify that calling execute twice with the same idempotency key sends that exact string twice.
    route = respx.post("http://localhost:8081/api/internal/payments/test-ref-123/recovery-execute").mock(
        return_value=httpx.Response(200, json={"success": True})
    )

    await settings_mock.execute_recovery_action("test-ref-123", RecoveryActionType.RETRY_NOW, "same-key-999")
    await settings_mock.execute_recovery_action("test-ref-123", RecoveryActionType.RETRY_NOW, "same-key-999")
    
    assert len(route.calls) == 2
    assert route.calls[0].request.headers["Idempotency-Key"] == "same-key-999"
    assert route.calls[1].request.headers["Idempotency-Key"] == "same-key-999"

@pytest.mark.asyncio
@respx.mock
async def test_execute_recovery_action_conflict_409(settings_mock):
    respx.post("http://localhost:8081/api/internal/payments/test-ref-123/recovery-execute").mock(
        return_value=httpx.Response(409)
    )

    result = await settings_mock.execute_recovery_action("test-ref-123", RecoveryActionType.RETRY_NOW, "idemp-key")

    assert result.success is False
    assert result.outcome_code == "CONFLICT"

@pytest.mark.asyncio
@respx.mock
async def test_execute_recovery_action_not_found_404(settings_mock):
    respx.post("http://localhost:8081/api/internal/payments/test-ref-123/recovery-execute").mock(
        return_value=httpx.Response(404)
    )

    result = await settings_mock.execute_recovery_action("test-ref-123", RecoveryActionType.RETRY_NOW, "idemp-key")

    assert result.success is False
    assert result.outcome_code == "PAYMENT_NOT_FOUND"

@pytest.mark.asyncio
@respx.mock
async def test_execute_recovery_action_timeout(settings_mock):
    respx.post("http://localhost:8081/api/internal/payments/test-ref-123/recovery-execute").mock(
        side_effect=httpx.TimeoutException("Timeout")
    )

    result = await settings_mock.execute_recovery_action("test-ref-123", RecoveryActionType.RETRY_NOW, "idemp-key")

    assert result.success is False
    assert result.outcome_code == "TIMEOUT"

@pytest.mark.asyncio
@respx.mock
async def test_execute_recovery_action_network_error(settings_mock):
    respx.post("http://localhost:8081/api/internal/payments/test-ref-123/recovery-execute").mock(
        side_effect=httpx.NetworkError("Network error")
    )

    result = await settings_mock.execute_recovery_action("test-ref-123", RecoveryActionType.RETRY_NOW, "idemp-key")

    assert result.success is False
    assert result.outcome_code == "NETWORK_ERROR"

