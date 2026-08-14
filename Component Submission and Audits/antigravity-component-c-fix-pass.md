# Antigravity Prompt — Component C Fix Pass

## Purpose

You are working on **Component C — Android App Shell** of the TRAAN disaster-response platform.

The current Component C implementation has already been audited against:

- `day1-contracts-and-repo-setup.md`
- `Updated_antigravity-build-prompts.md`

Do **not** redesign the component. Make only the changes listed below, preserve the existing architecture, and do not modify shared API contracts.

The Day 1 contracts remain authoritative.

---

# 1. Mandatory Rules

Before changing anything:

1. Inspect the current repository structure.
2. Inspect the existing Component C implementation and tests.
3. Inspect the existing Gradle configuration and Android manifests.
4. Reuse existing types, repositories, workers, utilities, and conventions.
5. Do not rewrite unrelated files.
6. Do not rename API endpoints, DTO fields, schema fields, enum values, or status values.
7. Do not implement A+B's Nearby Connections relay engine.
8. Keep the existing `:relay` abstraction/stub unless a minimal interface adjustment is required for integration.
9. Do not add photo, video, or audio functionality.
10. Do not introduce continuous background location tracking.
11. Do not silently change the shared contract. If a contract ambiguity is discovered, stop and report it rather than changing the contract.

---

# 2. Fix the Location Permission Race

## Problem

The current Home/SOS flow requests location permission and then immediately calls `triggerSos()` without waiting for the permission result.

This can create an SOS with `0,0` even when the user subsequently grants location permission.

## Required behavior

Implement this exact flow:

```text
User taps SOS
       |
       +-- location permission already granted
       |        |
       |        └── capture location once
       |                 |
       |                 └── create SOS
       |
       └-- permission not granted
                |
                └── request permission
                         |
                         +-- granted
                         |      |
                         |      └── capture location once
                         |               |
                         |               └── create SOS
                         |
                         └-- denied
                                |
                                └── create SOS without location
```

### Requirements

- Never block SOS creation merely because location permission is unavailable.
- Never call the location lookup before the permission result is known.
- If permission is granted, capture location once at SOS creation.
- Do not introduce continuous GPS/background location.
- If location cannot be obtained even after permission is granted, create the SOS with the existing nullable/fallback behavior rather than failing the SOS.
- Preserve the existing `is_quick_sos` behavior.
- Do not add mandatory user input.

### Tests

Add or update tests covering:

- Permission already granted → location capture occurs → SOS is created.
- Permission initially denied/not granted → permission request occurs → grant → location capture → SOS is created.
- Permission denied → SOS is still created.
- Location unavailable → SOS is still created.
- No network is required for any of these flows.

---

# 3. Fix Release/Debug Network Security

## Problem

The current `network_security_config.xml` permits cleartext traffic for development hosts such as:

```text
10.0.2.2
localhost
```

but it is referenced from the main application manifest.

This means the development cleartext configuration is not actually isolated to the debug build.

## Required solution

Move the development-only cleartext configuration to the debug variant.

The release build must not inherit the development cleartext exceptions.

Use the existing Android Gradle structure rather than inventing a new architecture.

Expected conceptual structure:

```text
app/src/main/AndroidManifest.xml
    ↓
No development cleartext exception

app/src/debug/AndroidManifest.xml
    ↓
References debug-only network security configuration

app/src/debug/res/xml/network_security_config.xml
    ↓
Allows required local development hosts
```

Adapt this to the repository's existing structure.

### Requirements

- Release must use HTTPS / normal secure network behavior.
- Debug may support local emulator development using `10.0.2.2` if required.
- Do not permit arbitrary cleartext traffic.
- Do not weaken release security merely to simplify local development.
- Keep comments/documentation accurate.

### Verification

Verify the Gradle/Android configuration so:

- Debug builds can still connect to the local development backend.
- Release builds do not reference the debug cleartext configuration.

---

# 4. Resolve `device_id` Semantics Carefully

## Contract

The Day 1 contract defines:

```text
device_id = installation ID of originating phone
```

The current implementation uses the locally generated installation UUID before registration and then uses the backend-issued `device_id` after registration.

Do NOT blindly change this.

## Required action

Inspect:

- `DevicePreferences`
- `SosRepository`
- `DeviceRegistrationWorker`
- `DeviceRegistrationResponse`
- all uses of `device_id`
- the Day 1 API contract

Determine whether the backend-issued `device_id` is intended to be the same persistent installation identity.

### If the existing implementation is contract-compatible

Keep it and document why.

### If the implementation can produce two different identities for the same physical installation

Fix the implementation so one persistent installation identity is used consistently, without changing the API contract.

### Important

Do not invent a new field.

