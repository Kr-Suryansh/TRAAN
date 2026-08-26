from pydantic import BaseModel

from app.models.common import AuthorityRole


class DeviceRegisterRequest(BaseModel):
    device_model: str
    app_version: str


class DeviceRegisterResponse(BaseModel):
    device_id: str
    device_jwt: str


class AuthorityLoginRequest(BaseModel):
    email: str
    password: str


class AuthorityResponse(BaseModel):
    user_id: str
    name: str
    role: AuthorityRole
    agency: str
    email: str


class AuthorityLoginResponse(BaseModel):
    access_token: str
    refresh_token: str
    user: AuthorityResponse


class AuthorityRefreshRequest(BaseModel):
    refresh_token: str


class AuthorityRefreshResponse(BaseModel):
    access_token: str
    refresh_token: str
