# TCloud — Telegram-Backed Personal Cloud Storage for Android

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple.svg)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Platform-Android_8.0+_(API_26+)-green.svg)](https://developer.android.com)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack_Compose-blue.svg)](https://developer.android.com/jetpack/compose)
[![TDLib](https://img.shields.io/badge/Backend-TDLib_0.1.1-blue.svg)](https://core.telegram.org/tdlib)

**TCloud** transforms any Telegram account into a private, unlimited personal cloud storage system resembling Google Drive and Google Photos. Files are automatically discovered, queued, chunked, uploaded, indexed, retrieved, and streamed behind an intuitive, modern Material 3 interface.

---

## Key Features

- **Automated Incremental Discovery**: Scans MediaStore for new photos, videos, documents, and audio based on customizable backup rules.
- **Large File Chunking (> 1.9 GB)**: Transparent multi-part chunking and reassembly with SHA-256 integrity verification.
- **Direct Video & Audio Streaming**: Custom `TCloudDataSource` for Media3 / ExoPlayer enables byte-range streaming without full file downloads.
- **In-App Media Viewers**:
  - **Photos**: Fullscreen pinch-to-zoom and pan viewer with metadata drawer.
  - **Videos**: Fullscreen playback with playback speed, seekbar, and quality controls.
  - **Audio**: Disc-spinning visualization with scrub controls and track details.
  - **Documents**: Built-in Android `PdfRenderer` and monospaced plain text/code viewer.
- **Cloud Index Rebuild**: Log in on any new device and click *Rebuild Cloud Index* to reconstruct the entire file index from Telegram storage channel history.
- **Offline Mode & Device Restore**: Save files back to local device storage (`Downloads`, `DCIM`, `Pictures`) or pin offline.
- **Background Synchronization**: WorkManager periodic sync paired with a foreground data-sync service surviving app process death and system restarts.
- **Enterprise Security**: Android Keystore encryption for session tokens and zero logging of passwords, OTP codes, or keys.

---

## Architecture Overview

```text
UI Layer (Jetpack Compose + Material 3)
   │
   ▼
ViewModels (StateFlow + Coroutines)
   │
   ▼
Domain & Engine Layer
   ├── SyncEngine (Queue, Hashing, Deduplication, Policy Checks)
   ├── MediaScanner (Incremental MediaStore Discovery)
   ├── ChunkManager (Chunk Calculation, Assembly, SHA-256 Verification)
   ├── RestoreManager (Downloads, SAF/MediaStore Restore)
   └── CloudIndexRebuilder (Channel Crawling, Metadata Decoding)
   │
   ▼
Storage Layer
   ├── Local: Room Database (FileEntry, TelegramObject, ChunkEntry, Daos)
   └── Remote: CloudStorageBackend -> TelegramStorageBackend (TDLib / MTProto)
```

---

## Configuration

To connect TCloud to Telegram:
1. Obtain an `api_id` and `api_hash` from [my.telegram.org](https://my.telegram.org) under *API development tools*.
2. Add your credentials in `gradle.properties` or command line:
   ```properties
   TELEGRAM_API_ID=12345678
   TELEGRAM_API_HASH=your_api_hash_here
   ```
*(Note: These are application credentials; end-users log in with their own Telegram phone number and OTP).*

---

## Building the APK

### Requirements
- JDK 17 or JDK 21 (e.g., Eclipse Adoptium or Android Studio JBR)
- Android SDK (API 35, Build Tools 35.0.0+)

### Debug Build
```bash
./gradlew assembleDebug
```
The output APK is generated at:
```text
app/build/outputs/apk/debug/app-debug.apk
```

### Release Build
```bash
./gradlew assembleRelease
```

---

## Running Unit Tests
```bash
./gradlew testDebugUnitTest
```

---

## Technical Specifications
- **Language**: Kotlin 2.0.21
- **Architecture**: Clean Architecture + MVVM + Repository Pattern
- **Dependency Injection**: Hilt 2.51.1
- **UI Toolkit**: Jetpack Compose BOM 2024.12.01 + Material 3
- **Local Persistence**: Room 2.6.1 with KSP
- **Media Engine**: AndroidX Media3 (ExoPlayer) 1.5.1
- **Image Pipeline**: Coil 3.0.4
- **Telegram Interface**: TDLib Android 0.1.1 (pre-built AAR with JNI for all 4 ABIs)
