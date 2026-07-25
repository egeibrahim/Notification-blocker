from __future__ import annotations

import datetime as dt

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.db import get_db
from app.models import RefreshToken, User
from app.schemas import (
    LoginRequest,
    LogoutRequest,
    RegisterRequest,
    RefreshRequest,
    TokenResponse,
)
from app.security import create_access_token, hash_password, hash_refresh_token, verify_password
from app.settings import settings

router = APIRouter(prefix="/auth", tags=["auth"])


def _issue_tokens(db: Session, *, user: User, device_id: str | None) -> TokenResponse:
    raw_refresh = RefreshToken.generate_raw_token()
    refresh_hash = hash_refresh_token(raw_refresh)
    now = dt.datetime.now(dt.timezone.utc)
    expires = now + dt.timedelta(seconds=settings.jwt_refresh_ttl_seconds)

    rt = RefreshToken(
        user_id=user.id,
        token_hash=refresh_hash,
        device_id=device_id,
        expires_at=expires,
    )
    db.add(rt)
    db.commit()

    access = create_access_token(subject=str(user.id), extra={"email": user.email})
    return TokenResponse(access_token=access, refresh_token=raw_refresh)


@router.post("/register", response_model=TokenResponse)
def register(payload: RegisterRequest, db: Session = Depends(get_db)):
    existing = db.query(User).filter(User.email == payload.email.lower()).first()
    if existing:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Email already registered")

    user = User(email=payload.email.lower(), password_hash=hash_password(payload.password))
    db.add(user)
    db.commit()
    db.refresh(user)

    return _issue_tokens(db, user=user, device_id=payload.device_id)


@router.post("/login", response_model=TokenResponse)
def login(payload: LoginRequest, db: Session = Depends(get_db)):
    user = db.query(User).filter(User.email == payload.email.lower()).first()
    if not user or not verify_password(payload.password, user.password_hash):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid credentials")

    return _issue_tokens(db, user=user, device_id=payload.device_id)


@router.post("/refresh", response_model=TokenResponse)
def refresh(payload: RefreshRequest, db: Session = Depends(get_db)):
    token_hash = hash_refresh_token(payload.refresh_token)
    rt = db.query(RefreshToken).filter(RefreshToken.token_hash == token_hash).first()
    if not rt:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid refresh token")

    now = dt.datetime.now(dt.timezone.utc)
    if rt.revoked_at is not None or rt.expires_at <= now:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Refresh token expired")

    # optional device binding (soft): if provided and doesn't match, reject
    if payload.device_id and rt.device_id and payload.device_id != rt.device_id:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Device mismatch")

    user = db.query(User).filter(User.id == rt.user_id).first()
    if not user:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="User not found")

    # rotate refresh
    new_raw = RefreshToken.generate_raw_token()
    new_hash = hash_refresh_token(new_raw)
    rt.revoked_at = now
    rt.replaced_by_token_hash = new_hash

    new_rt = RefreshToken(
        user_id=user.id,
        token_hash=new_hash,
        device_id=rt.device_id,
        expires_at=now + dt.timedelta(seconds=settings.jwt_refresh_ttl_seconds),
    )
    db.add(new_rt)
    db.commit()

    access = create_access_token(subject=str(user.id), extra={"email": user.email})
    return TokenResponse(access_token=access, refresh_token=new_raw)


@router.post("/logout")
def logout(payload: LogoutRequest, db: Session = Depends(get_db)):
    token_hash = hash_refresh_token(payload.refresh_token)
    rt = db.query(RefreshToken).filter(RefreshToken.token_hash == token_hash).first()
    if not rt:
        return {"status": "ok"}

    if rt.revoked_at is None:
        rt.revoked_at = dt.datetime.now(dt.timezone.utc)
        db.commit()

    return {"status": "ok"}
