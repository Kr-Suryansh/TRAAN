# 15-Day Build Roadmap
Disaster Response Coordination Platform · SIH 2026

This is the day-by-day plan for all six of you. Each day lists what every person/pair is working on. Jargon is explained the first time it shows up, and there's a quick-reference glossary right below if you forget what something means later.

**Team key** — used throughout this doc:
- **A + B** — Mesh/Relay Engine (the phone-to-phone offline communication)
- **C** — Android App Shell (the citizen-facing app itself)
- **D** — Backend Core (the server, database, live updates)
- **E** — AI + Resource Intelligence (Gemini, OR-Tools, the mock resource database)
- **F** — Dashboard (the authority-facing web app)

---

## Quick Glossary

| Term | What it means |
|---|---|
| **API / Endpoint** | A specific web address an app sends a request to — like a mailbox with one job, e.g. "save this SOS" or "give me the incident list." |
| **Foreground Service** | An Android background process that keeps running — with a visible notification — even when the app isn't on screen. Needed so a phone keeps relaying messages while its screen is off. |
| **WorkManager** | An Android tool that reliably runs a task later — mainly "try uploading this again once the internet is back." |
| **JWT (JSON Web Token)** | A signed digital pass that proves who's making a request, so the server doesn't need a username/password on every single call. |
| **WebSocket** | A connection that stays open so the server can push new data to the dashboard the instant something happens, instead of the dashboard having to keep asking "anything new yet?" |
| **Duty Cycling** | Turning Bluetooth/Wi-Fi on and off on a timer instead of leaving it running constantly — saves battery. |
| **Dedup (Deduplication)** | Noticing that multiple people reported the same emergency and merging them into one incident instead of showing duplicates. |
| **Clustering** | Grouping nearby reports into a single "incident" on the map, based on location. |
| **PostGIS** | An add-on for our database that understands maps/locations — lets us ask things like "which reports are within 500m of each other." |
| **OR-Tools** | Google's optimization library — the thing that actually does the math for "which resource should go where." |
| **Mesh / Relay / Hop** | How an SOS travels from phone to phone. Each pass from one phone to the next is a "hop." |
| **Manifest** | The short "here's what I already have" list two phones swap before transferring data, so they don't waste time/battery resending things the other phone already has. |
| **Mock data** | Fake but realistic-looking data used to build and test a screen before the real backend is ready. |

---

## How This Works Day to Day

Do a 10-15 minute stand-up every morning: each person says what they finished yesterday, what they're doing today, and anything blocking them. It's short, but with 6 people and this many moving parts, skipping it is how two people end up quietly duplicating work.

Days marked **Checkpoint** are when the whole team stops and actually runs things together, not just individually. Don't skip these even if you feel behind — finding an integration problem on Day 4 costs you an afternoon; finding the same problem on Day 11 costs you the project.

---

## Phase 1 — Contracts & Setup

### Day 1
Whole team locks the schemas (what data looks like) and endpoints (the API addresses) together — see the separate **Day 1 — API Contracts & Repo Setup** document for the full detail. Everyone leaves today with:
- The 4 repos created (`sih-android`, `sih-backend`, `sih-dashboard`, `sih-docs`) with write access for all 6
- Their own piece scaffolded (empty project that at least runs/builds)