Do not rename `device_id`.

Do not change the backend endpoint.

Do not create a second device identifier unless the existing contract explicitly requires it.

Add a focused regression test proving that the same installation identity is used consistently throughout the Android SOS lifecycle.

If this cannot be resolved from the current repository and Day 1 contract, **do not guess**. Report:

```text
Device ID ambiguity remains:
- Existing behavior:
- Contract wording:
- Why the two cannot be conclusively reconciled:
- Backend confirmation required:
```

---

# 5. Persist Onboarding Skip

## Problem

The onboarding screen currently allows:

```text
Skip for now
```

but the skip decision is not persisted.

As a result, onboarding can reappear on later launches.

## Required behavior

Persist an onboarding completion/skip state locally.

The onboarding flow should be:

```text
First launch
    ↓
Onboarding
    |
    +-- Save profile → persist onboarding completion
    |
    └-- Skip → persist onboarding skipped
             ↓
Future launches
             ↓
Home
```

### Requirements

- Use the existing local preferences/storage mechanism where appropriate.
- Do not put onboarding state into the backend.
- Do not require network access.
- If the user later edits their medical profile, preserve the existing profile behavior.
- Do not remove the ability to edit the profile later.
- Keep the SOS screen immediately accessible after onboarding is skipped.

### Tests

Add tests verifying:

- Save profile → onboarding does not reappear.
- Skip → onboarding does not reappear.
- App restart preserves the state.
- Medical profile remains locally editable.

---

# 6. Add Blood-Type Validation

Inspect the current medical-profile implementation.

Age and emergency-contact phone validation already exist.

Add reasonable validation for the blood-type field consistent with the existing UI and validation style.

Do not invent additional medical requirements.

At minimum, ensure obviously invalid arbitrary input cannot silently be treated as a valid blood type.

Add focused tests for:

- valid blood types accepted
- invalid blood type rejected or handled consistently
- optional empty blood type remains allowed because the contract defines it as nullable

Do not make blood type mandatory.

---

# 7. Review TTL Cleanup

## Contract requirement

SOS records are retained locally for relay/gateway purposes and are eventually cleaned using the TTL.

The configured TTL of approximately 72 hours is acceptable.

The current DAO cleanup only deletes records whose status is `uploaded`.

Review this behavior carefully.

### Required behavior

Do not delete active records prematurely.

At the same time, stale records must not remain forever solely because they are:

```text
pending_local
```

or

```text
in_relay
```

### Important

Because Component C does not own the relay engine, coordinate the semantics with the existing relay abstraction.

Do not invent a new routing policy.

Implement the smallest change that makes the TTL behavior consistent with the contract.

If safe cleanup semantics cannot be determined without A+B's implementation, do not make a speculative destructive change.

Instead:

1. Preserve the existing safe behavior.
2. Add/document the integration dependency.
3. Add a TODO/test hook if appropriate.
4. Report exactly what A+B must provide.

---

# 8. Improve `MainActivity` Startup

The current startup path uses `runBlocking` for Room/profile state.

Avoid blocking the Activity/UI thread for database initialization.

Use the smallest lifecycle-aware coroutine/background approach consistent with the existing architecture.

Requirements:

- Do not change onboarding behavior.
- Do not introduce race conditions between startup, registration, and navigation.
- Do not perform network-dependent onboarding decisions.
- Preserve the current offline-first behavior.

Add or update tests if the startup logic is testable.

---

# 9. Improve Status Screen Request Behavior

Review `StatusViewModel`.

Avoid making unnecessary backend requests when:

- the device is offline
- device registration has not completed
- there is no valid local SOS UUID

The status screen should still work offline using local Room state.

Backend status should only be checked when the necessary conditions exist.

Do not change the `GET /api/v1/sos/{uuid}/status` endpoint.

---

# 10. Tighten Auth Interceptor Path Matching

Review `AuthInterceptor`.

The registration endpoint should remain unauthenticated.

Prefer exact endpoint/path matching over broad `contains()` matching where practical.

Do not break:

```text
POST /api/v1/auth/device/register
```

and do not accidentally omit the Bearer token from:

```text
POST /api/v1/sos/batch
GET /api/v1/sos/{uuid}/status
```

Add a focused unit test if the interceptor is currently testable.

---

# 11. Expand Gateway Sync Tests

Add tests for the important failure/retry cases.

At minimum cover:

### Successful upload

```text
local SOS records
      ↓
batch upload
      ↓
accepted_uuids
      ↓
records marked uploaded
      ↓
records remain locally stored
```

### Duplicate upload

```text
duplicate_uuids
      ↓
records treated as successfully acknowledged
      ↓
records marked uploaded
      ↓
records retained locally
```

