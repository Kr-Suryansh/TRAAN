# Component C — Android Shell Audit

## Audit Basis

This audit covers the current, uncommitted Android Shell ZIP (Component C) against:

- Day 1 — API Contracts & Repo Setup
- Antigravity Build Prompts — Master Prompt, Global Engineering Instructions, and Component Prompt 2 (Android App Shell)

The contract documents were treated as authoritative.

## Overall Verdict

**Substantially improved and architecturally aligned, but not yet merge-ready.**

The implementation is approximately **8/10 for contract alignment**.

There are **2 immediate blockers/high-priority defects**, one integration-level contract question, and several medium-priority issues.

---

## 1. File-by-File Audit

| File | Verdict | Audit Result |
|---|---|---|
| `app/build.gradle.kts` | ⚠️ | Correct modules, Compose, Hilt, WorkManager, and location dependencies. Debug/release URLs are contract-compatible. Release URL remains a placeholder, so it is not demo-ready. |
| `app/src/main/AndroidManifest.xml` | ⚠️ | Correct Internet/location permissions. WorkManager setup is reasonable. Network security config is referenced from the main manifest, which undermines the claimed debug-only cleartext configuration. |
| `MainActivity.kt` | ⚠️ | Stable installation ID and registration startup are good. First-launch onboarding detection is implemented. However, `runBlocking` on the Activity/UI thread for Room startup is undesirable. Also, skipping onboarding is not persisted, so it can reappear on later launches. |
| `SihApplication.kt` | ✅ | Correct Hilt/WorkManager configuration and periodic sync scheduling. |
| `AppModule.kt` | ⚠️ | Correct Retrofit/Hilt wiring. Stub relay boundary is clean. Real relay integration will require replacing this binding. |
| `AppNavigation.kt` | ✅ | Correct onboarding/home/status flow. |
| `Screen.kt` | ✅ | Simple and appropriate navigation contract. |
| `HomeScreen.kt` | ❌ | **Permission flow bug:** permission is requested and `triggerSos()` is called immediately. The callback result is ignored. A user granting location permission can still have the SOS created with `0,0`. |
| `HomeViewModel.kt` | ⚠️ | One-shot location capture and offline SOS creation are correctly separated. Missing location does not block SOS. The permission-flow issue remains. |
| `OnboardingScreen.kt` | ⚠️ | Correct fields, optional profile, skip action, editable later. **Skip is not persisted**, so first-launch onboarding can recur. |
| `OnboardingViewModel.kt` | ⚠️ | Good age/phone validation and local persistence. **Blood type has no validation**, despite the corrective acceptance requirements. |
| `StatusScreen.kt` | ✅ | Uses device-side status and provides backend status separately. No contract drift found. |
| `StatusViewModel.kt` | ⚠️ | Correct validated-network capability check. Automatically checks backend status on connectivity return. It also calls the backend immediately during initialization even when offline/unregistered; not fatal, but unnecessarily noisy. |
| `SihTheme.kt` | ✅ | UI-only; no contract issues. |
| `network_security_config.xml` | ❌ | **Security issue.** Explicitly permits cleartext to `10.0.2.2` and localhost, but the file is referenced by the main manifest rather than a debug-only manifest. |
| `data/build.gradle.kts` | ⚠️ | Dependencies are appropriate. `:data -> :relay` and `:relay -> :network` are consistent with current architecture, though eventual relay integration needs careful persistence integration. |
| `AppDatabase.kt` | ✅ | Room schema correctly contains `SOSRequest` and `UserMedicalProfile`. No destructive migration fallback. |
| `RoomConverters.kt` | ✅ | Appropriate local JSON conversion. |
| `SosRequestEntity.kt` | ✅ | Strong contract match. Required SOS fields are present; `status` is explicitly device-side only. |
| `UserMedicalProfileEntity.kt` | ✅ | Correct singleton local profile representation. |
| `SosRequestDao.kt` | ⚠️ | Correct idempotent insertion and full-store retrieval. **TTL deletion only deletes `uploaded` records**, allowing stale pending/in-relay records to remain indefinitely. |
| `UserMedicalProfileDao.kt` | ✅ | Correct singleton/upsert behavior. |
| `SosRepository.kt` | ⚠️ | Strong offline-first behavior. Stable installation ID, medical snapshot, and deferred WorkManager sync are good. **Potential contract concern:** after registration, SOS `device_id` changes from installation UUID fallback to backend-issued device ID. The Day 1 schema describes `device_id` as the originating phone's installation ID. |
| `UserMedicalProfileRepository.kt` | ✅ | Correctly keeps profile local and snapshots it into SOS. |
| `DevicePreferences.kt` | ✅ | Good use of `EncryptedSharedPreferences`; stable installation ID is a significant improvement. |
| `DataModule.kt` | ✅ | Correct `CONNECTED` WorkManager constraint, 15-minute periodic sync, immediate sync, and registration worker. |
| `SosConstants.kt` | ✅ | Centralized 72-hour TTL is correct. |
| `EmergencyType.kt` | ✅ | Exact seven contract enum values. |
| `SeverityHint.kt` | ✅ | Exact contract values and nullable behavior. |
| `SosStatus.kt` | ✅ | Exact device-side status values. |
| `DeviceRegistrationWorker.kt` | ✅ | Correct endpoint/body, retry behavior, and secure credential storage. |
| `GatewaySyncWorker.kt` | ⚠️ | Very good overall. Uploads the complete local store, merges relay records, deduplicates locally, marks accepted/duplicate UUIDs uploaded, retains records, and handles 401 separately. TTL limitation remains. |
| `DisasterApi.kt` | ✅ | Exactly the three Android-owned endpoints: registration, batch upload, SOS status. No invented endpoint. |
| `RetrofitClientFactory.kt` | ✅ | HEADERS-only debug logging is appropriate and avoids exposing medical payloads/JWTs. |
| `AuthInterceptor.kt` | ⚠️ | Correct Bearer device JWT behavior and registration exclusion. Path matching via `contains()` is somewhat loose but not currently a practical contract violation. |
| `LocationDto.kt` | ✅ | Exact schema. |
| `SosRequestDto.kt` | ✅ | **Major positive:** `status` is completely absent from the network DTO. Correct wire contract. |
| `UserMedicalProfileDto.kt` | ✅ | Exact schema. |
| `DeviceRegistrationRequest.kt` | ✅ | Exact request body. |
| `GatewayUploadBatch.kt` | ✅ | Exact batch structure. |
| `BatchUploadResponse.kt` | ✅ | Exact response structure. |
| `DeviceRegistrationResponse.kt` | ✅ | Exact response structure. |
| `SosStatusResponse.kt` | ✅ | Correctly leaves backend status unspecified because the Day 1 contract does not enumerate its values. |
| `DtoSerializationTest.kt` | ✅ | Strong regression coverage for the most important contract issue: `status` cannot leak into the network payload. |
| `EnumContractTest.kt` | ✅ | Good exact enum coverage. |
| `TtlConstantTest.kt` | ✅ | Good TTL regression coverage. |
| `SosRepositoryTest.kt` | ⚠️ | Good tests for offline creation, stable identity, profile snapshot, TTL constant, and initial state. Worker-level behaviors are not comprehensively tested. |
| `relay/RelayRepository.kt` | ⚠️ | Good temporary integration boundary. Explicitly marked as a stub. It does not yet expose operations for C/data to persist relay-received SOS or update hop metadata; this is primarily an A+B integration dependency. |
| `README.md` | ❌ | Documentation is stale. It says first-launch detection is pending even though it is implemented. It also says Kotlin `2.1.0` while the version catalog uses Kotlin `2.0.21`. |

