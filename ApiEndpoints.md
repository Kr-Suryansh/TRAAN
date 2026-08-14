# ApiEndpoints.md

*This document matches the master API blueprint from day1-contracts-and-repo-setup.md. Any required deviations will be recorded here.*

## AI / Situation Brief
| Method | Endpoint | Auth | Notes |
|---|---|---|---|
| GET | `/api/v1/situation-brief` | Bearer authority | Latest Gemini-generated paragraph, cached, regenerated every 5–10 min |
| POST | `/api/v1/situation-brief/refresh` | Bearer admin | Force regenerate |
| POST | `/api/v1/incidents/{incident_id}/refresh-summary` | Bearer authority | Force Gemini to regenerate `ai_summary` |

## Optimizer
| Method | Endpoint | Auth | Notes |
|---|---|---|---|
| POST | `/api/v1/optimize/allocate` | Bearer authority | `{incident_ids?, constraints?}` → OR-Tools assignment plan (`[{incident_id, resource_id, quantity}]`) |

## Resources (mock IDRN)
| Method | Endpoint | Auth | Notes |
|---|---|---|---|
| GET | `/api/v1/resources?category=&status=&district=` | Bearer authority | List |
| GET | `/api/v1/resources/{resource_id}` | Bearer authority | Detail |
| POST | `/api/v1/resources` | Bearer admin | Create (seeding only) |
| PATCH | `/api/v1/resources/{resource_id}` | Bearer authority | Update quantity/status |

**Note on Bulk Seeding**: A seed script `app/services/mock_idrn/seed.py` will be used to populate the local mock IDRN database via the DB session instead of the API.
