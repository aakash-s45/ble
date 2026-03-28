# Project "Passover" Technical Design Document

## 1. Project Overview

**Passover** is an open-source, private toolset for seamless data synchronization between macOS and Android, utilizing a **Local-First WebSocket architecture** with **Mesh Sync** for multi-device group communication.

### Current Focus

- SAS-based device pairing (replaces QR-based pairing)
- Shared GroupKey encryption (AES-256-GCM)
- Star topology with hub election for multi-device sync (non-mobile devices only)
- Bidirectional Text Clipboard Synchronization
- Local Network Discovery (mDNS/Bonjour)

---

## 2. System Architecture

### 2.1 Networking Model — Star Topology

The system operates on a **Star Topology** for resource-efficient multi-device sync:

- **Hub (desktop/PC preferred)** — Runs a WebSocket server. Receives messages from any connected device and fans them out to all other connected devices. Any non-mobile device qualifies: macOS, Linux, Windows, Raspberry Pi, etc.
- **Mobile devices (Android, iOS)** — Always spokes, never hub candidates. Each maintains exactly 1 WebSocket connection to the hub. Same resource usage as a simple client-server setup.
- **Hub election** — Non-mobile devices are preferred (always-on, plugged in, no battery pressure). Among eligible non-mobile devices, the one with the longest uptime is elected. Mobile devices are excluded from election entirely.
- **Hub failover** — If the hub goes offline, the next non-mobile device by uptime promotes itself (registers mDNS, starts listening). If no non-mobile device is available, sync pauses until one comes back online.

**Connection count for N devices:**

| Devices | Connections (star) | Connections (full mesh) |
|---------|--------------------|------------------------|
| 2 | 1 | 1 |
| 3 | 2 | 3 |
| 5 | 4 | 10 |
| 10 | 9 | 45 |

### 2.2 Protocol Stack

- **Transport:** WebSockets (WS) for reliable message framing
- **Security:** AES-256-GCM with shared GroupKey (Authenticated Encryption)
- **Key Exchange:** ECDH (P-256) + HKDF for pairwise session keys during pairing
- **Verification:** SAS (Short Authentication String) — 6-digit code
- **Serialization:** Google Protocol Buffers (Protobuf)
- **Discovery:** mDNS (Bonjour) for local IP resolution

---

## 3. Data Protocol (Protobuf)

```proto
syntax = "proto3";

package com.local.passover;

message Message {
  int64 timestamp_ms = 1;
  string messageId = 15;      // UUID for deduplication
  string originatorId = 16;   // DeviceID of original sender

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
    HandshakeMessage handshake = 14;
  }
}

message Identity {
  string deviceId = 1;
  string deviceName = 2;
  bytes publicKeyAgreement = 3;   // P-256 public key (X9.63 format)
  bytes publicKeySignature = 4;   // P-256 signing public key
}

message HandshakeMessage {
  bool sasConfirmed = 1;
  bytes encryptedGroupKey = 2;    // GroupKey encrypted with pairwise ECDH session key
}
```

*(Other message types — ClipboardMessage, MediaControlMessage, etc. — remain unchanged.)*

---

## 4. Security & Pairing Flow

### 4.1 Key Architecture

| Key | Purpose | Storage |
|-----|---------|---------|
| **P-256 Key Agreement** | ECDH shared secret during pairing | Android Keystore / macOS Keychain |
| **P-256 Signing** | Device identity verification | Android Keystore / macOS Keychain |
| **GroupKey (AES-256)** | All data encryption after pairing | Android DataStore (wrapped) / macOS Keychain |
| **Master Key (AES-256)** | Wraps GroupKey for secure storage | Android Keystore |

### 4.2 Pairing Flow (SAS-based)

```
User taps "Pair New Device" on Android
  → mDNS discovery finds macOS
  → Android connects via WebSocket
  → Both exchange Identity messages (public keys + device name)
  → Both compute SAS = SHA256(sorted public keys) → 6-digit code
  → Both UIs show the code — user confirms on both
  → ECDH shared secret → HKDF → pairwise session key
  → Existing device sends GroupKey encrypted with session key
  → New device decrypts and saves GroupKey
  → Both save each other as trusted peers
```

- If this is the **first ever pairing**, the existing device generates a new GroupKey.
- If a GroupKey already exists, it's sent to the new device.
- The pairwise session key is **ephemeral** — used only during pairing.

### 4.3 GroupKey Rotation

When a device is **removed** from the trusted list:
1. A new GroupKey is generated.
2. Distributed to all remaining connected peers (encrypted with old GroupKey).
3. The removed device can no longer decrypt group messages.

### 4.4 E2E Encryption

- **Algorithm:** AES-256-GCM
- **Key:** Shared GroupKey (same for all group members)
- **Process:** Serialize → Encrypt with GroupKey + random IV → Prepend IV → Send over WebSocket

---

## 5. Detailed Implementation Design

### 5.1 macOS Module (Swift / SwiftUI)

