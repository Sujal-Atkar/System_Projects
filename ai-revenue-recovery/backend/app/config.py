from __future__ import annotations

from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    database_url: str = "sqlite:///./revenue_recovery.db"

    gemini_api_key: str = ""
    gemini_model: str = "gemini-2.0-flash"
    gemini_timeout_seconds: float = 5.0

    economic_floor: float = 100.0

    # Placeholder for the future real Java integration — unused by
    # MockPaymentSystemClient, read by RealPaymentSystemClient once it exists.
    java_payment_api_base_url: str = "http://localhost:8081"
    java_payment_api_timeout: int = 5
    use_real_payment_system: bool = False
    java_payment_api_key: str = ""
    java_payment_api_secret: str = ""


@lru_cache
def get_settings() -> Settings:
    return Settings()

