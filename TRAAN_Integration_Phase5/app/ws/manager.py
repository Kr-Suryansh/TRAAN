import json
from typing import Dict, Any, List
from fastapi import APIRouter, WebSocket, WebSocketDisconnect, Query
from pydantic import BaseModel
from datetime import datetime
import logging

from app.core.security import verify_jwt

logger = logging.getLogger(__name__)
router = APIRouter()

class ConnectionManager:
    def __init__(self):
        self.active_connections: List[WebSocket] = []

    async def connect(self, websocket: WebSocket):
        await websocket.accept()
        self.active_connections.append(websocket)

    def disconnect(self, websocket: WebSocket):
        if websocket in self.active_connections:
            self.active_connections.remove(websocket)

    async def broadcast_event(self, event: str, data: Any):
        """
        Broadcasts an event matching the exact Day 1 contract.
        """
        if isinstance(data, BaseModel):
            data_dict = data.model_dump(mode='json')
        else:
            data_dict = data
            
        message = {
            "event": event,
            "data": data_dict
        }
        
        for connection in list(self.active_connections):
            try:
                await connection.send_json(message)
            except WebSocketDisconnect:
                self.disconnect(connection)
            except Exception as e:
                logger.error(f"WebSocket send error: {e}")

manager = ConnectionManager()


@router.websocket("/ws/incidents")
async def websocket_incidents(websocket: WebSocket, token: str = Query(None)):
    if not token:
        await websocket.close(code=1008, reason="Missing token")
        return
        
    try:
        payload = verify_jwt(token)
        if payload.get("type") != "authority":
            await websocket.close(code=1008, reason="Invalid token type")
            return
    except Exception as e:
        await websocket.close(code=1008, reason=str(e))
        return

    await manager.connect(websocket)
    try:
        while True:
            # We don't expect messages from the dashboard, but we must keep the connection open
            await websocket.receive_text()
    except WebSocketDisconnect:
        manager.disconnect(websocket)
