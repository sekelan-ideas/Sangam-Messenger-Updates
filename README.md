---

### 2. README for `sekelan-ideas/Sangam-Messenger-Updates` (Public Download & Updates Mirror)

```markdown
# Sangam Messenger — Official Downloads & Updates

**Where friends & family meet.**  
A lightweight, privacy-first messenger for **Android** and **Windows 11**, designed around clean vector visuals, username-only privacy, and zero account bloat.

---

## Download Latest Release

- **Android APK (Signed Release):** [Download Latest `Sangam.apk`](https://github.com/sekelan-ideas/Sangam-Messenger-Updates/releases/latest)
- **In-App Updates:** Already installed Sangam on Android? Open **Sangam → Menu (⋮) → New updates → Check for updates** to update automatically.
- **Windows Desktop App:** Available inside the `windows/` directory of this repository (native Windows 11 Dark/Light Mica Glass desktop client).

---

## Highlights

- **No Phone Number Required:** Connect with friends and Circles using your `@username` or QR code.
- **Signal Protocol Sessions & 60-Digit Safety Numbers:** Messages, photos, videos, documents, voice notes, and call frames use Signal Protocol (`X3DH` + `Double Ratchet`) and AES-256-GCM encryption, with built-in **Verify Security Code** screens and key-change warnings.
- **Instant Realtime Sync:** Persistent WebSockets deliver messages, read receipts, and call alerts immediately on both Android and Windows.
- **Offline Nearby Mode (Android):** Chat directly phone-to-phone over Bluetooth with Signal encryption when you have no internet or cellular signal.
- **Sealed Time Capsules & View-Once Media:** Seal a message until a future date/time, send View-Once photos/videos, or set disappearing chat timers (`5m` to `7d`).
- **Native Windows 11 Desktop Companion:** Link your Windows PC in seconds by scanning the QR code from your phone (single-use magic-link pairing — your password is never shared).
- **Private Local Backups:** Export and restore password-protected `.sangam` backup files with zero Google Drive or cloud lock-in.

---

## How to Install

### Android
1. Download **`Sangam.apk`** from the [Latest Release](https://github.com/sekelan-ideas/Sangam-Messenger-Updates/releases/latest) page.
2. Open the downloaded file on your Android phone (Android 8.0+ / API 26+) and allow installation from your browser if prompted.
3. Future updates will notify you automatically inside the app.

### Windows 11 / 10 (Developer Preview)
```powershell
git clone [https://github.com/sekelan-ideas/Sangam-Messenger-Updates.git](https://github.com/sekelan-ideas/Sangam-Messenger-Updates.git)
cd Sangam-Messenger-Updates\windows
npm install
npm start
