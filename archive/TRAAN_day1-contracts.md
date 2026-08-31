# Day 1 — API Contracts & Repo Setup
Disaster Response Coordination Platform · SIH 2026

This is the single source of truth for schemas and endpoints. If anyone needs to change a field once building starts, edit this file first, flag it in the team channel, then update code — don't let repos silently drift apart.

---

## 1. Data Schemas

### 1.1 UserMedicalProfile
Filled once, before any emergency, during app setup — never typed under duress. Lives in the local `:data` module (Room), not synced anywhere until an SOS is triggered.

```json
{
  "name": "string | null",
  "age": "integer | null",
  "blood_type": "string | null",
  "medical_conditions": ["string", "... (e.g. diabetic, cardiac, mobility-impaired)"],
  "medications": ["string", "..."],
  "allergies": ["string", "..."],
  "emergency_contact_name": "string | null",
  "emergency_contact_number": "string | null"
}
```

---

### 1.2 SOSRequest
Created on-device, carried through the relay mesh unchanged, and finally POSTed to the backend inside a `GatewayUploadBatch`. **Text-only — no photos or voice notes.** This keeps every hop cheap on both battery and storage (see the team discussion notes at the bottom of this file for why).

```json
{
  "uuid": "string (UUIDv4, client-generated, primary identifier)",
  "device_id": "string (installation ID of originating phone)",
  "created_at": "ISO8601 (device local clock)",
  "location": {
    "lat": "float",
    "lng": "float",
    "accuracy_m": "float | null"
  },
  "is_quick_sos": "bool (true = sent via single tap, no typing required)",
  "emergency_type": "enum: medical | trapped | structural_collapse | flood_rescue | fire | missing_person | unspecified",
  "severity_hint": "enum: critical | high | medium | low | null (self-reported, omitted on quick SOS)",
  "people_count": "integer | null",
  "medical_snapshot": "UserMedicalProfile | null (auto-attached from local profile, not re-typed)",
  "custom_message": "string | null (optional free text, only if the user has time/ability to add it)",
  "contact_number": "string | null",
  "relay_hop_count": "integer (incremented at each hop)",
  "last_relayed_at": "ISO8601 (updated at each hop, used for TTL)",
  "status": "enum: pending_local | in_relay | uploaded (device-side only)"
}
```

**Design intent:** tapping the SOS button alone is a complete, valid emergency signal — `is_quick_sos: true`, `medical_snapshot` auto-filled from the profile, everything else null. Anyone with time/composure can add `custom_message` or set `emergency_type`/`severity_hint` explicitly, but nothing beyond the tap is ever required.

**Notes for the mesh team (A+B):** a text-only payload like this is roughly 0.5–2KB even fully populated. At BLE's effective throughput, that's a near-instant transfer — bandwidth is not the constraint here. Battery from *continuous radio activity* (advertising/scanning) is the real cost — see the team notes below.

---

### 1.3 RelayManifest
Exchanged when two phones connect, before any SOS data moves — this is the "what do you already have" handshake that makes epidemic routing efficient instead of wasteful.

```json
{
  "device_id": "string",
  "known_uuids": ["string", "..."],
  "timestamp": "ISO8601"
}
```

**Flow:** A connects to B → both send `RelayManifest` → each computes the diff → each sends only the `SOSRequest[]` the other is missing.

**Backend storage note:** the backend persists incoming reports as `SOSReport` records — the same fields as `SOSRequest` above, plus three backend-only bookkeeping fields: `received_at` (ISO8601), `cluster_id` (string | null, set once clustering assigns it to an `Incident`), and `received_via_gateway_device_id` (string). This is stored permanently in PostgreSQL, not deleted after processing — authorities may need to reference the original raw report later, and re-clustering may need to reprocess it.

---

