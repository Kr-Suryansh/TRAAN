from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select

from app.db.engine import get_db
from app.db._models.device import Device
from app.db._models.authority import Authority
from app.models.auth import (
    DeviceRegisterRequest,
    DeviceRegisterResponse,
    AuthorityLoginRequest,
    AuthorityLoginResponse,
    AuthorityResponse,
    AuthorityRefreshRequest,
    AuthorityRefreshResponse,
)
from app.core.security import (
    create_device_jwt,
    create_authority_jwt,
    verify_password,
    verify_jwt,
)

router = APIRouter(prefix="/auth", tags=["auth"])


@router.post("/device/register", response_model=DeviceRegisterResponse)
async def register_device(
    request: DeviceRegisterRequest,
    db: AsyncSession = Depends(get_db),
):
    device = Device(device_model=request.device_model, app_version=request.app_version)
    db.add(device)
    await db.commit()
    await db.refresh(device)

    jwt_token = create_device_jwt(device.id)
    return DeviceRegisterResponse(device_id=device.id, device_jwt=jwt_token)


@router.post("/authority/login", response_model=AuthorityLoginResponse)
async def authority_login(
    request: AuthorityLoginRequest,
    db: AsyncSession = Depends(get_db),
):
    result = await db.execute(select(Authority).where(Authority.email == request.email))
    authority = result.scalars().first()

    if not authority or not verify_password(request.password, authority.hashed_password):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Incorrect email or password",
        )

    access_token = create_authority_jwt(authority.id, authority.role)
    return AuthorityLoginResponse(
        access_token=access_token,
        user=AuthorityResponse(
            user_id=authority.id,
            name=authority.name,
            role=authority.role,
            agency=authority.agency,
            email=authority.email,
        ),
    )


@router.post("/authority/refresh", response_model=AuthorityRefreshResponse)
async def authority_refresh(request: AuthorityRefreshRequest):
    try:
        payload = verify_jwt(request.access_token)
    except HTTPException:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid token",
        )

    if payload.get("type") != "authority":
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid token type",
        )

    authority_id = payload.get("sub")
    role = payload.get("role")

    if not authority_id or not role:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid token payload",
        )

    new_token = create_authority_jwt(authority_id, role)
    return AuthorityRefreshResponse(access_token=new_token)
