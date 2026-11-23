<div align="center">

# Sangam

### Where friends and family meet

A private messenger for Android and Windows, built in India.

`v2.14.2` &nbsp;·&nbsp; `Android 8.0+` &nbsp;·&nbsp; `18.6 MB` &nbsp;·&nbsp; `No phone number required`

<br>

**[ Download the latest APK ](https://github.com/sekelan-ideas/Sangam-releases/releases/latest/download/Sangam.apk)**

[All releases](https://github.com/sekelan-ideas/Sangam-releases/releases) &nbsp;·&nbsp; [Changelog](CHANGELOG.md)

</div>

---

## What Sangam is

A private messenger that does not ask for your phone number. You sign in with
an email address, your messages are encrypted on the phone before they leave
it, and the server only ever holds unreadable ciphertext.

It also makes calls — voice and video — and it works with no internet at all
when two phones are close to each other.

## Features

### Messaging
- **One-to-one and group chats**, protected with Signal Protocol sessions
- Photos, video, documents, voice notes
- **Disappearing messages**, **view-once photos**, sealed **time capsules**
- Replies, reactions, forwarding, starring, message editing
- HD photo switch, per-chat themes, in-chat search, tappable links

### Calls
- **Voice calls** with mute and speaker
- **Video calls** with camera flip and picture-in-picture
- **Call history** per chat, plus a Calls hub across everyone
- **Rings from a locked screen**, even when the app is closed
- Audio and video travel as G.711 and JPEG frames over HTTPS through
  Supabase — no TURN server, no subscription, and it works behind Indian
  carrier NAT (Jio, Airtel, Vi)

### Groups
- Admins, co-leaders and members, with **Leader** and **Co-leader** badges
- Group avatars, descriptions, invite links and **QR codes**
- **Share** an invite link or QR card anywhere
- "Only leaders can send" and "members can add people" controls

### Nearby — offline mode
- Works over **Wi-Fi Direct** with no internet and no server
- Protected with direct device-to-device key exchange
- Phones announce themselves as `Sangam-xxxx` and connect automatically

### Devices
- **Link a second phone by QR** — scan a code from your signed-in phone and
  you are in, no typing
- Linked devices list
- Cloud backup with a recovery key, or an encrypted file export

### Privacy and safety
- **No phone number required** — email sign-in
- Messages encrypted on the phone; server holds only ciphertext
- **Column-level database privacy**: no user can read another user's email or
  push token, and nobody can overwrite another user's media
- In-app **reporting** for messages and accounts
- Published **grievance officer** with the 24-hour / 15-day timelines the
  IT Rules 2021 and DPDP Act require

## Design

A frosted glass interface throughout — an aurora background, translucent
bars, glass cards and a floating pill composer — with a Messages · Calls ·
Nearby switcher at the bottom of the screen.

## Versioning

| Change | Bump | Example |
| --- | --- | --- |
| Bug fix | patch | 2.12.2 → 2.12.3 |
| New feature | minor | 2.12 → 2.13 |
| Redesign | major | 2.x → 3.0 |

Every release is recorded in [CHANGELOG.md](CHANGELOG.md).

## How it is built

| Layer | Choice |
| --- | --- |
| App | Kotlin, Jetpack Compose |
| Encryption | Signal protocol (libsignal) |
| Backend | Supabase — auth, Postgres, storage, edge functions |
| Push | Firebase Cloud Messaging |
| Offline | Wi-Fi Direct (WifiP2p) |
| Camera / QR | CameraX, ML Kit barcode scanning |
| Build | GitHub Actions — compiles, signs and publishes every APK |

## Known limitations

- **Video quality** — the relay caps the frame rate. Real WebRTC would look
  better but needs a paid TURN server.
- **Multi-device SQL migration** — follow [`docs/DEPLOY_AND_TEST.md`](docs/DEPLOY_AND_TEST.md) to apply `supabase/migrations/0004a_run_now.sql`, deploy the `push`, `login-lookup`, and `delete-account` Edge Functions, install the updated apps, and then apply `0004b_profiles_lockdown.sql`, `0004c_policy_cleanup.sql`, and `0004d_storage_policies.sql`.
- **Nearby** — reliable for two phones; three or more is not yet solid.
- **No Play Services, no scanner** — the QR scanner uses the Play Services
  barcode model. Everything else works on a de-Googled Android.
- **Push must be redeployed** (`supabase functions deploy push`) after any
  change to the edge function.

## Setting up a fresh Supabase project

The SQL for every table lives in the release notes of the version that
introduced it:

| Table | Release |
| --- | --- |
| `calls` | 2.10.0 |
| `call_chunks` | 2.10.3 |
| `backups` | 2.8.0 |
| `abuse_reports` | 2.8.21 |
| `devices` | 2.7.0 |
| Group join permissions | 2.12.3 |
| Storage policies and `profiles` column grants | security block, Nov 2026 |

Without those, calls, backup, reporting and group joining will not work.

---

<div align="center">

Made in India · Built with care

</div>
