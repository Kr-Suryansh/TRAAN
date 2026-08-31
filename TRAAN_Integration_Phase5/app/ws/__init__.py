"""
app/ws — WebSocket connection manager.

PHASE 5 PLACEHOLDER — not wired in Phase 1.

Will implement:
  manager.py   — ConnectionManager class
    - connect(websocket, authority_id)
    - disconnect(websocket)
    - broadcast(event: str, data: dict)

WebSocket endpoint: WS /ws/incidents?token=<authority_jwt>
Channel: incidents:updates
Events pushed:
  - incident_created
  - incident_updated
  - incident_dispatched
  - resource_updated
  - situation_brief_updated
"""
