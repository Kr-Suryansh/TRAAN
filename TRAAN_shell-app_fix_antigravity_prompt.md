# TRAAN — Component C / Android Shell
# Corrective Implementation Prompt
# Branch: shell-app

You are the corrective-maintenance agent for Component C of the TRAAN project.

Your task is to FIX the existing `shell-app` branch based on the completed audit, while preserving the existing architecture and respecting the Master Prompt and Day 1 API/contracts as the authoritative sources of truth.

============================================================
1. AUTHORITATIVE SOURCES
============================================================

Before modifying anything, inspect these files in the repository/project:

1. `day1-contracts-and-repo-setup.md`
2. `Updated_antigravity-build-prompts.md`
3. `TRAAN_shell-app_audit.md`

The audit is the defect checklist.

The Master Prompt and Day 1 contracts are authoritative whenever there is any conflict.

If the audit suggests something that conflicts with the Master Prompt or Day 1 contract:
- STOP
- identify the conflict
- follow the authoritative contract
- document the decision

Do not silently change the API contract.

============================================================
2. OBJECTIVE
============================================================

Bring the existing `shell-app` branch to a merge-ready state.

You are NOT being asked to rewrite Component C.

Preserve the existing architecture:

:app
  -> UI, ViewModels, navigation, Hilt wiring

:data
  -> Room, repositories, WorkManager, device preferences

:network
  -> Retrofit, DTOs, API contracts, authentication

:relay
  -> RelayRepository abstraction / integration boundary

Make the smallest clean changes necessary to satisfy the contracts and resolve the audit findings.

Do not replace working architecture merely for stylistic reasons.

============================================================
3. NON-NEGOTIABLE CONTRACT RULES
============================================================

The following rules are mandatory:

A. SOS creation MUST work without network connectivity.

B. SOS creation MUST NOT directly make a network call.

C. `status` is DEVICE-SIDE ONLY.

D. `status` MUST NOT appear in `SosRequestDto`.

E. `status` MUST NOT be serialized into `/api/v1/sos/batch`.

F. Gateway upload must upload the complete local SOS store, not only SOS messages created by the current device.

G. Uploaded/duplicate SOS records must remain locally available until TTL expiry.

H. Medical profile is a local singleton profile.

I. Medical profile is NOT independently synchronized.

J. Medical profile is snapshotted into the SOS at creation time.

K. API endpoint paths must remain exactly as defined by the Day 1 contract.

L. Do not invent additional backend endpoints.

M. Device credentials must remain protected.

N. WorkManager must handle deferred/retryable uploads.

O. Relay integration must remain compatible with the existing `RelayRepository` abstraction.

============================================================
4. P0 — FIX FIRST
============================================================

Do these before all other changes.

------------------------------------------------------------
4.1 REMOVE `status` FROM NETWORK DTO
------------------------------------------------------------

Inspect:

`sih-android/network/.../SosRequestDto.kt`

Remove the `status` field completely.

Do not merely mark it transient.

It must not exist in the network DTO.

The DTO must contain only the contract-defined network fields.

Then inspect every mapper/converter constructing `SosRequestDto`.

Remove:

`status = ...`

from all network payload construction.

Especially inspect:

`GatewaySyncWorker.kt`

------------------------------------------------------------
4.2 FIX THE SERIALIZATION TEST
------------------------------------------------------------

Update the DTO serialization tests so they prove that:

- `status` is absent from `SosRequestDto`
- `status` is absent from serialized JSON
- all required contract JSON keys are correct

The test must actually pass against the implementation.

Do not weaken/remove the test merely to make the build green.

Add a regression test specifically ensuring:

serialized SOS JSON does NOT contain `"status"`.

------------------------------------------------------------
4.3 FIX `ApiEndpoints.md`
------------------------------------------------------------

Remove `status` from the documented SOS batch payload.

Ensure the documentation exactly reflects the actual DTO.

Do not claim "contract exact" compliance if the implementation differs.