---

## 2. Immediate High-Priority Defects

### P0/P1 — Location Permission Race

The current flow is effectively:

```text
User taps SOS
    ↓
request ACCESS_FINE_LOCATION
    ↓
immediately triggerSos()
    ↓
ViewModel checks permission
    ↓
permission isn't granted yet
    ↓
SOS created with 0,0
    ↓
user grants permission
```

The callback result is ignored.

Required behavior:

```text
Permission already granted
    → capture location
    → create SOS

Permission not granted
    → request permission
        → granted → capture location → create SOS
        → denied → create SOS without location
```

This should be fixed before merging.

### P0/P1 — Release Cleartext Configuration

`network_security_config.xml` permits cleartext traffic to `10.0.2.2` and localhost, but the configuration is referenced from the main manifest.

The XML claims to be debug-only, but the manifest configuration does not enforce that boundary.

Recommended fix:

- Keep the release manifest secure.
- Apply the development cleartext configuration only to the debug variant.
- Do not permit cleartext in release.

### P1 — Device ID Semantics Need Backend Confirmation

The contract defines:

```text
device_id = installation ID of originating phone
```

The implementation currently uses the local installation UUID before registration and the backend-issued `device_id` after registration.

This needs to be explicitly confirmed with Backend Core.

The important question is whether the backend-issued `device_id` is intended to equal the Android installation identity or whether the app must preserve the locally generated installation UUID as the SOS origin identifier.