Specifically:
- **A + B:** Empty Android Studio project, Nearby Connections library added (this is Google's tool that lets two nearby phones talk over Bluetooth/Wi-Fi without internet)
- **C:** Same Android project, Jetpack Compose (the UI toolkit) and Room (the on-device database) added
- **D:** FastAPI (the backend framework) skeleton running, Docker Compose file that starts Postgres + PostGIS with one command, a working `/health` endpoint
- **E:** Python environment set up, Gemini API key working (test with one simple prompt), OR-Tools installed
- **F:** Vite + React project scaffolded, Leaflet (the map library) installed, a folder of mock JSON files matching the agreed schemas

---

## Phase 2 — Build Against Mocks
*Nobody waits on anybody else this week — everyone builds against the fake data agreed on Day 1.*

### Day 2
- **A + B:** Get the single riskiest thing working first — one phone sends one message to a second nearby phone over Nearby Connections, no internet involved. This is your biggest technical risk, so it goes first, not last.
- **C:** Build the SOS creation screen — location auto-filled, emergency type buttons, optional message box, and a separate one-time "medical profile" setup screen (blood type, conditions, emergency contact) that gets filled in *before* any emergency, not during one.
- **D:** Write the database table definitions matching the Incident and Resource schemas; get Postgres + PostGIS running locally.
- **E:** Start pulling real numbers for the mock resource database — using the public IDRN query tool and District Disaster Management Plan PDFs (see the sourcing method in the contracts doc). Structure what you find into a spreadsheet/JSON file.
- **F:** Build the incident card component and a Leaflet map with a few hardcoded markers — no live data needed yet, just get it looking right.

### Day 3
- **A + B:** Add the manifest exchange — before two phones transfer any SOS data, they swap a short "here's what I already have" list first, so they only send what's actually missing.
- **C:** Connect the SOS form to the local Room database — confirm data survives closing and reopening the app.
- **D:** Build the `POST /sos/batch` endpoint (where a phone uploads its stored messages) with basic checks, returning success/duplicate status.
- **E:** Finish the mock resource seed data. Write the first Gemini prompt for turning a messy pile of reports into a one-line summary — test it by hand with made-up sample reports.
- **F:** Wire the dashboard fully to the mock JSON files — every screen should work end to end using fake data, even though nothing is real yet.

### Day 4 — Checkpoint: Mocked Version Complete
- **A + B:** Test with 3 phones, not 2 — confirm a message can hop from Phone A to Phone C through Phone B, without A and C ever being near each other directly.
- **C:** Add WorkManager so the app automatically retries uploading once the phone is back online.
- **D:** Build the dedup and clustering logic — detect duplicate reports and group nearby ones into a single incident using PostGIS's location math.
- **E:** Finish a first working version of the OR-Tools optimizer using made-up incidents and resources — confirm it outputs something sensible.
- **F:** Confirm every screen renders correctly with mock data: map, incident list, resource panel, situation brief placeholder.
- **End of day — whole team:** Quick check-in. Is anyone blocked heading into integration week? Fix that now, not on Day 5.

---

## Phase 3 — Integration Round 1
*Now the pieces start actually talking to each other instead of using fake data.*

### Day 5
- **A + B:** Wrap the relay logic in a real foreground service, and add duty cycling (scan for nearby phones on a timer, not constantly) so it doesn't drain the battery.
- **C:** Connect the local database's WorkManager job to D's *real* `/sos/batch` endpoint, so a stored message actually leaves the phone once internet is available.
- **D:** Add JWT authentication to the SOS and incident endpoints, so only registered devices/authorities can call them.
- **E:** Connect real Gemini calls for severity scoring, testing against real-shaped data pulled from D's database.
- **F:** Replace the mock JSON with a real fetch to D's `GET /incidents` endpoint.

### Day 6
- **A + B:** Handle real-device friction — permission prompts (Android will ask the user to allow Bluetooth/location), and the newer Android requirement where the app has to explicitly ask the user to turn Bluetooth/Wi-Fi on rather than doing it silently.
- **C:** Build "gateway mode" — the moment a phone gets internet, it should automatically flush its *entire* stored queue (its own SOS plus anything relayed from others), not just its own message.
- **D:** Build the WebSocket server, so the dashboard gets pushed new incidents live instead of having to keep re-asking.
- **E:** Wire the OR-Tools output so it's automatically attached to a new incident as soon as it's created, not just available on request.
- **F:** Connect the WebSocket client so the dashboard updates itself live, no manual refresh.

### Day 7
- **A + B:** Stress-test with 4-5 phones at once. Fix any bugs where messages get duplicated, dropped, or stuck.
- **C:** Polish the citizen-facing UI — make the one-tap SOS button prominent, add a confirmation screen and a status indicator (waiting / relaying / uploaded).
- **D:** Build the dispatch endpoint (authority assigns a resource to an incident), the resource endpoints, and the dashboard summary-stats endpoint.
- **E:** Build the "situation brief" — a short paragraph Gemini regenerates periodically summarizing the overall picture. Test it end to end.
- **F:** Build the incident detail view and the dispatch popup (where an authority picks a resource and confirms sending it).

> **STATUS NOTE (current, A+B):** The 4–5 phone *physical* stress test is **deferred** — only 3 physical
> Android devices are currently available. This is a testing-resource constraint, not a pass/fail result,
> and is **not** claimed as passed/validated. A+B is proceeding with the strongest feasible **3-phone**
> validation (2-phone regression → 3-phone A→B→C → dense/concurrent/recovery → diagnose/fix → document),
> then continuing to Component C integration without waiting for the deferred 4–5 phone run.
> See `PROJECT_HANDOFF.md` §16.

### Day 8 — Checkpoint: Integration Round 1 Complete
- **Whole team:** First real end-to-end run. A real SOS, created on a real phone, should travel through the relay (or upload directly), reach the backend, get processed, and show up live on the dashboard.
- Fix whatever breaks. Something will — that's the point of doing this today instead of assuming it'll work.
- Goal by end of day: it works once, even if it's slow or rough. Polishing comes later.

---

## Phase 4 — End-to-End Testing
*Now you make it work reliably, not just once.*

### Day 9
- **Whole team:** Run the full flow repeatedly with different scenarios — several SOS messages at once, different emergency types, duplicate reports (to confirm dedup actually merges them), and a relay test where phones are genuinely out of direct range and need a middle phone to pass the message along.

### Day 10
- **A + B (lead), whole team supports:** Battery and performance testing. Actually measure how much battery the relay drains over an hour of running, and tune the duty-cycling timing based on what you find. Test the low-battery mode.
- **Everyone else:** Fix bugs found from Day 9's testing, tighten up rough edges on your own piece.

### Day 11 — Checkpoint: Full Dry Run
- **Whole team:** Run both live demos back to back — citizen app sending an SOS on one device, authority dashboard receiving and dispatching it on another. This is the actual demo sequence, run for real, together, for the first time.
- Fix anything that breaks. By end of today, the full pipeline should work reliably, not just occasionally.

---

## Phase 5 — Demo Hardening
*Making it presentable and judge-proof, not just functional.*

### Day 12
- **Whole team:** Write the actual demo script — exactly which phone does what, in what order, said out loud by whom. Pre-load the resource database and a couple of realistic "starter" incidents so the demo doesn't open on a completely empty screen.
- **A + B specifically:** Build a fallback for the live Bluetooth demo — either a recorded backup video, or a "simulated mesh mode" (a software-added delay standing in for real Bluetooth range issues) in case live Bluetooth misbehaves in front of judges, which happens more often than teams expect.

### Day 13 — Checkpoint: Demo-Ready
- **Whole team:** Full dry run of the actual demo script, ideally in the room/setup you'll actually present in. Fix anything that breaks. Test the fallback plan too, not just the happy path.

---

## Phase 6 — Deck & Rehearsal

### Day 14
- **Whole team:** Finalize the pitch deck — the architecture diagrams are already made, so this is mostly the story, tech stack slide, and team slide. Write speaker notes. Decide who presents which section.

### Day 15 — Final
- **Whole team:** Full timed rehearsal (SIH pitches usually have a strict time limit — rehearse against it, not against how long you feel like talking). Prepare for likely judge questions — the most probable hard one is about your real-world assumptions on Bluetooth/Wi-Fi Direct range and how many phones you'd realistically need nearby for the relay to work. Final polish, then stop touching code.
