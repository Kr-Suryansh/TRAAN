# ApiEndpoints.md — Component 1: Mesh/Relay Engine (:relay)

> This file documents the interfaces **exposed by** and **consumed by** `:relay`.
> Compare against `day1-contracts-and-repo-setup.md` as the master contract.
> Do not deviate from the master contract without discussion and approval.

---

## 1. Public `:relay` Kotlin API

These are the ONLY methods that `:app` needs to call. No other classes or interfaces
from `:relay` are part of the public contract.

**Source:** `antigravity-build-prompts -.md` — Component Prompt 1
**Implementation class:** `com.sih.relay.RelayManager`
**Interface:** `com.sih.relay.api.RelayApi`

```kotlin
interface RelayApi {

    /**
     * Start the relay engine (advertise + discover via Nearby Connections).
     * From Day 5: also starts the foreground service with duty-cycled scanning.
     * Day 1: stub — no-op.
     */
    fun startRelay()

    /**
     * Stop the relay engine cleanly.
     * From Day 5: also stops the foreground service.
     * Day 1: stub — no-op.
     */
    fun stopRelay()

    /**
     * Observe all SOS messages currently held in the relay store.
     * Returns a Kotlin Flow that emits a new list whenever the store changes.
     * Day 1: returns an empty Flow.
     */
    fun getRelayStore(): Flow<List<SOSRequest>>
}
```

**How `:app` obtains an instance:**
```kotlin
// :app creates a RelayManager, injecting a RelayDataSource implementation from :data
// (see Agent.md §Architecture Decisions for the full wiring explanation)
val relayManager: RelayApi = RelayManager(dataSource = /* injected by :app */)
relayManager.startRelay()
val store: Flow<List<SOSRequest>> = relayManager.getRelayStore()
```

---

## 2. Internal Boundary Interface (NOT a public project API)

> [!IMPORTANT]
> `RelayDataSource` is an **internal implementation detail** — it is how `:relay`
> avoids importing Room directly. It is NOT a shared project API contract.
> It is documented in `Agent.md §Architecture Decisions`, NOT here as a contract.
> It is listed below only for completeness so Component C knows what to implement.

**Interface:** `com.sih.relay.api.RelayDataSource`
**Implemented by:** `:data` (Component C — Room-backed)
**Injected by:** `:app` into `RelayManager` at startup

Component C must implement this interface when adding Room persistence for the relay store.
See `Agent.md §Architecture Decisions — Decision 1` for the full wiring instructions.

---

## 3. REST Endpoints — Consumed (NOT owned by :relay)

`:relay` does NOT call any backend REST endpoints.
Gateway upload (flushing the relay store to the backend) is triggered by `:data`'s
WorkManager job, which calls `:network`, which calls the backend.

The following endpoints are referenced here for clarity — they are owned by other components:

| Endpoint | Owner | Notes |
|---|---|---|
| `POST /api/v1/sos/batch` | Backend (D) + `:network` | Upload gateway batch — not called by `:relay` |
| `GET /api/v1/sos/{uuid}/status` | Backend (D) + `:network` | SOS delivery receipt — not called by `:relay` |

---

## 4. REST Endpoints — Exposed by :relay

**None.** `:relay` is a Kotlin Android library module. It does not expose any HTTP endpoints.
All REST endpoints are owned by the backend component (D).

---

## 5. Schema Types Defined in :relay

These types are defined in `:relay` but consumed across modules.
All field names match `day1-contracts-and-repo-setup.md` exactly.

| Class | Package | Contract Source |
|---|---|---|
| `SOSRequest` | `com.sih.relay.model` | §1.2 |
| `RelayManifest` | `com.sih.relay.model` | §1.3 |
| `SosLocation` | `com.sih.relay.model` | §1.2 (location object) |
| `UserMedicalProfile` | `com.sih.relay.model` | §1.1 (snapshot copy) |
| `EmergencyType` | `com.sih.relay.model` | §1.2 enum |
| `SeverityHint` | `com.sih.relay.model` | §1.2 enum |
| `SOSStatus` | `com.sih.relay.model` | §1.2 enum |

---

## 6. Change Log

| Date | Change | Approved by |
|---|---|---|
| 2026-08-13 | Initial Day 1 contract defined | User |
