# Antigravity Prompt — Component F Frontend Polish & Contract Compliance

You are working on the TRAAN repository, specifically **Component F — Authority Dashboard** on the `mahima` branch.

## IMPORTANT SCOPE

I do **NOT** want backend integration at this stage.

Do NOT:
- modify the backend branch
- reconcile Component F against backend implementation quirks
- change backend schemas
- perform D↔F runtime integration
- make the frontend conform to undocumented backend behavior

The current objective is to make the `mahima` dashboard a polished, contract-compliant **standalone frontend** based on the Master Prompt, Component F specification, Day 1 contracts, and API blueprint.

Backend integration will be handled separately later.

---

## 1. READ THESE FIRST

Before modifying code, inspect:

1. `day1-contracts-and-repo-setup.md`
2. The TRAAN Master Prompt
3. The Component F prompt/specification
4. `sih-dashboard/Agent.md`
5. `sih-dashboard/ApiEndpoints.md`
6. All files under `sih-dashboard/src/`
7. All existing tests under `sih-dashboard/src/__tests__/`

Treat these documents as the source of truth.

Do not silently replace their terminology or invent requirements.

---

# 2. PRIMARY OBJECTIVE

Polish `sih-dashboard` so that it is:

- compliant with the Master Prompt
- compliant with the Component F specification
- compliant with the Day 1 contracts
- compliant with the API blueprint
- strongly typed
- internally consistent
- visually polished
- operationally clear
- resilient to loading/error/offline conditions
- demonstrable entirely through mock data
- ready for backend integration later

Do not rewrite the dashboard from scratch.

Preserve working architecture wherever possible.

---

# 3. CONTRACT COMPLIANCE

Audit and correct the frontend contract definitions.

Pay particular attention to:

- `UserMedicalProfile`
- `SOSRequest`
- `Incident`
- `Resource`
- `DispatchRecord`
- `AuthorityUser`
- `StatsSummary`
- `SituationBriefPayload`
- `RecommendedResource`
- dispatch request
- incident status
- incident severity
- WebSocket event payloads

The canonical recommendation structure must remain:

```ts
{
  resource_type: string;
  quantity: number;
  reasoning: string;
}
```

Do not weaken canonical fields simply to support speculative backend variants.

If compatibility logic is useful for future integration, isolate it outside the canonical types.

Do not use `any` to hide contract problems.

---

# 4. API BLUEPRINT

Keep the API layer aligned with the agreed frontend API blueprint.

Audit:

```text
sih-dashboard/src/api/auth.ts
sih-dashboard/src/api/client.ts
sih-dashboard/src/api/incidents.ts
sih-dashboard/src/api/resources.ts
sih-dashboard/src/api/stats.ts
```

The API layer should clearly represent:

- authority authentication
- incident listing
- incident detail
- incident status update
- dispatch
- summary refresh
- resource retrieval
- situation brief
- statistics

Important:

The API layer should represent the **agreed contract/API blueprint**, not the current backend implementation.

Do not change endpoint contracts merely because the current backend may differ.

---

# 5. INCIDENT LIST

Polish the incident list.

It should clearly communicate:

- severity
- status
- area/location
- AI summary
- report count
- estimated people affected
- relevant flags
- time information

Maintain severity priority:

```text
critical
high
medium
low
```

Within the same severity, maintain appropriate recency ordering.

The authority should be able to identify the most urgent incidents immediately.

---

# 6. INCIDENT DETAIL

Ensure Incident Detail contains all information required by Component F:

- incident ID
- location
- severity
- status
- AI summary
- emergency types
- affected population
- flags
- recommended resources
- recommendation reasoning
- contributing SOS reports
- timestamps
- assigned resources

SOS information must remain **text-only**.

Do not introduce:
- photo upload
- image gallery
- audio upload
- audio playback

The authority should be able to understand:

1. what happened
2. how serious it is
3. who is affected
4. why the incident has its priority
5. what resources are recommended
6. why those resources were recommended

---

# 7. HUMAN-IN-THE-LOOP DISPATCH — NON-NEGOTIABLE

The dashboard must NEVER automatically dispatch resources.

Required conceptual flow:

