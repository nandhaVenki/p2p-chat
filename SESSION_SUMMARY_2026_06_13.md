# Session Summary - June 13, 2026

## Project: P2P Private Android Messaging App (Emerald)

### Goal
To build a highly private, 1-on-1 messaging application where communication happens directly between devices (Peer-to-Peer) via WebRTC Data Channels. The primary focus is minimizing "middle-man" involvement and ensuring no message data is stored on any server.

### Architecture & Tech Stack
- **Android App**: Kotlin + Jetpack Compose (MVVM Architecture).
- **Local Storage**: Room Database with **SQLCipher** for full local encryption.
- **P2P Engine**: `webrtc-android` for encrypted data channels.
- **Signaling Server**: Node.js + WebSocket (`ws`) server.
  - Role: Facilitates initial IP discovery and SDP handshake (Offers/Answers).
  - Privacy: Data is ephemeral and cleared once the connection is established.
- **Identity**: Phone-number hashing with salts to identify users without storing PII on the server.

### Key Features Implemented/Discussed
1. **Direct IP Discovery**: Users find each other via the signaling server using hashed phone numbers.
2. **Resilient Connectivity**: Dynamic handling of network changes (5G, Wi-Fi, 4G) to maintain P2P tunnels.
3. **Multimedia Chunking**: A strategy to send large files (images/videos) in encrypted chunks directly over the Data Channel, avoiding the need for a TURN server.
4. **Premium Billing**: Integrated Google Play Billing for subscription tiers (Monthly and Lifetime plans) to support future server/development costs.
5. **Background Service**: A persistent service to keep the signaling connection alive for incoming message notifications.

### Current Status
- **Signaling Server**: 100% complete and ready for deployment (Oracle Cloud Free Tier recommended).
- **Android App**: Codebase is structured, but currently facing a **Gradle Build Error** during initialization.

### Next Steps
1. **Fix Gradle Build Error**: Resolve the dependency/configuration mismatch in `build.gradle.kts`.
2. **Verify P2P Handshake**: Test the signaling flow and WebRTC connection establishment.
3. **UI Polish**: Refine the "Emerald" UI theme and Chat Screen interaction.
