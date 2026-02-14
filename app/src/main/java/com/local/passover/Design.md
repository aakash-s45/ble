# Project "Passover" Technical Design Document

## 1. Project Overview

**Passover** is an open-source, private toolset for seamless data synchronization between macOS and Android, utilizing a **Local-First WebSocket architecture** with an **Encrypted Internet Relay fallback**.

### Phase 1 Focus (Current)

- Secure QR-based Pairing
- End-to-End (E2E) Encrypted Communication
- Bidirectional Text Clipboard Synchronization
- Local Network Discovery (mDNS)

---

## 2. System Architecture

### 2.1 Networking Model

The system operates on a **Client–Server hierarchy**:

- **macOS (Server)**  
  Acts as the primary hub. It runs a WebSocket server locally and maintains a client connection.

- **Android (Client)**  
  Acts as the initiator. It searches for the Mac locally first.

- **Relay (Proxy)** *(not yet implemented)*  
  A stateless Node.js WebSocket proxy that bridges two clients based on a `pairID`.

### 2.2 Protocol Stack

- **Transport:** WebSockets (WS/WSS) for reliable message framing and firewall traversal
- **Security:** AES-256-GCM (Authenticated Encryption)
- **Serialization:** Google Protocol Buffers (Protobuf)
- **Discovery:** mDNS (Bonjour) for local IP resolution
- **Proximity Gating:** BLE Advertisements *(not yet implemented, Phase 2)*

---

## 3. Data Protocol (Protobuf)

```proto
syntax = "proto3";

package com.local.passover;

message Message {
  int64 timestamp_ms = 1;

  oneof payload {
    ClipboardMessage clipboard = 2;
    MediaControlMessage mediaControl = 3;
    MediaPlaybackInfo playbackInfo = 4;

    FileHeader fileHeader = 5;
    FileChunk fileChunk = 6;
    FileStatusRequest statusRequest = 7;
    FileStatusResponse statusResponse = 8;
    FileTransferError error = 9;

    VideoStreamChunk videoChunk = 10;
    MediaArt artwork = 11;
    Heartbeat heartbeat = 12;
    Identity identity = 13;
    QRPayload qrPayload = 14;
  }
}

message Identity {
  string deviceId = 1;
}

message QRPayload {
  string deviceId = 1;
  string key = 2;
}

message ClipboardMessage {
  string content = 1;

  enum ClipboardContentType {
    TXT = 0;
    IMG = 1;
  }

  ClipboardContentType type = 2;
}

message MediaControlMessage {
  enum Action {
    PLAY_PAUSE = 0;
    NEXT = 1;
    PREVIOUS = 2;
    VOLUME_SET = 3;
  }

  Action action = 1;
  float volume = 2;
}

message MediaArt {
  bytes content = 1;
}

message MediaPlaybackInfo {
  bool playbackRate = 1;
  double duration = 2;
  double elapsed = 3;
  string title = 4;
  string album = 5;
  string artist = 6;
  string bundle = 7;
  float volume = 8;
}

message FileHeader {
  string fileId = 1;
  string filename = 2;
  int64 filesizeBytes = 3;
  bytes metadata = 4;
}

message FileChunk {
  string fileId = 1;
  int64 offset = 2;
  bytes data = 3;
}

message FileStatusRequest {
  string fileId = 1;
}

message FileStatusResponse {
  string fileId = 1;
  int64 bytesRecieved = 2;
}

message FileTransferError {
  string fileId = 1;
  string error = 2;
}

message Heartbeat {
  bool isMediaPlaying = 1;
  bool isMacUserActive = 2;
}

message VideoStreamChunk {
  bytes frame = 1;
  int64 presentationTimestampUs = 2;
}
```

---

## 4. Security & Pairing Flow

### 4.1 Pairing Mechanism

**Mac Side:**
- Generates a 32-byte SymmetricKey
- Generates a persistent UUID
- Displays a QR code: `{"id": "MAC_UUID", "key": "BASE64_KEY"}`

**Android Side:**
- Scans QR code
- Verifies key by:
  - Android connects via WebSocket using the scanned Key
  - Android sends an encrypted Identity message containing its deviceId
  - macOS attempts to decrypt. If successful, it verifies the deviceId matches the intended target
  - macOS responds with its own encrypted Identity message
  - Android decrypts and verifies the macOS deviceId

**Key Wrapping Architecture:**
- A Master Key is generated and stored in the hardware-backed Android Keystore
- The session SymmetricKey (from QR) is encrypted (wrapped) using the Master Key
- The wrapped key blob is stored in Jetpack DataStore (Preferences)

### 4.2 E2E Encryption

- **Algorithm:** AES-256-GCM
- **Process:**
  1. Serialize inner message (e.g., ClipboardMessage) to bytes
  2. Encrypt bytes using the SymmetricKey and a unique IV
  3. Prepend IV to ciphertext
  4. Send over WebSocket

---

## 5. Detailed Implementation Design

### 5.1 macOS Module (Swift / SwiftUI)

- **PairingManager** — Manages encryption key and device ID lifecycle. Stores secrets in the macOS Keychain.
- **NetworkServer** — Uses NWListener for local WebSocket connections. Maintains a WebSocket client connection to the Relay.
- **ClipboardMonitor** — Polls NSPasteboard every 500ms or 1s. Heuristic: If activeApp != "Xcode" and changeCount increments, trigger sync *(future)*.
- **CryptoService** — Uses CryptoKit for hardware-accelerated AES-GCM.