- **KeyStore** — P-256 key pairs (agreement + signing) in Keychain, GroupKey management, ECDH shared secret, HKDF session key, SAS computation, AES-GCM encrypt/decrypt.
- **PairingManager** — Device identity, SAS-based pairing flow, trusted peer management.
- **TrustedPeerStore** — Codable plist for trusted peers (deviceId, name, public keys, trustedAt).
- **NetworkManager** — NWListener with mDNS registration, GroupKey-based encryption, identity/SAS handshake for new peers.
- **ClipboardMonitor** — Polls NSPasteboard for clipboard changes, triggers sync.
- **PairingWindow** — Separate SwiftUI window for SAS confirmation on incoming pairing requests.

### 5.2 Android Module (Kotlin / Compose)

#### Process Hierarchy

- **PassoverAccessibilityService** — Root process. Detects copy/cut, manages clipboard sync lifecycle.
- **MainService (Foreground Service)** — WebSocket connection, mDNS discovery, incoming message processing.
- **ClipboardActivity (Ghost Activity)** — Transparent activity for background clipboard access (Android 10+).

#### Key Components

- **KeystoreManager** — P-256 key pairs in Keystore, ECDH + HKDF, GroupKey wrap/unwrap (master key pattern), SAS computation, AES-GCM encrypt/decrypt.
- **TrustedPeerStore** — Preferences DataStore with JSON serialization. CRUD for trusted peers.
- **ConnectionRepository** — Persistent deviceId, GroupKey-based encryption, `connectWithTrustedPeer` flow.
- **PairingViewModel** — Drives discovery → peer list → identity exchange → SAS → GroupKey exchange → save peer.
- **PairingScreen** — Two-stage UI: discovery list (filtered) → SAS confirmation (6-digit code).
- **DnsServiceManager** — mDNS discovery for `_passover._tcp` services. Supports single-target and multi-peer discovery.
- **WebSocketClient** — OkHttp-based WebSocket client.

#### Boot Chain

```
Reboot → System re-enables AccessibilityService
       → onServiceConnected()
       → startMainService()
       → Reads trusted peers
       → Auto-connects to trusted hub (GroupKey encryption, no SAS needed)
```

#### Screen On/Off Lifecycle

- **Screen off** → WebSocket closes, sync paused
- **Screen on** → Reconnects to trusted hub, sync active

---

## 6. Connection Lifecycle

### 6.1 Trusted Peer Reconnection

Already-trusted peers reconnect automatically using the stored GroupKey — no SAS, no ECDH needed.

### 6.2 Resource Saving

- Each non-hub device maintains exactly **1 WebSocket** — negligible battery.
- Screen off → all connections closed, mDNS stopped.
- Screen on → re-establish connection.

---

## 7. Building the Android App

### Prerequisites

- **Android Studio** installed (provides the bundled JDK and Android SDK)
- No separate JDK installation required

### Command-Line Build

The project uses Gradle. Since a standalone JDK may not be on `PATH`, point `JAVA_HOME` at Android Studio's bundled JBR:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew assembleDebug
```

The debug APK is output to:

```
app/build/outputs/apk/debug/app-debug.apk
```

### Common Gradle Tasks

| Task | Description |
|------|-------------|
| `assembleDebug` | Build debug APK |
| `assembleRelease` | Build release APK (requires signing config) |
| `installDebug` | Build and install on connected device/emulator |
| `clean` | Delete all build outputs |

> **Tip:** Prefix any task with `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` if no system JDK is configured.

---

## 8. Build Roadmap

**Phase 1: Identity & Trust (Current)**
- ✅ SAS-based device pairing (replaces QR)
- ✅ P-256 key pairs (agreement + signing)
- ✅ ECDH + HKDF shared secret and session key derivation
- ✅ Shared GroupKey (AES-256-GCM) for all data encryption
- ✅ Trusted peer persistent storage
- ✅ mDNS-based peer discovery with trusted peer filtering
- ✅ AES-GCM encryption pipeline
- ✅ Bidirectional text clipboard sync
- ✅ Android AccessibilityService clipboard detection
- ✅ Screen on/off lifecycle management

**Phase 2: Group Mesh Transport & Security Hardening**
- Star topology with hub election (non-mobile devices only — macOS, Linux, Windows, etc.)
- Hub fan-out (message forwarding to all connected peers)
- Hub failover and migration (pause sync if no non-mobile device is available)
- Continuous passive mDNS discovery
- Message deduplication (SeenMessageCache)
- GroupKey rotation on device removal
- mDNS service registration (isHub TXT record)
- Ephemeral pairing keys (generate fresh P-256 key pair per session, or nonce exchange, so SAS and session keys are unique per attempt)
- Signing key authentication (use the P-256 signing key to sign Identity messages, verify on receive — binds key agreement to device identity)
- Connection-level trust verification on reconnect (verify peer identity after GroupKey decryption, not just accept any device with the key)
- Pairing timeout (auto-reject SAS if not confirmed within ~60s, prevent stale pairing state blocking new requests)
- HKDF salt improvement (use a meaningful constant or session-derived salt instead of 32 zero bytes)
- removing a device from one device should also remove it from the other device (if connected)

**Phase 3: Relay & Multi-OS Expansion** *(future)*
- Stateless relay server for cross-network sync
- Trust Introduction (transitive trust via signed PeerIntroduction)
- Persistent MessageCache deduplication (Room / CoreData)
- File transfer, media controls, video streaming
