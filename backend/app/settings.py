from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    app_name: str = "notifilter-backend"
    environment: str = "dev"

    database_url: str = "postgresql+psycopg://postgres:postgres@localhost:5432/notifilter"

    jwt_secret: str = "change-me"
    jwt_access_ttl_seconds: int = 900
    jwt_refresh_ttl_seconds: int = 60 * 60 * 24 * 30

    stripe_secret_key: str = ""
    stripe_webhook_secret: str = ""


settings = Settings()