### 5.2 Android Module (Kotlin / Compose)

#### Process Hierarchy

- **PassoverAccessibilityService** — Root process. Registered as an Android AccessibilityService. Survives across reboots (if enabled by user in system settings). Responsible for:
  - Starting MainService on boot / service connect
  - Detecting copy/cut user actions (click events, long-press on configured apps, "copied" toast notifications)
  - Launching ClipboardActivity to read clipboard data
  - Managing screen on/off lifecycle (pauses/resumes sync)

- **MainService (Foreground Service)** — Child of PassoverAccessibilityService. Manages:
  - WebSocket connection and reconnection
  - mDNS discovery via DnsServiceManager
  - Incoming message processing (clipboard updates from Mac)
  - Persistent notification ("Keeping device in sync" / "Sync paused")

- **ClipboardActivity (Ghost Activity)** — Transparent, transient activity (Theme.Ghost). Workaround for Android 10+ background clipboard access restrictions.
  - Flow: PassoverAccessibilityService detects copy → Launches ClipboardActivity → Activity gains focus → Reads clipboard → Sends text to ConnectionRepository → Closes immediately

#### Boot Chain

```
Reboot → System re-enables AccessibilityService
       → onServiceConnected()
       → startMainService()
       → MainService.onCreate()
       → Reads credentials from DataStore
       → Connects with saved credentials (mDNS → WebSocket → Identity handshake)
```

#### Screen On/Off Lifecycle

PassoverAccessibilityService registers a BroadcastReceiver for screen events:
- **Screen off** → Sends PAUSE to MainService → WebSocket closes, notification shows "Sync paused"
- **Screen on** → Sends RESUME to MainService → Reconnects with saved credentials, notification shows "Keeping device in sync"

This saves battery and resources when the phone is in a pocket or the screen is off.

#### Clipboard Sync Flow (Text Only — Phase 1)

**Outgoing (Android → Mac):**
1. PassoverAccessibilityService detects a copy/cut event
2. Launches ClipboardActivity (ghost/transparent)
3. ClipboardActivity reads clipboard text, wraps in ClipboardMessage (TXT type)
4. Encrypts via ConnectionRepository → sends over WebSocket

**Incoming (Mac → Android):**
1. MainService collects incoming messages from ConnectionRepository
2. ClipboardHandler receives ClipboardMessage, sets device clipboard via ClipboardManager

**Planned: Debounce Logic**
- Start a 1.5s timer on copy detection
- Reset timer if another copy occurs within the window
- Send only the final clipboard content
- Prevents unnecessary network traffic from rapid copy operations

#### Key Components

- **ConnectionRepository** — Singleton. Manages the full connection lifecycle: mDNS discovery → WebSocket connect → Identity handshake → encrypted message send/receive. Stores credentials in DataStore, wraps/unwraps keys via KeystoreManager.
- **KeystoreManager** — Singleton. Handles AES-256-GCM encrypt/decrypt, Master Key generation in Android Keystore, session key wrapping/unwrapping.
- **DnsServiceManager** — Singleton. Uses NsdManager to discover the Mac's `_passover._tcp` mDNS service, filtering by deviceId in the TXT record.
- **WebSocketClient** — Singleton. OkHttp-based WebSocket client. Exposes message and connection state flows.

### 5.3 Relay Module (Node.js) *(not yet implemented)*

- Map<PairID, WebSocket[]> storage
- On message arrival:
  - Forward message to all sockets in the same PairID except sender

---

## 6. Connection Lifecycle & Optimization *(not yet implemented)*

### 6.1 The "Upgrade" Path

- Android connects to Relay if mDNS fails
- Android continues low-power background mDNS scanning
- If Mac is discovered:
  - Open local WebSocket
  - Authenticate locally
  - Close Relay connection to save latency and data

### 6.2 Resource Saving

- **Mac Heartbeat** — Sends user activity and media playback state
- **Android Idle Mode:**
  - If screen is OFF and Mac heartbeat indicates idle: close WebSocket, enter BLE Proximity Scan mode
  - Wake Up Conditions: BLE signal detected, user turns screen ON

---

## 7. Build Roadmap

**Phase 1: Foundations (Current)**
- ✅ macOS: QR display & Keychain storage
- ✅ Android: QR scanner & Keystore storage
- ✅ Networking: mDNS discovery & local WebSocket handshake
- ✅ Security: AES-GCM encryption pipeline
- ✅ Feature: Bidirectional **text-only** clipboard sync
- ✅ Android: AccessibilityService-based clipboard detection
  - Detect copy/cut via click events and toast notifications
  - Launch transparent ClipboardActivity to read clipboard
  - Send text clipboard data to macOS
- ✅ Lifecycle: Screen on/off auto pause/resume sync

**Phase 2: Mobility & Reliability** *(not yet implemented)*
- Relay: Deploy Node.js server
- Android & macOS: Add Relay fallback logic
- Stability: Handle network changes (Wi-Fi ↔ LTE)
- Feature: Media controls (Play / Pause / Volume)
- Feature: Image clipboard support
- Feature: Clipboard debounce logic

**Phase 3: High Bandwidth** *(not yet implemented)*
- Files: Resumable chunk-based transfer (on-demand)
- Video: Android camera stream → macOS virtual camera (CMIO)
- Efficiency: BLE Proximity Gating
