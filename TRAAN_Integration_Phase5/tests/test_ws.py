"""Tests for WebSocket Endpoint"""

import pytest
from httpx import AsyncClient
from fastapi.testclient import TestClient
from app.core.security import create_authority_jwt
from app.main import app

@pytest.fixture
def admin_token(setup_authority):
    return create_authority_jwt(setup_authority.id, "admin")

@pytest.fixture
def sync_client():
    # TestClient is synchronous but allows websocket testing easily
    with TestClient(app) as client:
        yield client

def test_ws_missing_token(sync_client: TestClient):
    # Depending on implementation, starlette testclient raises an exception on reject
    # or closes the websocket with code 1008
    from fastapi import WebSocketDisconnect
    try:
        with sync_client.websocket_connect("/ws/incidents") as ws:
            pass
        pytest.fail("Should have rejected without token")
    except WebSocketDisconnect as e:
        assert e.code == 1008

def test_ws_invalid_token(sync_client: TestClient):
    from fastapi import WebSocketDisconnect
    try:
        with sync_client.websocket_connect("/ws/incidents?token=invalid_token") as ws:
            pass
        pytest.fail("Should have rejected with invalid token")
    except WebSocketDisconnect as e:
        assert e.code == 1008

def test_ws_valid_connection(sync_client: TestClient, admin_token):
    # Should connect without raising an exception
    with sync_client.websocket_connect(f"/ws/incidents?token={admin_token}") as ws:
        pass # successfully connected and disconnected

from app.core.security import create_device_jwt
from tests.test_sos import make_batch_payload, make_sos_item
import uuid

def test_ws_incident_created(sync_client: TestClient, setup_authority):
    admin_token = create_authority_jwt(setup_authority.id, 'admin')
    device_token = create_device_jwt('gw-test-123')
    
    with sync_client.websocket_connect(f'/ws/incidents?token={admin_token}') as ws:
        payload = make_batch_payload([make_sos_item(str(uuid.uuid4()))])
        headers = {'Authorization': f'Bearer {device_token}'}
        
        response = sync_client.post('/api/v1/sos/batch', json=payload, headers=headers)
        assert response.status_code == 202
        
        data = ws.receive_json()
        assert data['event'] == 'incident_created'

def test_ws_malformed_token(sync_client: TestClient):
    from fastapi import WebSocketDisconnect
    try:
        with sync_client.websocket_connect("/ws/incidents?token=this.is.not.a.valid.jwt") as ws:
            pass
        pytest.fail("Should have rejected with malformed token")
    except WebSocketDisconnect as e:
        assert e.code == 1008

def test_ws_reconnect_simulation(sync_client: TestClient, admin_token):
    # Simulate the Component F reconnect behavior: connect, disconnect, connect again
    with sync_client.websocket_connect(f"/ws/incidents?token={admin_token}") as ws1:
        pass # successfully connected and disconnected (closed)
        
    with sync_client.websocket_connect(f"/ws/incidents?token={admin_token}") as ws2:
        pass # successfully re-connected
