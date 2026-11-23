# Sangam Encryption Status & Metadata Disclosure

This document specifies exactly what data in Sangam is end-to-end encrypted (E2EE), what is encrypted at rest or in transit only, and what metadata is visible to the Sangam server (Supabase backend).

## 1. Summary Table by Data Type

| Data Type | Protection Status | How It Is Protected | What the Server Can See | 
| ----- | ----- | ----- | ----- | 
| **1-to-1 Message Text** | **End-to-End Encrypted** | Signal Protocol (X3DH key agreement + Double Ratchet AES-256-CBC + HMAC-SHA256) per recipient device (`d<dev>@<src>:...`). | Ciphertext only, plus sender ID, recipient ID, timestamps, delivery/read timestamps, and message size. | 
| **Group Message Text** | **End-to-End Encrypted (Per-Device Fan-Out)** | Encrypted individually via Signal Protocol for every group member's registered devices. Adding a new member later does not reveal past messages. | Ciphertext per member, `group_id`, `sender_id`, `recipient_id`, and `created_at`. | 
| **Photos, Videos & File Attachments** | **End-to-End Encrypted** | Encrypted on-device with a random 256-bit AES-GCM key + 96-bit IV (`ImageCrypto` / `mediaEncrypt`) before upload to `message-images`. The random AES key and IV travel only inside the Signal Protocol ciphertext message. | Encrypted binary blob in Storage (`message-images`), file size, and upload timestamp. | 
| **Voice Notes** | **End-to-End Encrypted** | Encrypted on-device with a random 256-bit AES-GCM key + 96-bit IV before upload; the key and IV travel only inside the Signal Protocol ciphertext message. | Encrypted binary blob in Storage (`message-images`), duration metadata inside ciphertext, and blob size. | 
| **1-to-1 Voice & Video Call Audio/Video (`call_chunks`)** | **End-to-End Encrypted** | Each audio (G.711 u-law) and video (JPEG) frame in `public.call_chunks` is encrypted with AES-256-GCM (`E:<senderPubB64>:<ivB64>:<ctB64>`) using a 256-bit key derived via Curve25519 ECDH between the caller and callee device identity keys + HKDF-SHA256 (`"SangamCallE2EE:<callId>"`). | Call participants (`caller`, `callee`), call kind (`audio`/`video`), call status (`ringing`/`active`/`ended`), timestamps, and encrypted frame sizes/sequence numbers. | 
| **Chat Backups (`public.backups` & Local Backup Files)** | **End-to-End Encrypted (User Password / Recovery Key)** | Encrypted on-device prior to upload using PBKDF2-HMAC-SHA256 (120,000 iterations, 16-byte random salt) + AES-256-GCM (12-byte random IV). The backup password/recovery key never leaves the device. | Encrypted blob (`public.backups.blob`), `user_id`, and `updated_at`. | 
| **Desktop QR Pairing Blob (`pair_<code>.bin`)** | **End-to-End Encrypted (Ephemeral QR Secret)** | Encrypted on the phone with PBKDF2-HMAC-SHA256 (120,000 iterations) + AES-256-GCM using a 32-byte random ephemeral key embedded only inside the desktop QR code (`?k=<ephemeralKey>`), and deleted immediately after pairing. | Short-lived encrypted blob in Storage (`avatars/pair_<code>.bin`). | 
| **Group Name, Description & Group Photo** | **Visible to Server (Plaintext in DB / Storage, Sent Over TLS)** | Stored in `public.chat_groups` (`name`, `description`, `photo_url`) and `avatars` bucket; protected in transit via HTTPS/TLS and encrypted at rest by Supabase storage/database disk encryption, not E2EE. | Group name, description, group creator, member list (`public.group_members`), and group icon. | 
| **User Profile Info (Display Name, Username, Status, Avatar)** | **Visible to Server (Plaintext in DB, Sent Over TLS)** | Stored in `public.profiles` (`username`, `display_name`, `status`, `avatar_url`). Email and `fcm_token` are hidden from `anon` and other `authenticated` users via column-level grants (`0004b_profiles_lockdown.sql`). | Display name, username, status text, avatar image, email (visible to owner & service role), and push token (service role only). | 
| **Communication Metadata (Who Talks to Whom & When)** | **Visible to Server (Plaintext Metadata)** | Stored in `public.messages` (`sender_id`, `recipient_id`, `group_id`, `created_at`, `delivered_at`, `read_at`) and `public.calls` (`caller`, `callee`, `created_at`, `status`). | Who messaged or called whom, when, whether messages were delivered/read, and active device public keys (`public.prekey_bundles`, `public.devices`). | 

## 2. Key Verification & Trust Model (TOFU + Safety Numbers)

1. **Trust On First Use (TOFU) & Key-Change Warnings:**

   * Both Android and Windows record the last-seen Signal `identity_key` and device set for each peer.

   * If a contact's primary `identity_key` changes (e.g., they reinstalled Sangam) or a new linked device (`device_key > 1`) is added to their account, Sangam flags the conversation with a key-change / new-device security notice.

2. **60-Digit Safety Numbers ("Verify Security Code"):**

   * Users can compare a 60-digit numeric fingerprint out-of-band (or face-to-face) in the **Verify Security Code** dialog on both Android and Windows.

   * Computed identically on both platforms using the standard Signal v0 numeric fingerprint algorithm: 5,200 iterations of SHA-512 over `[0x00, 0x00] || identityKey_33bytes || userId_utf8`, sorted lexicographically and formatted as 12 groups of 5 digits.

## 3. Mandatory Post-Fix Security Hygiene for Previously Paired Accounts

If any account was ever paired with Sangam for Windows prior to Round 4 (`0005_lock_pairing.sql`):

1. Run `supabase/migrations/0005_lock_pairing.sql` in the Supabase SQL Editor to delete all rows in `public.desktop_pairings` and revoke client table access.

2. In the Supabase Dashboard -> **Storage** -> **`avatars`**, delete any leftover `pair_*.json` or `pair_*.bin` files.

3. Change the account password for any user account that used QR pairing under earlier builds.