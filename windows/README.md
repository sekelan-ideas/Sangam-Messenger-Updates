# Sangam for Windows

Windows desktop client for Sangam. It is a locked-down Electron shell around the Sangam web client
(`app/`), packaged as a Microsoft Store `.appx` and as a normal `SangamSetup.exe`.

See [`docs/DEPLOY_AND_TEST.md`](docs/DEPLOY_AND_TEST.md) for the step-by-step GitHub, Supabase, and real-device deployment and test guide.

## Build on Windows
```
npm ci
npm run start        # run it
npm run dist:setup   # dist/SangamSetup-1.0.0.exe
```
Store package (needs the three values from Partner Center > Product identity):
```
set STORE_IDENTITY_NAME=...
set STORE_PUBLISHER=CN=...
set STORE_PUBLISHER_DISPLAY_NAME=...
npm run dist:store   # dist/Sangam-1.0.0-Store.appx
```
Or run the **Build Sangam for Windows** GitHub Action (set the 3 values as repository *Variables*).

## Features & What Is Missing on Windows
- Signal Protocol v3 multi-device messaging (`d<deviceKey>@<senderDevice>:<type>:<base64>` and `s<deviceKey>@<senderDevice>:<type>:<base64>`) with atomic prekey claims via `take_device_one_time_prekey`.
- Pre-login username lookup via the rate-limited `login-lookup` Edge Function and pre-signup username check via the `username_available` RPC.
- AES-256-GCM encrypted photos, documents, and recorded voice notes (`MediaRecorder` + `SangamCrypto.mediaEncrypt`).
- Profile editing (photo crop & upload, display name, status, username display), desktop notifications (when window is unfocused) with toggle, group creation and management (add/remove members, leave, leader and co-leader roles), reactions, replies, forwarding, message editing, delete for everyone, disappearing messages, view-once photos, typing/online/last-seen/read-receipts with privacy toggles, chat pin/archive, encrypted cloud backup restore, and in-app account deletion.
- **Not available on Windows (controls hidden)**: Offline Wi-Fi Direct (`Nearby`) and live G.711/JPEG audio/video call streaming (`call_chunks`). The `Calls` tab displays read-only CallPulse history.

## Security notes
- No remote code, fonts or scripts: Tailwind and Inter are compiled/bundled at build time.
- Strict Content-Security-Policy; sandboxed renderer; navigation locked to the app origin.
- Identity keys and Signal sessions are stored with Windows DPAPI (Electron `safeStorage`) via `window.sangamVault` only. If secure storage is unavailable, keys are kept in memory only and are NOT written to disk or `localStorage`.
- Only camera, microphone, notifications and clipboard-write are permitted, only for the app origin.

## Tests
```
node tests/selftest.js
node tests/robustness_test.js
python3 tests/selfimpl_interop.py
python3 tests/acceptance_checks.py
PROTOCOL_BUFFERS_PYTHON_IMPLEMENTATION=python python3 tests/independent_interop.py
```
