from datetime import datetime, timedelta
from typing import Any

from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from jose import JWTError, jwt
from passlib.context import CryptContext

from app.config import settings

pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")
security_bearer = HTTPBearer()


def hash_password(plain: str) -> str:
    return pwd_context.hash(plain)


def verify_password(plain: str, hashed: str) -> bool:
    return pwd_context.verify(plain, hashed)


def create_device_jwt(device_id: str) -> str:
    expire = datetime.utcnow() + timedelta(minutes=settings.DEVICE_JWT_EXPIRE_MINUTES)
    to_encode = {"sub": device_id, "exp": expire, "type": "device"}
    return jwt.encode(to_encode, settings.JWT_SECRET_KEY, algorithm=settings.JWT_ALGORITHM)


def create_authority_jwt(authority_id: str, role: str) -> str:
    expire = datetime.utcnow() + timedelta(minutes=settings.AUTHORITY_JWT_EXPIRE_MINUTES)
    to_encode = {"sub": authority_id, "role": role, "exp": expire, "type": "authority"}
    return jwt.encode(to_encode, settings.JWT_SECRET_KEY, algorithm=settings.JWT_ALGORITHM)


def create_authority_refresh_jwt(authority_id: str, role: str) -> str:
    # 7 days expiration for refresh tokens
    expire = datetime.utcnow() + timedelta(days=7)
    to_encode = {"sub": authority_id, "role": role, "exp": expire, "type": "authority_refresh"}
    return jwt.encode(to_encode, settings.JWT_SECRET_KEY, algorithm=settings.JWT_ALGORITHM)


def verify_jwt(token: str) -> dict[str, Any]:
    try:
        payload = jwt.decode(token, settings.JWT_SECRET_KEY, algorithms=[settings.JWT_ALGORITHM])
        return payload
    except JWTError:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Could not validate credentials",
            headers={"WWW-Authenticate": "Bearer"},
        )


def require_device_jwt(credentials: HTTPAuthorizationCredentials = Depends(security_bearer)) -> str:
    payload = verify_jwt(credentials.credentials)
    if payload.get("type") != "device":
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid token type",
            headers={"WWW-Authenticate": "Bearer"},
        )
    return payload.get("sub")


def require_authority_jwt(credentials: HTTPAuthorizationCredentials = Depends(security_bearer)) -> dict[str, Any]:
    payload = verify_jwt(credentials.credentials)
    if payload.get("type") != "authority":
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid token type",
            headers={"WWW-Authenticate": "Bearer"},
        )
    return {"id": payload.get("sub"), "role": payload.get("role")}
