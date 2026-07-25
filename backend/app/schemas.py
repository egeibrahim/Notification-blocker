from __future__ import annotations

import datetime as dt

from pydantic import BaseModel, EmailStr, Field


class RegisterRequest(BaseModel):
    email: EmailStr
    password: str = Field(min_length=8, max_length=128)
    device_id: str | None = Field(default=None, max_length=64)


class LoginRequest(BaseModel):
    email: EmailStr
    password: str
    device_id: str | None = Field(default=None, max_length=64)


class RefreshRequest(BaseModel):
    refresh_token: str
    device_id: str | None = Field(default=None, max_length=64)


class LogoutRequest(BaseModel):
    refresh_token: str


class TokenResponse(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "bearer"


class ErrorResponse(BaseModel):
    detail: str


class MeResponse(BaseModel):
    user_id: int
    email: str
    created_at: dt.datetime
