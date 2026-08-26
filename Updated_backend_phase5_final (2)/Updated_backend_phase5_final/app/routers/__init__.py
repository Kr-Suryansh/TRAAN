"""
app/routers package.

One file per resource group, matching the contract exactly:
  auth.py       — POST /auth/device/register, /auth/authority/login, /auth/authority/refresh
  sos.py        — POST /sos/batch, GET /sos/{uuid}/status
  incidents.py  — GET/PATCH /incidents, POST /incidents/{id}/dispatch, etc.
  resources.py  — GET/POST/PATCH /resources
  optimizer.py  — GET/POST /situation-brief, POST /optimize/allocate
  misc.py       — GET /health, GET /stats/summary
"""