------------------------------------------------------------
4.4 FIX TTL
------------------------------------------------------------

The current implementation uses 7 days.

This is incorrect.

The contract specifies approximately 48–72 hours.

Use ONE centralized TTL definition.

Prefer:

72 hours

unless the authoritative contract explicitly specifies another value.

Do NOT leave separate hardcoded TTL values in:

- `SosRepository`
- `GatewaySyncWorker`
- DAO queries
- constants
- documentation

All cleanup behavior must use the same source of truth.

Update README/documentation accordingly.

Do NOT retain the incorrect "7 days" invariant.

------------------------------------------------------------
4.5 FIX 401 RECOVERY
------------------------------------------------------------

Current behavior can produce:

upload
 -> 401
 -> clear credentials
 -> retry
 -> not registered
 -> retry
 -> repeat

Fix this.

A protected API call receiving 401 must have a deterministic credential recovery path consistent with the Day 1 contract.

Use the existing device registration mechanism.

Do NOT invent a token-refresh endpoint.

Do NOT add a new backend API.

Ensure that after credentials are invalidated:

1. registration is re-triggered or otherwise guaranteed to complete
2. credentials are restored
3. the upload can retry
4. WorkManager eventually succeeds or fails correctly

Avoid infinite retry loops.

If registration itself fails, use proper WorkManager backoff.

------------------------------------------------------------
4.6 FIX DEVICE IDENTITY BEFORE REGISTRATION
------------------------------------------------------------

Current behavior generates:

`unregistered_<random UUID>`

for each SOS if registration has not completed.

This is incorrect because the same installation can create multiple SOS messages with different identities.

The app must maintain a stable local installation/device identity for offline SOS creation.

IMPORTANT:

Before implementing this, inspect the authoritative Day 1 contract carefully and determine the intended relationship between:

- locally generated installation/device identity
- `/auth/device/register`
- returned `device_id`

Do NOT invent a new API behavior.

If the contract contains an ambiguity, preserve compatibility with the contract and document the chosen implementation.

The critical invariant is:

Multiple SOS messages created offline by the same installation must NOT receive a new random device identity for every SOS.

============================================================
5. P1 — FIX INTEGRATION ISSUES
============================================================

------------------------------------------------------------
5.1 RESOLVE ROOM VS RELAY STORE OWNERSHIP
------------------------------------------------------------

Inspect:

- `RelayRepository`
- `StubRelayRepository`
- `SosRepository`
- `SosRequestDao`
- `GatewaySyncWorker`

Determine the intended authoritative local SOS store from the Master Prompt and Day 1 architecture.

Do not create a second authoritative database unless the contract explicitly requires it.

The desired architecture should remain conceptually:

Nearby relay
    ↓
relay layer
    ↓
local SOS persistence
    ↓
Room
    ↓
GatewaySyncWorker
    ↓
backend

Relay logic may own propagation, but the data layer should have a clear authoritative persistence model.

When Agent A+B eventually replaces the stub, the architecture must still support:

Phone A creates SOS
Phone B receives SOS
Phone B stores SOS
Phone C receives SOS
Phone C gets Internet
Phone C uploads SOS

Ensure duplicate UUIDs remain idempotent.

Do not implement the real Nearby Connections mesh here unless the existing task explicitly requires it.

------------------------------------------------------------
5.2 FIX LOCATION PERMISSION FLOW
------------------------------------------------------------

Inspect:

- `HomeScreen`
- `HomeViewModel`
- location permission launcher

Current behavior can request permission and immediately create the SOS before the callback completes.

Fix this flow.

Desired behavior:

If permission already exists:
    capture location
    create SOS

If permission does not exist:
    request permission
        if granted:
            capture location
            create SOS
        if denied:
            create SOS without blocking

IMPORTANT:

Location must NEVER become a prerequisite for creating an SOS.

If location cannot be obtained:
the SOS must still be created.