```text
AI / OR-Tools recommendation
        ↓
Authority reviews recommendation
        ↓
Authority explicitly confirms
        ↓
Dispatch API request
```

Preserve and strengthen the current `DispatchModal`.

It must require:

1. resource selection
2. valid quantity
3. explicit authority confirmation
4. explicit confirmation action

The dispatch button must remain unavailable until the required confirmation is satisfied.

Do not allow any WebSocket event, AI recommendation, incident creation, or background process to trigger dispatch automatically.

---

# 8. RECOMMENDATIONS VS ASSIGNMENTS

Make the UI distinction between:

```text
RECOMMENDED
```

and:

```text
ASSIGNED / DISPATCHED
```

unambiguous.

AI/OR-Tools recommendations are guidance only.

A recommendation must never look like an already-authorized dispatch.

---

# 9. RESOURCE PANEL

Polish the Resource Panel.

Clearly display:

- category
- subtype
- total quantity
- available quantity
- deployment status
- custodian agency
- district/location
- contact

Keep the registry explicitly labeled:

```text
Mock IDRN-style Registry
```

Do not imply that this is a live IDRN government integration.

---

# 10. MAP

Polish the Leaflet map.

It should clearly represent:

- incidents
- incident severity
- resources

Maintain consistent severity semantics between:

- incident list
- incident cards
- incident detail
- map markers

Useful interactions should include:

- selecting an incident from the map
- selecting an incident from the list
- showing relevant marker information
- sensible zoom/bounds behavior

Do not add unnecessary map functionality.

---

# 11. SUMMARY STRIP

Ensure the dashboard clearly represents:

- critical incidents
- high incidents
- medium incidents
- low incidents
- estimated affected population
- available resources
- deployed resources
- new incidents in the last 15 minutes

Optimize this for rapid operational comprehension.

---

# 12. SITUATION BRIEF

The Situation Brief must clearly display:

- brief text
- update timestamp
- loading state
- empty state
- error state

Keep it informational.

Do not invent new AI capabilities.

---

# 13. WEBSOCKET ARCHITECTURE

Preserve the existing WebSocket architecture.

The frontend should support:

```text
incident_created
incident_updated
incident_dispatched
resource_updated
situation_brief_updated
```

Verify that:

- `incident_created` updates incident state without page reload
- `incident_updated` updates the corresponding incident
- `incident_dispatched` updates dispatch-related state appropriately
- `resource_updated` updates resources
- `situation_brief_updated` updates the situation brief
- reconnect behavior works
- connection state is visible
- malformed/unknown messages do not crash the dashboard

Do NOT replace WebSockets with polling.

---

# 14. LOW-NETWORK / FAILURE UX

This dashboard is intended for disaster-response operations.

Every major data-driven component should have appropriate:

- loading state
- empty state
- error state
- disconnected/offline indication where applicable

The interface must not silently look broken when the network fails.

`ConnectionStatus` should make the live-feed state obvious.

Reconnect behavior should be understandable to the authority user.

---

# 15. AUTHENTICATION UX

Audit and polish:

```text
src/context/AuthContext.tsx
src/pages/LoginPage.tsx
```

Ensure:

- authority login is clearly identified
- validation is sensible
- loading state exists
- errors are understandable
- logout works
- expired sessions are handled cleanly
- passwords are not persisted insecurely

Do not add unnecessary authentication mechanisms.

---

# 16. MOCK MODE

Mock mode is intentionally important because backend integration is NOT being performed yet.

Preserve:

```text
VITE_MOCK_MODE
```

The dashboard must be fully demonstrable using mock data.

Audit:

```text
src/mocks/index.ts
```

against the canonical frontend contracts.

Every mock object should satisfy the TypeScript types.

Do not remove mock mode.

---

# 17. UI / UX POLISH

This is a major part of the task.

Make the dashboard look and behave like a serious emergency operations / authority command center rather than a generic React dashboard.

Improve:

- visual hierarchy
- typography
- spacing
- information density
- severity indicators
- status indicators
- cards/panels
- primary/secondary actions
- disabled states
- hover/focus states
- modal design
- responsive behavior
- empty states
- error states
- map/list/detail consistency

Prioritize rapid information comprehension.