### 401

```text
401
 ↓
credentials cleared
 ↓
registration re-enqueued
```

Do not create an infinite retry loop.

### Offline

No upload attempt when the network constraint is unavailable.

### Complete gateway batch

The worker must upload all locally stored SOS records, including records originating from other devices.

### Malformed/failed backend response

The worker must not mark records uploaded if the backend response cannot be safely interpreted.

---

# 12. Preserve the Network DTO Contract

Do not change this behavior.

`SosRequestDto` must NOT contain:

```text
status
```

`status` is device-side only.

The wire payload must remain:

```text
SOSRequest
without device-side status
```

Keep the existing serialization regression tests.

Do not add `status` to make local state easier to serialize.

---

# 13. Update Documentation

Update `README.md` so it reflects the actual implementation.

Specifically remove outdated statements such as first-launch detection being pending if it is now implemented.

Correct the documented Kotlin version to match the actual version catalog.

Document:

- onboarding skip persistence
- debug vs release network configuration
- device registration lifecycle
- 401 re-registration behavior
- gateway upload behavior
- local SOS retention/TTL
- relay stub/integration dependency

Do not document features that are not actually implemented.

---

# 14. Preserve Component Ownership

Do NOT implement:

- Nearby Connections
- epidemic routing
- manifest exchange
- foreground relay service
- BLE/Wi-Fi Direct logic
- backend clustering
- Gemini
- OR-Tools
- dashboard UI

Those belong to other components.

The Android Shell only consumes the relay interface.

---

# 15. Required Verification

Before declaring the work complete, run the available checks.

At minimum attempt:

```bash
./gradlew test
```

If executable permission is unavailable:

```bash
bash gradlew test
```

Also run the appropriate Android build task, for example:

```bash
./gradlew assembleDebug
```

and, if the environment supports it:

```bash
./gradlew assembleRelease
```

Verify that the release configuration does not reference the debug cleartext network security configuration.

If Gradle cannot download dependencies because the environment has no network access, report that clearly rather than claiming the build passed.

---

# 16. Acceptance Criteria

Before completion, verify:

- [ ] SOS works in airplane mode.
- [ ] SOS does not wait on network.
- [ ] Location permission flow does not create an incorrect `0,0` SOS merely because the permission dialog is still open.
- [ ] Permission denial still allows SOS creation.
- [ ] Location is captured once at SOS creation.
- [ ] No continuous location tracking was introduced.
- [ ] Medical profile is stored locally.
- [ ] Medical profile is automatically snapshotted into SOS.
- [ ] Onboarding skip persists across app restarts.
- [ ] Profile completion persists across app restarts.
- [ ] Blood type remains optional.
- [ ] Network DTO contains no `status`.
- [ ] Gateway uploads the entire local SOS store.
- [ ] Accepted UUIDs become `uploaded`.
- [ ] Duplicate UUIDs are safely treated as acknowledged.
- [ ] Uploaded records remain locally stored until TTL cleanup.
- [ ] 401 causes credential recovery without an infinite retry loop.
- [ ] Debug local HTTP support works if required.
- [ ] Release does not inherit development cleartext exceptions.
- [ ] Device ID semantics are consistent with the Day 1 contract, or the unresolved ambiguity is explicitly reported.
- [ ] Existing API endpoints remain unchanged.
- [ ] Existing enum values remain unchanged.
- [ ] No photo/audio fields are introduced.
- [ ] No unrelated modules are rewritten.
- [ ] Tests pass where the environment permits.
- [ ] Documentation matches the implementation.

---

# 17. Final Completion Report

Finish with:

```text
COMPONENT C FIX PASS REPORT

1. Changes Implemented
- ...

2. Files Added
- ...

3. Files Modified
- ...

4. Contract Verification
- API endpoints: PASS / FAIL
- DTO fields: PASS / FAIL
- Enum values: PASS / FAIL
- Device ID semantics: PASS / NEEDS BACKEND CONFIRMATION
- Local status isolation: PASS / FAIL

5. Tests Added/Modified
- ...

6. Commands Run
Build:
Test:
Verification:

7. Build Result
- PASS
OR
- NOT VERIFIED — explain exact environment limitation

8. Integration Dependencies
- ...

9. Known Limitations
- ...

10. Contract Changes
- None
OR
- Proposed change:
  Reason:
  Affected components:

11. Acceptance Criteria
- [ ] ...
- [ ] ...

12. Remaining Work Before Merge
- ...
```

## Final Instruction

This is a **fix pass**, not a rewrite.

Inspect first, modify only what is necessary, preserve the existing architecture, keep the API contracts immutable, test every changed behavior, and explicitly report anything that cannot be verified.