------------------------------------------------------------
5.3 HANDLE UNKNOWN LOCATION CORRECTLY
------------------------------------------------------------

Inspect all uses of:

`0.0`
`0.0`

as location fallback.

Do not silently change the API schema.

Determine from the authoritative contract whether the sentinel is allowed.

If `0,0` must remain because the contract requires non-null coordinates:
- isolate the sentinel behavior
- document it clearly
- ensure it cannot accidentally be treated as a meaningful location inside the Android logic

If the authoritative contract allows nullable values, follow the contract instead.

Do not invent a backend schema change.

------------------------------------------------------------
5.4 IMPROVE CONNECTIVITY VALIDATION
------------------------------------------------------------

Inspect StatusViewModel connectivity detection.

If the purpose is to determine actual validated Internet connectivity, use the Android capability appropriate for validated Internet access rather than treating merely `INTERNET` capability as proof of working Internet.

Do not block offline SOS creation based on this.

============================================================
6. P1 — COMPLETE FUNCTIONAL GAPS
============================================================

------------------------------------------------------------
6.1 SEVERITY HINT
------------------------------------------------------------

Inspect the Master Prompt and contract carefully.

If detailed SOS functionality is required to expose optional severity:

critical
high
medium
low

implement the UI and state flow without breaking quick SOS behavior.

Quick SOS may continue to omit severity if that is what the contract specifies.

Do not make severity mandatory.

------------------------------------------------------------
6.2 FIRST-LAUNCH ONBOARDING
------------------------------------------------------------

Inspect the authoritative requirements for onboarding.

If first-launch onboarding is expected:

- detect first launch/profile absence
- route appropriately
- allow Skip
- never block emergency SOS

Do not make onboarding mandatory for emergency operation.

------------------------------------------------------------
6.3 INPUT VALIDATION
------------------------------------------------------------

Add sensible validation for numeric/user-entered fields.

At minimum inspect:

- age
- people_count
- phone number
- blood type

Do not over-engineer validation.

The goal is to prevent malformed values from entering local state/API payloads.

Do not reject valid emergency submissions merely because optional profile data is incomplete.

============================================================
7. SECURITY HARDENING
============================================================

------------------------------------------------------------
7.1 HTTP LOGGING
------------------------------------------------------------

Inspect `RetrofitClientFactory`.

SOS payloads may contain sensitive medical information.

Do not expose full medical SOS bodies through BODY-level logging unnecessarily.

Prefer less verbose logging or redaction.

Do not log:

- medical conditions
- medications
- allergies
- emergency contact data
- device JWT

------------------------------------------------------------
7.2 NETWORK SECURITY CONFIG
------------------------------------------------------------

Inspect:

- `network_security_config.xml`
- debug/release manifests
- build variants

Correct the invalid/overbroad LAN host configuration.

Keep cleartext HTTP strictly development/debug-only.

Do not weaken release network security.

Do not introduce production cleartext HTTP.

============================================================
8. TESTING REQUIREMENTS
============================================================

Do not stop after making the compiler happy.

Add meaningful tests for the corrected behavior.

At minimum cover:

1. SOS can be created without network.
2. SOS creation does not directly call the API.
3. Medical profile is snapshotted at SOS creation.
4. SOS UUID duplication is idempotent.
5. `status` is absent from network DTO.
6. `status` is absent from serialized JSON.
7. Correct emergency enum values.
8. Correct severity values.
9. Gateway uploads the complete local store.
10. Accepted UUIDs become uploaded.
11. Duplicate UUIDs become uploaded.
12. Records are not deleted immediately after upload.
13. TTL cleanup uses the correct 48–72 hour policy.
14. 401 recovery does not create an infinite retry loop.
15. Registration failure uses retry/backoff correctly.
16. Missing location does not prevent SOS creation.
17. Medical profile remains local and is not independently uploaded.
18. Numeric validation rejects clearly invalid values.

Use the existing testing framework and dependencies.

