import httpx
import pytest

from app.ai.gemini_client import GeminiRecoveryClient
from app.config import get_settings
from app.models.recovery import FailureType, RecoveryActionType
from app.schemas.ai import RecoveryContext


def _context(**overrides) -> RecoveryContext:
    base = dict(
        revenue_source="ONE_OFF_PAYMENT",
        external_reference="sim_test",
        amount=1000.0,
        currency="INR",
        failure_type=FailureType.TRANSIENT_PROVIDER_FAILURE,
        failure_reason="Gateway timeout",
        provider="MOCK_PROVIDER_A",
        provider_health="HEALTHY",
        attempt_number=1,
        allowed_actions=[RecoveryActionType.RETRY_NOW, RecoveryActionType.REROUTE, RecoveryActionType.NO_ACTION],
        estimated_intervention_cost=1.0,
    )
    base.update(overrides)
    return RecoveryContext(**base)


@pytest.fixture(autouse=True)
def clear_settings_cache():
    get_settings.cache_clear()
    yield
    get_settings.cache_clear()


async def test_missing_api_key_fails_cleanly(monkeypatch):
    monkeypatch.delenv("GEMINI_API_KEY", raising=False)
    client = GeminiRecoveryClient()
    result = await client.get_decision(_context())
    assert result.succeeded is False
    assert result.failure_reason == "MISSING_API_KEY"
    assert result.output is None


async def test_timeout_fails_cleanly(monkeypatch):
    monkeypatch.setenv("GEMINI_API_KEY", "fake-key-for-test")
    get_settings.cache_clear()

    async def _raise_timeout(*args, **kwargs):
        raise httpx.TimeoutException("simulated timeout")

    monkeypatch.setattr(httpx.AsyncClient, "post", _raise_timeout)

    client = GeminiRecoveryClient()
    result = await client.get_decision(_context())
    assert result.succeeded is False
    assert result.failure_reason == "TIMEOUT"


async def test_malformed_json_response_fails_cleanly(monkeypatch):
    monkeypatch.setenv("GEMINI_API_KEY", "fake-key-for-test")
    get_settings.cache_clear()

    class _FakeResponse:
        status_code = 200

        def json(self):
            broken_text = "not valid json at all"
            return {"candidates": [{"content": {"parts": [{"text": broken_text}]}}]}

        text = "not valid json"

    async def _fake_post(*args, **kwargs):
        return _FakeResponse()

    monkeypatch.setattr(httpx.AsyncClient, "post", _fake_post)

    client = GeminiRecoveryClient()
    result = await client.get_decision(_context())
    assert result.succeeded is False
    assert result.failure_reason == "MALFORMED_RESPONSE"


async def test_disallowed_action_is_rejected(monkeypatch):
    monkeypatch.setenv("GEMINI_API_KEY", "fake-key-for-test")
    get_settings.cache_clear()

    payload = {
        "diagnosis": "insufficient funds",
        "recoveryProbability": 0.5,
        "organicRecoveryProbability": 0.1,
        "recommendedAction": "ESCALATE",  # not in this context's allowed_actions
        "timing": "IMMEDIATE",
        "reasoning": "test",
        "confidence": 0.8,
        "plan": [],
    }

    class _FakeResponse:
        status_code = 200

        def json(self):
            import json
            return {"candidates": [{"content": {"parts": [{"text": json.dumps(payload)}]}}]}

    async def _fake_post(*args, **kwargs):
        return _FakeResponse()

    monkeypatch.setattr(httpx.AsyncClient, "post", _fake_post)

    client = GeminiRecoveryClient()
    result = await client.get_decision(_context())
    assert result.succeeded is False
    assert result.failure_reason == "DISALLOWED_ACTION"


async def test_valid_response_succeeds(monkeypatch):
    monkeypatch.setenv("GEMINI_API_KEY", "fake-key-for-test")
    get_settings.cache_clear()

    payload = {
        "diagnosis": "transient provider failure",
        "recoveryProbability": 0.72,
        "organicRecoveryProbability": 0.18,
        "recommendedAction": "REROUTE",
        "timing": "IMMEDIATE",
        "targetProvider": "MOCK_PROVIDER_B",
        "reasoning": "Provider A degraded, Provider B healthy.",
        "confidence": 0.91,
        "plan": [],
    }

    class _FakeResponse:
        status_code = 200

        def json(self):
            import json
            return {"candidates": [{"content": {"parts": [{"text": json.dumps(payload)}]}}]}

    async def _fake_post(*args, **kwargs):
        return _FakeResponse()

    monkeypatch.setattr(httpx.AsyncClient, "post", _fake_post)

    client = GeminiRecoveryClient()
    result = await client.get_decision(_context())
    assert result.succeeded is True
    assert result.output.recommended_action == RecoveryActionType.REROUTE
    assert result.output.confidence == 0.91


async def test_contextual_assistant_missing_key_falls_back(monkeypatch):
    monkeypatch.delenv("GEMINI_API_KEY", raising=False)
    get_settings.cache_clear()
    result = await GeminiRecoveryClient().explain_case(
        question="Why did you choose Provider B?",
        context={"case_id": "case-1", "status": "PLAN_PROPOSED", "amount": 7500},
    )
    assert result.succeeded is False
    assert result.failure_reason == "MISSING_API_KEY"
    assert result.answer is None


async def test_contextual_assistant_valid_response_is_bounded(monkeypatch):
    monkeypatch.setenv("GEMINI_API_KEY", "fake-key-for-test")
    get_settings.cache_clear()

    class _FakeResponse:
        status_code = 200

        def json(self):
            import json
            return {"candidates": [{"content": {"parts": [{"text": json.dumps({"answer": "Provider B was selected because the verified case context shows the current recovery recommendation."})}]}}]}

    async def _fake_post(*args, **kwargs):
        assert kwargs["params"] == {"key": "fake-key-for-test"}
        assert "fake-key-for-test" not in kwargs["json"]["contents"][0]["parts"][0]["text"]
        return _FakeResponse()

    monkeypatch.setattr(httpx.AsyncClient, "post", _fake_post)
    result = await GeminiRecoveryClient().explain_case(
        question="Why did you choose Provider B?",
        context={"case_id": "case-1", "status": "PLAN_PROPOSED", "amount": 7500},
    )
    assert result.succeeded is True
    assert result.answer.startswith("Provider B")