Avoid decorative UI that competes with operational information.

Do not unnecessarily redesign working functionality.

---

# 18. ACCESSIBILITY

Perform a practical accessibility pass.

Check:

- form labels
- button labels
- checkbox labels
- keyboard interaction
- focus states
- semantic elements
- useful ARIA labels where appropriate
- sufficient contrast
- understandable error messages

Do not introduce excessive accessibility abstractions.

---

# 19. RESPONSIVE BEHAVIOR

The dashboard is primarily an authority desktop/tablet interface.

Ensure it remains usable at reasonable desktop and tablet widths.

Prevent:

- overlapping panels
- clipped controls
- unusable modals
- broken map layouts
- unreadable incident cards

Do not optimize primarily for mobile.

---

# 20. CODE QUALITY

Clean up obvious technical debt discovered during the audit.

Examples:

- remove genuinely unused Vite starter assets
- remove genuinely unused CSS
- avoid duplicated state fetching
- extract reusable utilities where appropriate
- keep components focused
- preserve strict TypeScript
- remove unnecessary dependencies if safe

Do not perform broad refactoring without a clear benefit.

Do not modify unrelated TRAAN components.

---

# 21. TESTS

Review and improve:

```text
src/__tests__/DispatchModal.test.tsx
src/__tests__/WebSocketIntegration.test.tsx
src/__tests__/apiErrors.test.tsx
src/__tests__/incidentUtils.test.ts
```

Tests should validate frontend behavior against the agreed contract.

At minimum verify:

### Dispatch

- disabled before confirmation
- confirmation enables dispatch
- no automatic dispatch
- cancel does not dispatch

### WebSocket

- `incident_created`
- `incident_updated`
- `incident_dispatched`
- `resource_updated`
- `situation_brief_updated`
- reconnect
- malformed messages
- unknown events

### Incident behavior

- severity ordering
- recency ordering
- required flags
- contract field names

### Error handling

- REST errors
- auth expiry
- retry behavior
- empty states

Do not write tests that merely duplicate the implementation instead of testing actual production utilities/components.

---

# 22. DOCUMENTATION

Update:

```text
sih-dashboard/Agent.md
sih-dashboard/ApiEndpoints.md
sih-dashboard/README.md
```

Documentation must accurately describe the polished frontend and agreed contract.

Do not rewrite the API documentation around current backend quirks.

Make the project status clear:

```text
Component F is implemented against the agreed frontend/API contract.
Backend integration and D↔F runtime verification are intentionally deferred.
```

Correct outdated terminology such as "Component 5" where the project specification identifies this as Component F.

---

# 23. VALIDATION

Run from `sih-dashboard`:

```bash
npm install
npm run build
npm run test
```

Run lint if available.

The dashboard must:

- compile successfully
- pass tests
- run in mock mode
- not require the backend to demonstrate the frontend

Do NOT make backend availability a prerequisite for this task.

---

# 24. FINAL REPORT

When finished, provide:

## Files Changed

Every modified file and why.

## Contract Compliance

Explain compliance with:

- Master Prompt
- Component F prompt
- Day 1 contracts
- API blueprint

## UI/UX Improvements

Summarize the major polish performed.

## Safety

Explicitly confirm:

- no automatic dispatch
- authority confirmation remains mandatory
- recommendations remain recommendations

## Validation

Report:

```text
Build: PASS/FAIL
Tests: PASS/FAIL
Lint: PASS/FAIL/N/A
Mock mode: PASS/FAIL
```

## Backend Integration

Explicitly state:

```text
Backend integration was NOT performed.
```

Do not claim backend compatibility merely because the frontend tests pass.

## Final Status

Use exactly one:

```text
GREEN — COMPONENT F FRONTEND CONTRACT-COMPLIANT
```

or:

```text
YELLOW — NEEDS POLISH
```

or:

```text
RED — NOT COMPLIANT
```

Only use GREEN if the frontend genuinely satisfies the supplied specifications/contracts and validation passes.

---

## FINAL INSTRUCTION

Start by auditing the existing `mahima` implementation against the specified documents.

Then implement the necessary **frontend-only** corrections and polish.

Do not integrate with the backend yet.
