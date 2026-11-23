# Sangam Messenger

**Where friends & family meet.**  
Sangam is a lightweight, privacy-focused messenger built for **Android (Kotlin + Jetpack Compose)** and **Windows 11 (Electron + Dark Mica Glass UI)**, backed by **Supabase** (PostgreSQL, PostgREST, Realtime WebSockets, Storage, and Deno Edge Functions) and **Firebase Cloud Messaging**.

---

## Core Architecture

| Layer | Stack & Key Components |
| :--- | :--- |
| **Android Client (`app/`)** | Kotlin, Jetpack Compose (Material 3), `libsignal` (`signal-protocol-android:2.8.1`), `EncryptedSharedPreferences`, OkHttp Supabase Realtime WebSocket (`SangamRealtime.kt`), CameraX + on-device ML Kit QR scanner, Bluetooth P2P Nearby mode, FCM push. |
| **Windows Desktop (`windows/`)** | Electron, Windows 11 Dark/Light Mica Glass UI, zero-emoji inline SVG icon set, custom `libsignal`-compatible Signal Protocol v3 JS engine (`SangamCrypto`), Windows DPAPI key vault, native Chromium Supabase Realtime WebSocket, custom waveform audio player. |
| **Backend (`supabase/`)** | PostgreSQL with Row-Level Security (RLS) & column-level grants, versioned SQL migrations (`0001`–`0005`), and 4 Deno Edge Functions (`push`, `login-lookup`, `desktop-pair`, `delete-account`). |

---

## Key Features

- **Username-Only Identity (`@handle` + QR):** Connect with contacts and join Circles without exposing a personal phone number.
- **Multi-Device Messaging:** Per-device Signal Protocol sessions (`X3DH` + `Double Ratchet`) with per-device fan-out across linked Android phones and Windows desktops.
- **Magic-Link QR Desktop Pairing:** The phone requests a single-use magic-link `hashed_token` via `desktop-pair`, encrypts `{ email, tokenHash, syncKey, outbox }` with AES-256-GCM using the QR code's ephemeral key, and Windows exchanges it via `POST /auth/v1/verify` for an independent session before deleting the blob.
- **Key Verification & TOFU Protection:** Built-in **60-digit Signal v0 Safety Numbers** (`NumericFingerprintGenerator(5200)` on Android, matching SHA-512 × 5,200 on Windows) plus automatic Trust-On-First-Use (TOFU) banners when a contact's identity key changes or a new device is added.
- **Instant Realtime Delivery:** Persistent Supabase Realtime WebSockets on both Android and Windows with smart background fallback.
- **Voice & Video Calls:** Two-way 1-to-1 voice and video calls with Curve25519 ECDH + HKDF-SHA256 + AES-256-GCM encrypted media frames.
- **Offline Nearby Mode (Android):** Direct phone-to-phone Signal-encrypted messaging over Bluetooth with zero internet or server required.
- **Sealed Time Capsules & View-Once Media:** Send messages that remain visibly sealed until a chosen unlock timestamp, plus disappearing timers (`5m`–`7d`) and View-Once photos/videos.
- **Account-Free Encrypted Backups:** Export and restore local `.sangam` backups encrypted with PBKDF2-HMAC-SHA256 (120,000 iterations) + AES-256-GCM — no Google Drive or iCloud account required.

---

## Security & Metadata Transparency

- **Protected Content:** 1-to-1 and Circle message text, photos, videos, documents, voice notes, 1-to-1 call frames, local backups, and QR pairing blobs are encrypted client-side before leaving the device.
- **Server-Visible Metadata:** Profile details (`username`, `display_name`, `status`, `avatar_url`), Circle name/description/photo, and routing metadata (`sender_id`, `recipient_id`, timestamps) are visible to the Supabase backend.
- **Verification Gate:** Client UI encryption wording is controlled centrally via `ENCRYPTION_VERIFIED` (`app/build.gradle.kts` on Android and `windows/app/config.js` on Windows). See [`docs/ENCRYPTION_STATUS.md`](docs/ENCRYPTION_STATUS.md) for the complete data-by-data breakdown.

---

## Repository Structure

```text
├── app/                     # Android Kotlin + Jetpack Compose application
├── windows/                 # Windows 11 Electron desktop application
├── supabase/
│   ├── migrations/          # Ordered SQL migrations (0001 through 0005_lock_pairing.sql)
│   ├── functions/           # Edge Functions: push, login-lookup, desktop-pair, delete-account
│   └── verify.sql           # Post-migration live database verification queries
├── tests/                   # Crypto, robustness, interop, Round 4 security & acceptance tests
├── scripts/                 # Static security checks (profile column access scanner)
└── docs/                    # DEPLOY_AND_TEST.md, ENCRYPTION_STATUS.md, PRIVACY_POLICY.html