---

## 3. Other Issues to Fix

### Onboarding Skip Is Not Persistent

The user can select:

```text
Skip for now
```

and reach Home, but no persisted `onboarding_skipped` state is stored.

On a later launch, the app can show onboarding again.

This is a functional polish issue rather than a core SOS blocker.

### TTL Cleanup Is Too Restrictive

Current behavior effectively is:

```text
uploaded + old       → deleted
in_relay + old       → retained indefinitely
pending_local + old  → retained indefinitely
```

The contract calls for TTL-based cleanup of SOS records based on `last_relayed_at`.

Review this with the relay team so stale records are eventually purged without deleting records prematurely.

### Blood Type Validation

Age and emergency-contact phone validation are present, but blood type is not validated.

Add validation if blood-type input is expected to conform to a defined set.

### `runBlocking` in `MainActivity`

Room access is performed synchronously during Activity startup.

This should eventually be moved to a lifecycle-aware coroutine/background initialization path.

### Status Screen Startup Request

`StatusViewModel` can perform a backend status request during initialization even when the device is offline or registration is unavailable.

Not a contract violation, but it creates avoidable failed requests/noise.

### `AuthInterceptor` Path Matching

The registration exclusion uses a loose path `contains()` check.

Exact path matching would be safer and less error-prone.

---

## 4. API Blueprint Audit

The Android network layer matches the Day 1 API blueprint.

```text
POST /api/v1/auth/device/register
POST /api/v1/sos/batch
GET  /api/v1/sos/{uuid}/status
```

No renamed endpoints, invented Android-owned endpoints, or incorrect HTTP methods were found.

DTO fields match the contract, including:

- `device_id`
- `created_at`
- `location.lat`
- `location.lng`
- `location.accuracy_m`
- `is_quick_sos`
- `emergency_type`
- `severity_hint`
- `people_count`
- `medical_snapshot`
- `custom_message`
- `contact_number`
- `relay_hop_count`
- `last_relayed_at`

Gateway batch fields also match:

- `gateway_device_id`
- `gateway_location`
- `uploaded_at`
- `sos_batch`

---

## 5. Important Contract Compliance That Is Correct

### Device-Side `status` Is Properly Isolated

The implementation correctly separates:

```text
Room:
SOSRequest + device-side status

Backend:
SOSRequest without device-side status
```

`SosRequestDto` does not contain `status`.

The serialization tests also explicitly protect this behavior.

### Gateway Upload Uses the Entire Local Store

The worker uploads:

```text
Room SOS records
       +
relay records
       ↓
distinctBy(uuid)
       ↓
POST /api/v1/sos/batch
```

This matches the requirement that any connected phone can act as a gateway for SOS messages originating from other devices.

### Medical Profile Snapshotting Is Correct

The architecture is:

```text
UserMedicalProfile
      ↓
local Room
      ↓
SOS creation
      ↓
medical_snapshot
```

The medical profile itself is not independently synchronized to the backend.

### TTL Choice

The implementation uses a 72-hour TTL.

The contract specifies approximately 48–72 hours, so 72 hours is valid.

### Authentication Recovery

The 401 flow is substantially improved:

```text
401
 ↓
clear credentials
 ↓
re-register device
 ↓
retry upload
```

