# Component 1 — Mesh / Relay Engine Overview

This document provides a simple, practical overview of Component 1 for all team members.

## A. What Component 1 is responsible for
Component 1 is the **Mesh / Relay Engine**. It allows phones to connect to each other directly using offline technologies like Bluetooth and Wi-Fi Direct (via Google's Nearby Connections API) to exchange emergency messages without needing an internet connection.

## B. What problem the relay component solves
During a major disaster, cell towers and internet infrastructure often fail. The relay engine solves this by turning users' phones into a decentralized mesh network. As people walk around, their phones automatically discover each other and securely sync SOS distress signals. Eventually, one of those phones will reach an area with working internet and upload the collected SOS messages to the rescue teams.

## C. What was completed on Day 1
On Day 1, we successfully set up the foundational project structure and data contracts:
- Created the Android multi-module project.
- Configured the necessary library dependencies (like Nearby Connections and Kotlinx Serialization).
- Defined the exact data schemas for emergency messages based on the team's master contract.
- Set up the public API interfaces that other components will use to interact with the relay engine.

## D. The Module Structure
The project is divided into four main modules to keep the code organized and prevent messy dependencies:
- **`:app`**: The main application module where the user interface and navigation live. (Stubbed for now).
- **`:relay`**: The core library module containing our Mesh / Relay Engine. This is where all the offline networking magic happens.
- **`:data`**: The local storage module responsible for saving things to the device using a local database (Room). (Stubbed for now).
- **`:network`**: The shared module responsible for communicating with the backend servers over the internet. (Stubbed for now).

## E. Important Classes and Their Purpose
- **`SOSRequest`**: The main data payload representing an emergency distress signal (contains location, emergency type, etc.).
- **`UserMedicalProfile`**: A read-only snapshot of the victim's medical information (blood type, allergies) attached to the SOS message.
- **`SosLocation`**: The GPS coordinates (`lat`, `lng`) and accuracy of the emergency.
- **`RelayManifest`**: A lightweight summary of which SOS messages a phone currently has. Used when two phones connect to quickly figure out which messages they need to exchange.
- **`RelayApi`**: The public interface that the rest of the app uses to talk to the relay engine (e.g., to turn it on or off).
- **`RelayDataSource`**: An internal boundary interface. It describes how the relay engine reads and writes SOS messages to the database without the relay engine having to know anything about the actual database technology.
- **`RelayManager`**: The class that actually implements the offline mesh operations.

## F. The Data Flow
1. When a user presses the SOS button, an `SOSRequest` is created and saved to the local database.
2. The relay engine constantly broadcasts its presence in the background.
3. When two offline phones connect, they exchange their `RelayManifest` to compare notes.
4. They figure out which messages the other phone is missing and exchange only those `SOSRequest`s.
5. Received messages are saved to the local database to be passed along to the next person.

## G. The Public RelayApi
The `RelayApi` provides three simple commands for the app to use:
- `startRelay()`: Turns on the offline mesh networking.
- `stopRelay()`: Turns off the offline mesh networking.
- `getRelayStore()`: Allows the app's UI to observe the list of all SOS messages currently held on the device.

## H. Difference Between RelayApi and RelayDataSource
- **`RelayApi`** is for the **outside** world to talk to the relay engine (e.g., the UI telling the relay to start).
- **`RelayDataSource`** is for the **inside** of the relay engine to talk to the database (e.g., the relay engine asking for the list of saved SOS messages to send to someone else).

## I. Database Ownership (Room)
The **`:data`** module owns the Room database. The `:relay` module does not use Room directly to maintain strict separation of concerns. If `:relay` depended on Room, the architecture would become tangled. Instead, `:relay` just defines what it needs via the `RelayDataSource` interface, and the `:data` module handles the actual database work.

## J. Current Implementation Status
Days 1–7 are complete and committed. Integration Stages 0a–5 (A+B ↔ Component C merge) are complete
but uncommitted — pending manual commit through GitHub Desktop.

**Day 2 physical proof**: Demonstrated on two real Android phones (Pixel 8 and CPH2793/Oppo) with no internet connection.

**Day 3 physical proof**: Demonstrated on two real physical Android phones running different OS versions — Phone A (Pixel 8 running Android 17, ADB `PIXEL8_DEV_01`) and Phone B (Vivo running Android 15, ADB `VIVO_DEV_02`) with no internet connection. Bidirectional `RelayManifest` exchange (54 bytes, `0x01` type prefix), manifest decoding, and 0-diff calculation via `dataSource.getMissingSos()` were verified on physical hardware (recovered automatically from a transient 8012 I/O error during connection setup). Non-empty SOS transfer & persistence await Component C / Room integration.

**Day 4 physical proof (3-phone A → B → C):** Demonstrated multi-hop relay on three physical phones with no internet. Phone A created a test SOS (UUID `153f5b02-6855-4e4c-b646-997fdc6e9638`, `relayHopCount=0`, `status=PENDING_LOCAL`). Phone B received it with `relayHopCount=1` / `IN_RELAY` and saved it, then forwarded it to Phone C, which received it with `relayHopCount=2` / `IN_RELAY` and saved it. RelayManifest/UUID synchronization worked — peers already holding the UUID reported no missing SOSRequests (no blind retransmission). See `Logs.md` for the full record. SOS delivery is phone-to-phone only; internet/cloud delivery is not part of Day 4.

**Day 5–7**: Foreground service + duty cycling, Room persistence + permission preflight, diagnostic instrumentation + 3-phone stress validation — all complete and committed. See `Logs.md` and `walkthrough.md`.

**Integration Stages 0a–5 (A+B ↔ Component C)**: Selective file extraction from Component C's repo into the validated A+B codebase. Build files merged (Compose, Hilt, KSP, Retrofit, Moshi, WorkManager). Network module created (full Retrofit HTTP layer). Data module extended (Hilt DI, repositories, WorkManager workers). Relay seam added (`RelayRepository` interface). Compose app shell created (Home/Onboarding/Status screens, Material3 theme, navigation). Android Studio build succeeded; app launched and UI screens rendered correctly. All A+B relay code preserved (zero diff). Pending manual commit.

## K. What is NOT Implemented Yet
- **Stage 6A: Single-device physical verification** — PASSED (onboarding, SOS, location, persistence, status)
- **Stage 6B: Relay integration + multi-device + backend testing** — NOT YET COMPLETED
- **Final documentation cleanup (Stage 7)**
- Low-battery throttle mode (Day 10)
- Fallback simulation demo mode (Day 12)
- TTL (Time-to-Live) cleanup for expiring old messages (Later)
- 4–5 physical-phone stress test (deferred — only 3 devices available)

## L. Building the Project
You don't need to install Java globally. Android Studio comes with a bundled Java Runtime (JBR). You can build the project using the included Gradle wrapper by pointing to that JBR:

**Windows PowerShell:**
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat build
```

## M. Verification Status
- **Build**: Successfully compiled all four modules (`./gradlew :relay:test :relay:build` passed in 18s).
- **Unit Tests**: Passed successfully (including `RelayModelsTest` and `RelayPayloadCodecTest`).
- **Lint**: Passed with no blocking errors.
- **Gradle Wrapper**: Successfully generated via JBR.
- **Physical Device Test (Day 2)**: Confirmed end-to-end P2P SOSRequest transmission on two real Android phones (Pixel 8 → CPH2793) via Nearby Connections with no internet.
- **Physical Device Test (Day 3)**: Confirmed bidirectional `RelayManifest` exchange, manifest decoding, and UUID diff execution on two real Android phones across different OS versions (Pixel 8 / Android 17 ↔ Vivo / Android 15) over Nearby Connections with no internet. Non-empty SOS transfer & persistence await Component C / Room integration.
- **Physical Device Test (Day 4)**: Confirmed 3-phone A → B → C multi-hop relay on physical phones with no internet. Hop accounting verified: A created the SOS at `relayHopCount=0`/`PENDING_LOCAL`, B received it at `relayHopCount=1`/`IN_RELAY`, and C received it at `relayHopCount=2`/`IN_RELAY`; both B and C saved it via `saveSosMessages()`. RelayManifest/UUID synchronization prevented blind retransmission to peers that already held the UUID. Day 4 is COMPLETE.
- **Integration Build (Stages 0a–5)**: Android Studio build succeeded. App launched on device; Home, Onboarding, Status Compose screens appeared and rendered correctly. All A+B relay code preserved. Namespace fix applied (`com.sih.android` → `com.sih.app`). 75 JVM tests pass (64 relay + 11 data). Pending manual commit.

## N. What the Next Developer Should Know
- **Source of Truth**: Always refer to `day1-contracts-and-repo-setup.md` for schemas. Do not change field names or types (like changing `Float` to `Double` or renaming `UserMedicalProfile`) without a team agreement.
- **Dependency Rules**: The `:relay` module must NEVER depend on `:data` or `:network`. Keep it completely isolated from Room and internet/Retrofit logic.
- **No Internet**: The relay engine does NOT upload data to the internet. It only passes data between phones and the local database. The `:network` component is responsible for internet uploads.