### 1.4 GatewayUploadBatch
What a gateway device (any phone with internet — victim's own phone, responder, volunteer) POSTs to the backend.

```json
{
  "gateway_device_id": "string",
  "gateway_location": { "lat": "float", "lng": "float" },
  "uploaded_at": "ISO8601",
  "sos_batch": ["SOSRequest", "..."]
}
```

---

### 1.5 Incident
The backend's post-dedup, post-clustering representation. **This is what the dashboard actually reads** — never the raw SOS reports directly.

```json
{
  "incident_id": "string (server UUID)",
  "cluster_id": "string",
  "source_sos_uuids": ["string", "..."],
  "location": { "lat": "float", "lng": "float" },
  "area_name": "string (reverse-geocoded)",
  "emergency_types": ["string", "... (aggregated from contributing reports)"],
  "severity": "enum: critical | high | medium | low (AI-assigned, may override self-reported)",
  "ai_summary": "string (Gemini one-liner)",
  "report_count": "integer",
  "estimated_people_affected": "integer",
  "flags": {
    "medical_emergency": "bool",
    "trapped": "bool",
    "elderly_or_children": "bool",
    "structural_damage": "bool"
  },
  "first_reported_at": "ISO8601",
  "last_updated_at": "ISO8601",
  "status": "enum: new | acknowledged | dispatched | resolved",
  "recommended_resources": [
    { "resource_type": "string", "quantity": "integer", "reasoning": "string" }
  ],
  "assigned_resources": ["resource_id", "..."]
}
```

---

### 1.6 Resource (mock IDRN registry)

```json
{
  "resource_id": "string",
  "category": "enum: medical | rescue | shelter | transport | communication",
  "sub_type": "string (e.g. ambulance, motorboat, JCB, relief_camp, generator)",
  "custodian_agency": "string (e.g. 'SDRF Unit 3')",
  "quantity_total": "integer",
  "quantity_available": "integer",
  "status": "enum: available | partially_deployed | deployed | maintenance",
  "location": { "lat": "float", "lng": "float", "district": "string" },
  "contact": "string",
  "last_updated_at": "ISO8601"
}
```

---

### 1.7 DispatchRecord

```json
{
  "dispatch_id": "string",
  "incident_id": "string",
  "resource_id": "string",
  "quantity_dispatched": "integer",
  "dispatched_by": "string (authority user_id)",
  "dispatched_at": "ISO8601",
  "eta_minutes": "integer | null",
  "status": "enum: dispatched | en_route | arrived | completed"
}
```

---

### 1.8 AuthorityUser (dashboard login)

```json
{
  "user_id": "string",
  "name": "string",
  "role": "enum: DDMA | police | fire | ambulance | NDRF | SDRF | admin",
  "agency": "string",
  "email": "string"
}
```
Password stored bcrypt-hashed; auth via JWT (see endpoints below).

---

### 1.9 Device (citizen/gateway identity — lightweight, no personal login)

```json
{
  "device_id": "string (UUID, generated on first app install)",
  "device_jwt": "string (issued on registration, authenticates SOS uploads)",
  "last_seen_at": "ISO8601"
}
```

---

## 2. REST API Endpoints
Base URL: `/api/v1`

### Auth
| Method | Endpoint | Auth | Body → Returns |
|---|---|---|---|
| POST | `/auth/device/register` | none | `{device_model, app_version}` → `{device_id, device_jwt}` |
| POST | `/auth/authority/login` | none | `{email, password}` → `{access_token, user}` |
| POST | `/auth/authority/refresh` | refresh token | → `{access_token}` |

### SOS Ingestion
| Method | Endpoint | Auth | Body → Returns |
|---|---|---|---|
| POST | `/sos/batch` | Bearer device_jwt | `GatewayUploadBatch` → 202, `{accepted_uuids, duplicate_uuids}` |
| GET | `/sos/{uuid}/status` | Bearer device_jwt | → `{status}` (nice-to-have: lets citizen app show a "delivered" receipt) |

### Incidents
| Method | Endpoint | Auth | Notes |
|---|---|---|---|
| GET | `/incidents?bbox=&severity=&status=&since=` | Bearer authority | Filterable, paginated list |
| GET | `/incidents/{incident_id}` | Bearer authority | Full detail incl. source SOS + photos |
| PATCH | `/incidents/{incident_id}` | Bearer authority | Update status |
| GET | `/incidents/{incident_id}/recommendations` | Bearer authority | Returns OR-Tools + Gemini suggested allocation |
| POST | `/incidents/{incident_id}/dispatch` | Bearer authority | `{resource_id, quantity}` → creates `DispatchRecord` |
| POST | `/incidents/{incident_id}/refresh-summary` | Bearer authority | Force Gemini to regenerate `ai_summary` |

### Resources (mock IDRN)
| Method | Endpoint | Auth | Notes |
|---|---|---|---|
| GET | `/resources?category=&status=&district=` | Bearer authority | List |
| GET | `/resources/{resource_id}` | Bearer authority | Detail |
| POST | `/resources` | Bearer admin | Create (seeding only) |
| PATCH | `/resources/{resource_id}` | Bearer authority | Update quantity/status |

**Bulk seeding:** don't hand-POST each mock IDRN record — E should write a one-off seed script (`app/services/mock_idrn/seed.py`) that reads a CSV/JSON of the real district data (see §7) and inserts it directly via the DB session. Run once with `python -m app.services.mock_idrn.seed`, not through the API.

### AI / Situation Brief
| Method | Endpoint | Auth | Notes |
|---|---|---|---|
| GET | `/situation-brief` | Bearer authority | Latest Gemini-generated paragraph, cached, regenerated every 5–10 min |
| POST | `/situation-brief/refresh` | Bearer admin | Force regenerate |

### Optimizer
| Method | Endpoint | Auth | Notes |
|---|---|---|---|
| POST | `/optimize/allocate` | Bearer authority | `{incident_ids?, constraints?}` → OR-Tools assignment plan (`[{incident_id, resource_id, quantity}]`) — the "Suggest Allocation" button |

**Relationship to `Incident.recommended_resources`:** the field on the incident itself is a baseline suggestion computed automatically when the cluster is created/updated. `/optimize/allocate` is an on-demand full re-solve across multiple incidents at once (e.g. after new resources become available or priorities shift) — it's a refresh, not a duplicate of the same data.

### Misc
| Method | Endpoint | Notes |
|---|---|---|
| GET | `/health` | Uptime check |
| GET | `/stats/summary` | Dashboard top strip. Returns `{active_incidents: {critical, high, medium, low}, total_estimated_people_affected, resources: {available, deployed}, new_incidents_last_15min}` |

---

## 3. WebSocket Events
`WS /ws/incidents` — channel `incidents:updates`

**Auth:** browsers can't set an `Authorization` header on a WebSocket handshake, so pass the authority JWT as a query param at connect time: `wss://.../ws/incidents?token=<accessToken>`. Backend validates it once at connection open, same JWT as the REST endpoints.

```json
{ "event": "incident_created",        "data": "Incident" }
{ "event": "incident_updated",        "data": "Incident" }
{ "event": "incident_dispatched",     "data": "DispatchRecord" }
{ "event": "resource_updated",        "data": "Resource" }
{ "event": "situation_brief_updated", "data": { "text": "string", "updated_at": "ISO8601" } }
```

---

## 4. Repo Structure

**Three code repos, split along toolchain boundaries (Gradle / Python / Node), not along people — that way each repo has one CI setup and one build process.**

### `sih-android` (owners: A, B, C)
Single app, Gradle multi-module so A+B and C aren't editing the same files daily:
```
:app        → UI, navigation, DI wiring          (C)
:relay      → Nearby Connections, epidemic routing,
              foreground service, manifest exchange (A + B)
:data       → Room DB, entities, DAOs, WorkManager   (C)
:network    → Retrofit client, models matching §1/§2  (shared)
```

### `sih-backend` (owners: D, E)
```
/app/routers            → auth.py, sos.py, incidents.py, resources.py,
                           optimize.py, situation_brief.py   (D, E on last two)
/app/models              → Pydantic schemas — mirror §1 exactly (D)
/app/services/ai         → Gemini integration                (E)
/app/services/optimizer  → OR-Tools                           (E)
/app/services/mock_idrn  → seed data + resource logic         (E)
/app/db                  → PostGIS setup, Alembic migrations  (D)
/app/ws                  → WebSocket manager                  (D)
docker-compose.yml       → Postgres+PostGIS+backend, one command for local dev
```

### `sih-dashboard` (owner: F)
```
/src/api        → typed client matching §2 (or generated from FastAPI's
                   auto OpenAPI spec at /docs — see note below)
/src/components → IncidentCard, Map, SituationBrief, ResourcePanel, DispatchModal
/src/hooks      → useIncidents (REST+WS merge), useResources
/src/mocks      → mock JSON matching §1, used Days 2–4 before backend is live
```

### `sih-docs` (everyone, read/write)
Holds this file as the canonical version. If it drifts from a repo's actual implementation, this file wins — update code to match, not the other way around.

**Practical tip:** once FastAPI is running, its auto-generated Swagger UI (`/docs`) reflects your actual Pydantic models in real time — treat that as the live contract check once backend work starts, and this markdown file as the Day 1 planning baseline.

---

## 5. GitHub Workflow

- **Access:** all 6 as collaborators with write access on all 4 repos. Fine-grained permissions aren't worth the overhead for a trusted 6-person, 15-day sprint.
- **Branches:** `main` (always demo-able) ← `dev` (integration) ← `feature/<initials>-<short-desc>` (e.g. `feature/ab-mesh-manifest`)
- **PRs:** at least one teammate reviews before merging into `dev`. Merge `dev` → `main` only at the checkpoint days below — don't merge broken states into `main`.
- **Commits:** conventional format — `feat:`, `fix:`, `chore:`, `docs:` — keeps history scannable during a fast sprint.
- **Milestones** (GitHub Milestones, issues assigned to each):
  - Day 4 — mocked integration complete
  - Day 8 — integration round 1
  - Day 11 — end-to-end test pass
  - Day 13 — demo-ready
- **Board:** GitHub Projects, Kanban — Backlog / In Progress / In Review / Done. Labels: `android`, `backend`, `dashboard`, `ai`, `blocker`.
- **Secrets:** `.env.example` committed, real values (Gemini API key, JWT secret, DB creds) never committed. Share actual keys via a password manager or direct message, not Slack/Discord channels.

---

## 6. Team Notes — Epidemic Routing Tradeoffs & Battery

Since every phone in the mesh stores a copy of every SOS it has ever received (true epidemic routing), the physical constraints to design around are, in order of severity for this project:

1. **Battery — the real constraint, not storage or bandwidth.** Text-only payloads (0.5–2KB each) mean even thousands of stored SOS messages amount to a few MB — trivial on any phone. Bandwidth over BLE for a payload this small is near-instant. The actual cost is *continuous radio activity*: BLE/Wi-Fi Direct advertising and scanning running in the foreground service draws power constantly, whether or not any transfer is happening. For a trapped victim, this is the resource to protect. Mitigations to build in:
   - **Duty-cycle discovery** instead of continuous scanning — short scan windows on a timer (e.g. 10s every 45–60s) rather than always-on. Costs a little discovery latency, saves real battery.
   - **Prefer BLE-only discovery over Wi-Fi Direct** by default — BLE draws far less power. Only escalate to a higher-throughput channel if Nearby Connections needs to for a specific transfer (it can do this automatically), then drop back down.
   - **Grab location once at SOS creation**, not via continuous GPS polling. Add a manual "update my location" action instead of a background location stream.
   - **A visible battery-aware mode**: once battery drops below a threshold (e.g. 20%), throttle scan frequency further and let the user choose to stop actively relaying *other* people's messages while still holding their own for opportunistic sync — this is as much a product decision as an engineering one, worth deciding as a team rather than defaulting silently.

2. **Connection congestion in dense areas.** In a crowded evacuation point with many phones discovering each other simultaneously, `P2P_CLUSTER` has practical limits on concurrent connections — expect discovery to actually slow down in high-density spots, not speed up. Worth knowing before a live demo with many devices in one room.

3. **Manifest growth.** As a phone accumulates more distinct SOS UUIDs, the manifest it exchanges on every new contact grows too (still small — UUIDs are cheap — but non-zero). A TTL-based purge (see `relay_hop_count` / `last_relayed_at` in §1.2) keeps this bounded over a multi-day disaster.

4. **No delivery confirmation through the mesh itself.** A sender can't know their SOS got out until their own phone regains signal and can call `GET /sos/{uuid}/status` directly. This is a known, accepted limitation of the design, not a bug to solve.

---

## 7. Mock IDRN — Where to Get Real Numbers

IDRN itself has no public API and requires MHA-authorized login for full access — but two legitimate, no-login sources give real numbers to seed the mock database with, so the demo isn't built on invented figures:

1. **IDRN's own public query tool — https://idrn.nidm.gov.in/** Look for "Country Wide Query for Resource Inventory — Open to All" on the homepage. No login needed. Select State → District → Category (Equipment / Human Resources / Medical Supplies) → Item, and it returns actual counts reported by that district. Data completeness varies a lot by district — try a few, including your state capital's district, which tends to be better maintained, until you find one with populated results for your demo.

2. **District Disaster Management Plans (DDMPs)** — every district publishes one, usually as a PDF hosted on the state government's disaster management site (often on an `s3waas.gov.in` subdomain). Search `"<district name> district disaster management plan" filetype:pdf` or `"<district name> DDMP resource inventory"`. These typically include an equipment/resource inventory section (ambulances, boats, JCBs, relief camps, blood banks) with agency names attached — useful both for real numbers and for realistic `custodian_agency` values in your `Resource` records.

**Recommendation:** pick one real district for your demo, pull whatever real numbers you can find from either source, and fill only genuine gaps with clearly-reasonable estimates. In the pitch, be upfront that this is an "IDRN-structured mock registry seeded with public district data" — that's a defensible, honest framing if a judge asks about it directly.
