# `device_id` Contract Inconsistency — TRAAN Finding

## Finding

There is an inconsistency within the Day 1 / Master contract between the definition of `device_id` and the `POST /api/v1/auth/device/register` endpoint.

This is a **contract-level inconsistency**, not merely a Component C implementation issue.

The relevant Day 1 contract defines `device_id` as an installation identity generated on first app install, while the registration endpoint is currently specified as generating and returning the `device_id` without accepting one from the Android device. fileciteturn0file0

---

## 1. What the Data Contract Says

### `SOSRequest`

The Day 1 contract defines:

```text
device_id: string (installation ID of originating phone)
```

Therefore, the natural interpretation is:

```text
Android phone
    ↓
first app installation
    ↓
installation ID generated
    ↓
SOSRequest.device_id uses that ID
```

### `Device`

The Day 1 contract further defines:

```text
device_id: string (UUID, generated on first app install)
```

This reinforces the interpretation that the device/installation is the source of the persistent `device_id`.

---

## 2. What the Registration API Says

The Day 1 contract defines:

```text
POST /api/v1/auth/device/register
```

Request:

```json
{
  "device_model": "string",
  "app_version": "string"
}
```

Response:

```json
{
  "device_id": "string",
  "device_jwt": "string"
}
```

The request does **not** contain a `device_id`.

Therefore, the API as currently specified implies:

```text
Android
    ↓
POST /auth/device/register
    ↓
Backend generates device_id
    ↓
Backend returns device_id
```

This conflicts with the data-schema wording that the `device_id` is generated on first app install.

---

## 3. Why This Creates a Problem

The conflict becomes significant because TRAAN explicitly supports offline SOS creation.

Consider this sequence:

```text
Phone A installed
    ↓
Android generates local installation ID
    ↓
Local ID = A123
    ↓
Phone has no internet
    ↓
User creates SOS-1
    ↓
SOS-1.device_id = A123
```

Later:

```text
Internet becomes available
    ↓
Phone registers with backend
    ↓
Backend generates device_id = B456
    ↓
Phone receives B456
```

Future SOS messages could then contain:

```text
SOS-1 → device_id = A123
SOS-2 → device_id = B456
```

Both SOS records originated from the same physical installation, but the backend may see two different device identities.

This can create problems for:

- SOS provenance
- device-level deduplication
- relay metadata
- gateway uploads
- backend device tracking
- auditability of reports created before registration

---

## 4. Why Antigravity Correctly Flagged It

The Master Prompt's Global Engineering Instructions explicitly prohibit silently changing shared contracts.

Therefore, Component C should **not** independently decide whether:

- the Android-generated ID is canonical, or
- the backend-generated ID is canonical.

The issue affects multiple components:

```text
Component C
Android App Shell
        ↕
Component D
Backend Core
        ↕
Component A+B
Mesh / Relay Engine
```

Changing the registration contract would therefore affect more than Component C.

Antigravity was correct to preserve the existing implementation and report:

> Backend Core confirmation required.

---

## 5. The Two Possible Contract Models

### Model A — Android Owns the Device ID

The Android installation generates the persistent ID:

```text
Android
    ↓
generate installation UUID
    ↓
device_id = A123
```

Registration would then conceptually become:

```json
{
  "device_id": "A123",
  "device_model": "...",
  "app_version": "..."
}
```

Backend registers that existing ID and returns:

```json
{
  "device_id": "A123",
  "device_jwt": "..."
}
```

This makes the following contract statements consistent:

```text
device_id = installation ID
device_id = UUID generated on first app install
```

It also preserves identity for SOS messages created while offline.

However, this would require changing the currently specified registration request contract.

---

### Model B — Backend Owns the Device ID

The Android app does not own the canonical backend identity.

Instead:

```text
Android
    ↓
POST /auth/device/register
    ↓
Backend generates B456
    ↓
Android receives B456
```

The backend-issued ID then becomes the canonical `device_id`.

However, this creates a problem for SOS messages created before registration if they use a locally generated ID.

A reconciliation mechanism would then be required.

The current Day 1 contract does not specify such a mechanism.

---

## 6. Preferred Resolution

Based strictly on the existing Day 1 wording, **Model A appears more consistent with the existing data schema**:

```text
Android generates installation UUID
            ↓
POST /auth/device/register
{
    device_id,
    device_model,
    app_version
}
            ↓
Backend registers that identity
            ↓
{
    device_id,
    device_jwt
}
```

This preserves a single identity across:

```text
offline SOS
    ↓
relay
    ↓
gateway upload
    ↓
backend
```

However, this is a **proposed resolution**, not an approved contract change.

The team must explicitly agree on it before changing the API or implementation.

---

## 7. What Should NOT Be Done Yet

Until the team resolves the contract:

- Do not silently change `/auth/device/register`.
- Do not add `device_id` to the registration request without team approval.
- Do not remove the local installation ID.
- Do not introduce a second device identifier.
- Do not create an undocumented reconciliation mechanism.
- Do not change Component A+B relay identity behavior.
- Do not change backend database semantics independently.

The canonical Day 1 contract should be updated first if a contract change is approved.

---

## 8. Required Team Decision

The Backend Core team should answer:

> The Day 1 contract says `SOSRequest.device_id` is the originating phone's installation ID and `Device.device_id` is generated on first app install. However, `POST /api/v1/auth/device/register` currently accepts only `device_model` and `app_version` and returns a newly generated `device_id`. Should the Android app-generated installation UUID be sent during registration and become the canonical backend `device_id`, or is the backend-issued ID intended to replace it? If the latter, how should SOS records created offline before registration be reconciled?

---

## 9. Impacted Components

| Component | Impact |
|---|---|
| Component C — Android Shell | Stores/generates device identity and places it into SOS records |
| Component A+B — Relay Engine | Carries SOS records containing `device_id` |
| Component D — Backend Core | Registers devices and persists incoming SOS reports |
| Gateway Sync | Uploads offline/relayed SOS records |
| Database | May depend on canonical device identity semantics |

---

## 10. Status of This Finding

**Classification:** Contract-level inconsistency

**Severity:** High for cross-component integration, but not an immediate blocker to all Component C work.

**Current action:** Preserve existing implementation and do not silently change the contract.

**Required owner:** Backend Core + overall team / contract owner.

**Recommended next step:** Resolve the canonical `device_id` ownership model before final integration of Android registration and backend device identity.

---

## Conclusion

The issue is best described as:

> **The Day 1 data contract treats `device_id` as an installation-generated identity, while the current device-registration API treats `device_id` as a backend-generated identity. The contract does not define how these identities are reconciled for SOS messages created offline before registration.**

This is an inconsistency in the **shared contract itself**, specifically between the `Device`/`SOSRequest` schema definitions and the device-registration endpoint.

Component C should not resolve it unilaterally. The contract should be clarified first, and then Android, Backend, and Relay implementations should be aligned to the agreed definition.