Do not add unnecessary libraries.

============================================================
9. DOCUMENTATION
============================================================

After implementation, update documentation that became stale.

At minimum inspect:

- `README.md`
- `ApiEndpoints.md`
- `Agent.md`
- `Logs.md`

Documentation must accurately describe:

- status is device-side only
- correct TTL
- current registration behavior
- current relay integration state
- actual consumed endpoints
- known limitations

Do not leave documentation claiming behavior that the code no longer implements.

============================================================
10. CODE QUALITY RULES
============================================================

Follow these rules:

- Prefer small, targeted changes.
- Preserve existing architecture.
- Avoid unnecessary rewrites.
- Avoid duplicate constants.
- Avoid magic numbers.
- Keep API DTOs contract-driven.
- Keep device state separate from network state.
- Keep offline SOS creation independent of network availability.
- Use existing dependency injection.
- Use structured error handling.
- Do not swallow exceptions silently.
- Do not add fake implementations pretending to be production-ready.
- Do not change backend contracts.
- Do not add speculative features.

============================================================
11. VALIDATION AFTER CHANGES
============================================================

After all fixes:

1. Run the Android build.
2. Run unit tests.
3. Run all relevant module tests.
4. Run lint/check tasks available in the project.
5. Inspect generated/serialized DTO behavior.
6. Search the entire repository for:

   `status`
   `7 * 24`
   `ttlDays = 7`
   `unregistered_`
   `0.0`
   `BODY`

Review every occurrence and ensure it is intentional.

7. Search for all construction sites of `SosRequestDto`.
8. Search for all gateway upload paths.
9. Search for all 401 handling.
10. Search for all registration scheduling.
11. Search for all TTL cleanup logic.

Do not declare completion until these searches have been reviewed.

============================================================
12. FINAL ACCEPTANCE CRITERIA
============================================================

The task is complete only if all of the following are true:

[ ] `SosRequestDto` contains no `status`
[ ] serialized SOS payload contains no `status`
[ ] tests prove `status` is absent
[ ] TTL is within the authoritative 48–72 hour requirement
[ ] only one authoritative TTL constant exists
[ ] offline SOS creation works
[ ] same installation has stable device identity before registration
[ ] 401 recovery has a finite, deterministic path
[ ] registration failures use WorkManager retry/backoff
[ ] gateway uploads the full local SOS store
[ ] accepted and duplicate UUIDs are marked uploaded
[ ] records are retained until TTL cleanup
[ ] medical profile is snapshot-only
[ ] relay integration has a clear persistence boundary
[ ] location permission flow is correct
[ ] missing location never blocks SOS creation
[ ] connectivity detection is appropriate
[ ] required severity functionality is implemented if mandated by contract
[ ] input validation is present for relevant fields
[ ] sensitive data is not emitted through verbose HTTP logs
[ ] release cleartext networking remains disabled
[ ] tests cover the critical repository/worker behavior
[ ] README and ApiEndpoints.md match the implementation
[ ] build succeeds
[ ] tests pass
[ ] no new API contract deviations have been introduced

============================================================
13. FINAL REPORT
============================================================

When finished, DO NOT simply say "done".

Produce a concise implementation report containing:

1. Files changed
2. P0 issues fixed
3. P1 issues fixed
4. P2 issues fixed
5. Tests added/modified
6. Build/test results
7. Any remaining known limitations
8. Any contract ambiguities that still require Backend Core / Agent A+B confirmation

For every remaining issue, classify it as:

- BLOCKER
- INTEGRATION DEPENDENCY
- NON-BLOCKING

Do not claim merge-ready if any P0 issue remains.

============================================================
START
============================================================

First inspect the repository, Master Prompt, Day 1 contracts, and the audit.

Then produce a short implementation plan.

After that, implement the fixes directly on the `shell-app` branch.

Do not wait for confirmation between individual fixes unless you encounter a genuine ambiguity in the authoritative contract.
