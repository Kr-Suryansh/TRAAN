# TRAAN — Android Disaster Response App

**Component A/B/C · SIH 2026**

This is the Android client for the Disaster Response Coordination Platform. 

## Project Overview

TRAAN operates as a decentralized offline mesh network. When cell towers and internet fail during a disaster:
1. Phones discover each other via Google Nearby Connections (Bluetooth/Wi-Fi Direct).
2. Devices securely sync and relay SOS distress signals (epidemic routing).
3. When any device in the mesh regains internet connectivity, it acts as a **gateway**, automatically uploading all collected SOS messages to the backend for authorities to act on.

## Module Structure

The project uses a clean, multi-module architecture:
- **`:app`**: The main application shell and UI.
- **`:relay`**: The core Mesh / Relay Engine. Handles offline networking, manifest exchange, and duty-cycled scanning in a Foreground Service.
- **`:data`**: Local persistence layer using Room Database. Stores both user-created and relayed SOS messages.
- **`:network`**: Retrofit client for communicating with the backend gateway.

## Integration Status

✅ **Fully Integrated with Backend (Component D)**
- The offline mesh relay (`:relay`) successfully captures and propagates SOS messages.
- `RoomRelayRepository` bridges the offline mesh data into the local Room database (`:data`).
- `GatewaySyncWorker` successfully flushes the local queue (both own and relayed messages) to the backend (`192.168.1.204:8000/api/v1/sos/batch`) when internet is restored.
- Successfully verified with 3 physical devices (A → B → C multihop routing).

## Getting Started

1. **Configure Backend IP**:
   In `app/build.gradle.kts`, set `BASE_URL` to your backend's local IP address (or `10.0.2.2` for the emulator).
2. **Build and Install**:
   ```bash
   ./gradlew :app:assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
3. **Permissions**:
   The app requires location, Bluetooth, and nearby devices permissions to operate the mesh network.

## Deep-Dive Documentation

For detailed technical histories, forensic logs, and step-by-step guides from the initial development phases, see the `archive/` folder at the root of the project:
- `archive/TRAAN_PROJECT_HANDOFF.md`: In-depth state of the relay engine.
- `archive/TRAAN_walkthrough.md`: Step-by-step development logs.
- `archive/TRAAN_INTEGRATION_GUIDE.md`: The backend integration manual.
- `archive/TRAAN_Component1_Overview.md`: High-level explanation of the Nearby Connections logic.
