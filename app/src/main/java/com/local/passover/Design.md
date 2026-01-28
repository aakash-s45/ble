# Project "Passover" Technical Design Document

## 1. Project Overview

**Passover** is an open-source, private toolset for seamless data synchronization between macOS and Android, utilizing a **Local-First WebSocket architecture** with an **Encrypted Internet Relay fallback**.

### Phase 1 Focus (Current)

- Secure QR-based Pairing
- End-to-End (E2E) Encrypted Communication
- Bidirectional "Clever" Clipboard Synchronization
- Local Network Discovery (mDNS)

---

## 2. System Architecture

### 2.1 Networking Model

The system operates on a **Client–Server hierarchy**:

- **macOS (Server)**  
  Acts as the primary hub. It runs a WebSocket server locally and maintains a client connection.

- **Android (Client)**  
  Acts as the initiator. It searches for the Mac locally first.

- **Relay (Proxy)** *(future phases)*  
  A stateless Node.js WebSocket proxy that bridges two clients based on a `pairID`.

### 2.2 Protocol Stack

- **Transport:** WebSockets (WS/WSS) for reliable message framing and firewall traversal
- **Security:** AES-256-GCM (Authenticated Encryption)
- **Serialization:** Google Protocol Buffers (Protobuf)
- **Discovery:** mDNS (Bonjour) for local IP resolution
- **Proximity Gating:** BLE Advertisements (used in Phase 2 for power saving)

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
### 4. Security & Pairing Flow
####  4.1 Pairing Mechanism
- Mac Side
- Generates a 32-byte SymmetricKey
- Generates a persistent UUID
- Displays a QR code:
- {"id": "MAC_UUID", "key": "BASE64_KEY"}
- Android Side
- Scans QR
-Verifies key by
-Android connects via WebSocket using the scanned Key.
-Android sends an encrypted Identity message containing its deviceId.
-macOS attempts to decrypt. If successful, it verifies the deviceId matches the intended target.
-macOS responds with its own encrypted Identity message.
-Android decrypts and verifies the macOS deviceId.
- "Key Wrapping Architecture:
- A Master Key is generated and stored in the hardware-backed Android Keystore.
- The session SymmetricKey (from QR) is encrypted (wrapped) using the Master Key.
- The wrapped key blob is stored in Jetpack DataStore (Preferences)."
#### 4.2 E2E Encryption
- Algorithm: AES-256-GCM
- Process
- Serialize inner message (e.g., ClipboardMessage) to bytes
- Encrypt bytes using the SymmetricKey and a unique IV
- Wrap IV and ciphertext into the WrapperMessage
- Send over WebSocket
### 5. Detailed Implementation Design
#### 5.1 macOS Module (Swift / SwiftUI)
- PairingManager
- Manages encryption key and device ID lifecycle. Stores secrets in the macOS Keychain.
- NetworkServer
- Uses NWListener for local WebSocket connections
- Maintains a WebSocket client connection to the Relay
- ClipboardMonitor
- Polls NSPasteboard every 500ms or 1s
- Heuristic: If activeApp != "Xcode" and changeCount increments, trigger sync (future)
- CryptoService
- Uses CryptoKit for hardware-accelerated AES-GCM
#### 5.2 Android Module (Kotlin / Compose)
- Service (Foreground Service)
- Core application brain. Maintains WebSocket connection and prevents background termination.
- DiscoveryManager
- Uses NsdManager to find the Mac IP
- Filters by UUID in the mDNS TXT record
- ClipboardMonitor
- "Uses an AccessibilityService to detect 'Copy' or 'Cut' user interactions (clicks/long-presses) or Toast notifications.
- Constraint Workaround: Since Android 10+ restricts background clipboard access, the Service launches a transparent, transient ClipboardActivity (Theme.Ghost).
- Flow: Service detects copy -> Launches Activity -> Activity gains focus -> Reads Clipboard -> Sends to Repository -> Closes immediately."
- Uses OnPrimaryClipChangedListener
- Debounce logic:
- Start 1.5s timer on copy
- Reset timer if another copy occurs
- Send final clipboard content only
- NetworkClient
- Manages reconnection logic
- Falls back to Relay URL if mDNS fails (future plan)
#### 5.3 Relay Module (Node.js) (future plan)
- Map<PairID, WebSocket[]> storage
- On message arrival:
- Forward message to all sockets in the same PairID except sender
### 6. Connection Lifecycle & Optimization

#### 6.1 The "Upgrade" Path
- Android connects to Relay if mDNS fails
- Android continues low-power background mDNS scanning
- If Mac is discovered:
- Open local WebSocket
- Authenticate locally
- Close Relay connection to save latency and data

#### 6.2 Resource Saving
- Mac Heartbeat
- Sends user activity and media playback state
- Android Idle Mode
- If screen is OFF and Mac heartbeat indicates idle:
- Close WebSocket
- Enter BLE Proximity Scan mode
- Wake Up Conditions
- BLE signal detected
- User turns screen ON

### 7. Build Roadmap

Phase 1: Foundations (The "Core")
-  macOS: QR display & Keychain storage
-  Android: QR scanner & Keystore storage
-  Networking: mDNS discovery & local WebSocket handshake
-  Security: AES-GCM encryption pipeline
-  Feature: Bidirectional clipboard sync with debouncing
- Android Clipboard Enhancements
- Monitor clipboard using an Accessibility Service (cut/copy events)
- On detection:
- Launch transparent activity to gain focus
- Read clipboard contents
- Send clipboard data to macOS

Phase 2: Mobility & Reliability
 - Relay: Deploy Node.js server
 - Android & macOS: Add Relay fallback logic
 - Stability: Handle network changes (Wi-Fi ↔ LTE)
 - Feature: Media controls (Play / Pause / Volume)
 
Phase 3: High Bandwidth (Future)
 - Files: Resumable chunk-based transfer (on-demand)
 - Video: Android camera stream → macOS virtual camera (CMIO)
 - Efficiency: BLE Proximity Gating
