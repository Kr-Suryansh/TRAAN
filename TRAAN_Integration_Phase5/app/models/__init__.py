"""
Pydantic request/response schemas (app/models package).

One file per resource group, mirroring app/routers/:
  auth.py      — device registration, authority login/refresh shapes
  sos.py       — SOSRequest, BatchUpload, BatchResponse, SOSStatusResponse
  incident.py  — Incident, IncidentDetail, IncidentPatch, DispatchRequest
  resource.py  — Resource, ResourceCreate, ResourcePatch
  common.py    — shared enums and small helpers
"""
