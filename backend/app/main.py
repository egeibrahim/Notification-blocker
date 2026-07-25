from fastapi import FastAPI

from .settings import settings
from .routers.health import router as health_router
from .routers.auth import router as auth_router


def create_app() -> FastAPI:
    app = FastAPI(title=settings.app_name)
    app.include_router(health_router)
    app.include_router(auth_router)
    return app


app = create_app()