This avoids a simple endless retry loop.

---

## 6. Testing Audit

Current tests cover:

- exact emergency enum values
- exact local status values
- TTL value
- rejection of an invalid 7-day TTL
- offline SOS creation
- stable offline device identity
- medical snapshot
- initial SOS state
- absence of `status` from DTO
- absence of `status` from serialized JSON
- DTO JSON field names

However, the Component C acceptance requirements are not fully covered.

Missing or insufficient coverage includes:

- permission grant flow
- permission denial flow
- `GatewaySyncWorker` accepted UUID handling
- `GatewaySyncWorker` duplicate UUID handling
- record retention after successful upload
- 401 recovery
- registration retry behavior
- complete local-store upload behavior
- invalid `people_count`
- invalid age
- blood-type validation
- missing-location upload behavior
- actual worker TTL deletion behavior

The test suite is good but not complete.

---

## 7. Build Verification

Build execution could not be completed in the audit environment.

Attempted:

```bash
./gradlew test
```

The Gradle wrapper was not executable in the extracted ZIP, so:

```bash
bash gradlew test
```

was attempted.

Gradle then attempted to download Gradle 8.9 but the audit environment had no outbound network access:

```text
UnknownHostException: services.gradle.org
```

Therefore:

**Build status: NOT VERIFIED.**

This does not establish that the project fails to compile. It means compilation/tests could not be executed in the audit environment.

Also note that `gradlew` has non-executable file permissions in the extracted repository. The README already instructs developers to run:

```bash
chmod +x gradlew
```

but restoring executable permission in Git would be cleaner.

---

## 8. Documentation Issues

`README.md` is behind the implementation.

It says first-launch detection is still pending, but `MainActivity.kt` already implements first-launch/profile detection.

It also documents Kotlin `2.1.0`, while the actual version catalog uses:

```text
kotlin = "2.0.21"
```

Update the README before committing.

`ApiEndpoints.md` is generally accurate, but its registration lifecycle description should explicitly acknowledge re-registration after a 401.

---

## 9. Merge Recommendation

### Must fix before merge

- [ ] Fix the location permission race in `HomeScreen`.
- [ ] Move development cleartext configuration to the debug variant only.
- [ ] Confirm `device_id` semantics with Backend Core.
- [ ] Verify the Android implementation against the backend's final device-registration behavior.

### Should fix before PR approval

- [ ] Persist onboarding skip state.
- [ ] Add blood-type validation.
- [ ] Expand `GatewaySyncWorker` tests.
- [ ] Test 401 recovery.
- [ ] Test duplicate UUID handling.
- [ ] Test record retention after upload.
- [ ] Test actual TTL cleanup.
- [ ] Review stale pending/in-relay records and TTL behavior with A+B.
- [ ] Avoid `runBlocking` during Activity startup.
- [ ] Update README.
- [ ] Correct Kotlin version in documentation.
- [ ] Restore executable permission on `gradlew`.

### Integration dependencies

- [ ] A+B replace the relay stub.
- [ ] A+B define the persistence/update boundary for relay-received SOS records.
- [ ] Confirm `relay_hop_count` update semantics.
- [ ] Confirm `last_relayed_at` update semantics.
- [ ] Confirm `in_relay` lifecycle.
- [ ] Backend D confirms whether `0,0` is accepted as the gateway-location fallback.
- [ ] Backend D confirms backend SOS status values.

---

## Final Assessment

**Component C is substantially improved and mostly aligned with the master contract.**

The strongest areas are:

- offline-first SOS creation
- Room persistence
- medical-profile snapshotting
- secure device credential storage
- complete gateway batches
- exact Android-owned API paths
- exact DTO field names
- device-side-only `status`
- exact enum values
- 72-hour TTL configuration
- basic 401 recovery
- contract-focused serialization tests

The two issues requiring immediate correction are:

1. **Location permission race**
2. **Release/debug cleartext network-security configuration**

After those are fixed, the remaining major task is to confirm the `device_id` semantics with Backend Core and strengthen the worker/integration test coverage.

**Recommendation: Do not merge yet. Fix the blockers, then re-audit Component C before PR approval.**
