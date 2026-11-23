# Changelog

## 2.14.2 - 2026-11-10

Multi-device Signal Protocol sessions, modular database security scripts, Edge Function hardening, and deployment guide.

- Split database security migration into small idempotent scripts (`0004a_run_now.sql`, `0004b_profiles_lockdown.sql`, `0004c_policy_cleanup.sql`, `0004d_storage_policies.sql`) plus `supabase/verify.sql`, `supabase/maintenance/find_plaintext_rows.sql`, and `supabase/maintenance/wipe_test_data.sql`.
- Updated Android `Sangam.usernameTaken` to call the `username_available(p_username)` RPC instead of selecting from `public.profiles` before login, and added `scripts/check_profile_columns.py` to verify all profile column reads.
- Updated `supabase/functions/push/index.ts` to read recipient `fcm_token` via the `service_role` client, added 10 req / 10 min per-IP rate limiting to `supabase/functions/login-lookup/index.ts`, and updated `supabase/functions/delete-account/index.ts` to verify every delete/remove error, transfer group leadership to the oldest co-leader or member, and guard `auth.users` deletion on `profiles` deletion success.
- Updated `Crypto.kt` on Android so session deletion only occurs on `InvalidMessageException` MAC failures (never on `DuplicateMessageException` or replays) while preserving read tolerance for legacy rows containing `|w:`.
- Updated `windows/app/app.js` to use `take_device_one_time_prekey` exclusively, look up usernames via `login-lookup`, check sign-up usernames via `username_available`, store keys strictly via `window.sangamVault`, record and encrypt voice notes with `MediaRecorder` + AES-256-GCM, tolerate missing first-device bundles, display `"Message could not be decrypted"` on undecryptable ciphertexts, and hide unsupported Windows controls (`Nearby` and live call streaming).

## 2.10.2 - 2026-11-03

Your phone rings when someone calls you.

- An incoming call now takes over the screen with Answer and Decline, from
  anywhere in the app - not just inside the chat.
- Calls older than a minute are ignored, so a call that was never answered
  cannot reappear later as a ghost ring.
- A ringtone plays while it rings and stops the moment you answer, decline,
  or the caller hangs up.
- The call screen has the frosted glass look like the rest of the app.
- The phone icon is hidden inside group chats, where calling makes no sense.

## 2.10.1 - 2026-11-03

You can ring someone now.

- A phone icon in the chat header starts a call. Your phone writes the call
  to the database, the screen shows "Calling...", and when the other side
  answers it switches to a running timer.
- Declining or hanging up closes the call on both phones.
- No microphone is opened yet - this build proves the ringing handshake
  before any audio is added.
- The incoming side is not wired yet: if someone rings you, your phone does
  not light up in this build. That is the next release.

## 2.10.0 - 2026-11-03

Foundation for voice and video calls.

- Camera, audio-routing and Bluetooth-headset permissions are declared, so a
  call can take the microphone, route to the earpiece or speaker, and use a
  connected headset without a permission failure mid-call.
- This release adds no call screen yet. It is the groundwork: the permissions
  and the database table (see the release notes for the SQL) that the ringing
  screen in 2.10.1 will use.

## 2.9.4 - 2026-11-03

Cleartext HTTP is now explicitly forbidden.

- Android already defaults to blocking unencrypted HTTP at targetSdk 34, so
  nothing was actually exposed. The setting is now declared in the manifest
  rather than left to the default, so a future library cannot quietly turn
  it back on.
- Everything Sangam does was already HTTPS, so nothing changes in behaviour.

## 2.9.3 - 2026-11-03

The frosted glass reaches the login and profile screens.

- Login, sign-up and email verification now sit on the aurora background
  inside a frosted glass card, so the app looks the same from the first
  screen onward.
- My profile gets the same treatment.
- What's new now shows the 2.9.1, 2.9.2 and 2.9.3 entries, and its release
  cards are glass like the rest.

## 2.9.2 - 2026-11-03

Settings and the chat screen get the frosted glass treatment.

- Settings is now a grouped glass sheet: rounded cards, each row with a
  tinted icon badge, a title and a one-line explanation, separated by thin
  dividers. Close is the only plain button left.
- The chat screen's top bar is translucent frosted glass like the chat list.
- The message input is a floating glass pill instead of a flat grey bar.
- Message bubbles carry a thin specular border - brighter along the top
  edge, fading downward - so they read as glass rather than flat panels.

## 2.9.1 - 2026-11-03

The chat list gets the frosted glass look.

- A soft aurora wash behind the list - a hint of teal at the top fading
  through to a warm glow at the bottom.
- The top bar is translucent frosted glass instead of a solid block, so the
  aurora shows faintly through it.
- Both are painted gradients, not blur effects, so they cost nothing in
  battery.

## 2.2.4 - 2026-10-16

- Fixed the two phones colliding when they both tried to connect at the
  same instant (one side failed with a Bluetooth timeout, error 8, and the
  other could not finish). Now only one of the two opens the connection -
  decided by a small id carried in the Bluetooth advert - so the link
  comes up cleanly.

## 2.2.3 - 2026-10-16

- Nearby: the two phones now stagger their Bluetooth connection attempts
  (both connecting at the same instant often fails), and use the low-energy
  transport explicitly.
- Nearby now reports each step on screen, so a stuck connection is visible:
  "Connected - checking services…", "Services found - saying hello…",
  "Hello sent - waiting for the other phone…", or the exact failure.
- Services are discovered after the Bluetooth packet size is settled, which
  is more reliable than doing both at once.

## 2.2.2 - 2026-10-16

- Fixed Nearby mode still not listing nearby phones even though Bluetooth
  worked: the opening "hello" between two phones was looked up by the
  other phone's name before that name was known, so it was never sent and
  neither side could see the other. Connections are now tracked by the
  Bluetooth address, and the phone that receives a hello answers with its
  own - so both sides appear in each other's list.
- The hello now waits for the Bluetooth packet size to settle first, so it
  is not chopped into tiny pieces.
- Nearby now works with no internet too (the account id is remembered on
  the phone), and each handshake uses its own fresh key.
- The Nearby status line is now plain text instead of red, since it also
  reports things like "Found a nearby phone".

## 2.2.1 - 2026-10-16

- Fixed Nearby mode not finding anyone: the Bluetooth advertisement was
  larger than the 31-byte limit, so the phone never became discoverable
  ("Bluetooth advertising failed (1)"). Only the service id is advertised
  now, so nearby phones can see each other.
- Nearby mode now offers to turn Bluetooth on when it is off, instead of
  silently doing nothing (Android does not let an app switch it on by
  itself).
- The "Sangam closed unexpectedly" dialog now shows the cause - the top
  of the error - and has a "Copy & close" button so the full report can
  be shared.

## 2.2.0 - 2026-10-08

Nearby mode (Phase 1): offline messaging over Bluetooth.

- Turn on Nearby mode from the chats menu (Off by default) and Sangam
  finds other Sangam users close to you - no internet, no server.
- Message a nearby friend directly over Bluetooth. Messages stay
  Signal Protocol encrypted: the two phones exchange their keys directly,
  then use the same Signal encryption as everywhere else.
- Nearby messages show a small Bluetooth mark so you always know how a
  message travelled.
- Runs in a foreground service with a small "Nearby is on" notification,
  so it costs nothing unless you switch it on.

## 2.0.3 - 2026-10-07

Group chat polish.

- @mentions: type "@" in a group and pick a member from the list that
  appears; mentions are highlighted in teal in the message.
- Group photo: the group's creator can set or change a group picture
  from Group info; it shows in the chat header.
- Typing: "X is typing..." now shows in group chats too.
- Clear chat: wipe a group's messages from this phone only (others
  keep their copy) from the chat menu.
- Delete group: the creator can delete a group entirely from Group info.

## 2.0.2 - 2026-10-03

- Fixed a newly created group not showing up in the chats list until
  someone sent a message. An empty group now appears right away with
  "No messages yet", so you can open it any time.

## 2.0.1 - 2026-10-03

- Fixed group creation failing with "Could not create the group": the
  creator's own membership is now saved first, so adding the other
  members passes the access rule.
- Group read rule now always lets the creator open their own group.

## 2.0.0 - 2026-10-03

Group chats - the first major release.

- Create a group: give it a name, pick friends, done. Up to ~10 members.
- Group messages stay Signal Protocol encrypted: each message is encrypted
  separately for every member, so the server still only ever sees
  ciphertext - exactly like a private chat.
- Group chat screen: sender names on incoming bubbles, member list in
  the header, and read ticks that show when everyone has read.
- Group info: rename, description, add or remove members (admin only),
  and leave the group.
- Groups appear in your chats list with pin, mute and search, and work
  with photos, videos, documents, voice notes, replies and reactions.
- Offline messages in a group are queued with a clock icon like before.

## 1.11.3 - 2026-09-29

- Last seen now reads "today at 9:41 AM" or "yesterday at 9:41 PM";
  only genuinely old ones show the date with the time.
- Photos and videos now send on a background thread - the chat no
  longer freezes for a moment while a photo is compressed and
  encrypted, and typing stays smooth during a send.
- Images are decoded at screen size for the chat list - scrolling
  through photos is smooth and the app uses far less memory
  (a large photo drops from ~50 MB to ~3.5 MB in memory).
- Small speedup of the encrypted message storage.

## 1.11.2 - 2026-09-28

- Offline start fixed properly: starting the app now NEVER touches the
  network - the saved login is checked locally and, when its token has
  expired, the app opens from the phone's caches and signs in quietly
  once the internet returns. No more hang on the splash screen.
- Fixed the endless loading circle that could appear on the chats screen
  when opening without a network.
- Network failures during the background refreshes can no longer crash
  the app, ever.
- If Sangam ever closes unexpectedly, the next start now shows the
  reason on screen so it can be screenshot and fixed exactly.

## 1.11.1 - 2026-09-28

- Fixed opening the app with no internet or airplane mode. A saved
  login whose access token had expired used to require the network
  to refresh it, so offline the app fell back to the login screen.
  It now opens straight into your cached chats and quietly signs in
  again in the background once the internet returns.

## 1.11.0 - 2026-09-28

- Block user: from a friend's profile you can now block them - their
  chats are hidden, no notifications arrive, and they can be unblocked
  anytime from the ⋮ menu on the chats screen.
- Offline outbox: messages written without a network no longer vanish.
  They sit in the chat with a clock icon and are sent automatically
  (turning into the usual ticks) the moment the connection returns.
- Storage safeguard: outgoing photos are now compressed to stay under
  400 KB, keeping the free-tier storage small.
- Delete account now also wipes every Sangam setting and cached file
  on the phone, not just the secure stores.

## 1.10.4 - 2026-09-27

- Invites now include everything a friend needs: the share message
  carries a direct always-latest download link for the app plus the
  personal invite link, so they can install Sangam and add you in
  one go. The link always gives the newest version.

## 1.10.3 - 2026-09-27

- Times now always show the clock: "Yesterday, 9:14 PM",
  "5 Mar, 9:14 PM", "5 Mar 2025, 9:14 PM" - in chats and the chats list.
- New notification look: the Tamil letter in the status bar, Sangam's
  teal accent, a conversation-style card and a gentle double-pulse
  vibration - Sangam's own style, not a copy of anyone else.
- Faster notifications: the wake-up call now reuses one connection
  instead of building a new one for every message.
- Faster chats: an idle chat no longer rewrites its cache and rebuilds
  the screen every two seconds - only when something actually changed.
- More stable: showing a notification can no longer crash the app.

## 1.10.2 - 2026-09-27

- Fixed search jumping: the scroll to a found message was being cut off
  half-way, and the chat kept sliding back to the newest message right
  after a jump. Now it lands on the message and stays there.
- App icon: the Sangam badge was too large for the round home-screen
  mask, so the letter was cropped. It now sits fully inside the icon
  and stays complete and clear on every launcher shape.

## 1.10.1 - 2026-09-27

- Search now takes you to the message: tapping a search result from the
  chats screen opens that chat right at the message and highlights it.
  In-chat search also jumps to the newest match as soon as you type.
- Fixed message times: today shows the time, yesterday shows "Yesterday",
  older this year shows date and month (5 Mar), and older shows the year
  too (5 Mar 2025) - everywhere: chat bubbles, chats list and search.

## 1.10.0 - 2026-09-27

- Pin chats: long-press a chat to pin it to the top of the chats list.
  Pinned chats stay on top with a pin icon, no matter who messages.
- Drafts: if you type a message and leave the chat, Sangam remembers
  it. The chats list shows "Draft" so you never forget to finish it.
- Global search: searching from the chats screen now also finds
  messages across all your chats, with the chat name and a snippet.

## 1.9.3 - 2026-09-26

- Fixed the chats list: the newest message now shows as each chat's
  preview and the chat moves to the top when you send or receive a
  message (it was showing an older message and staying in place).
- Fixed the photo editor layout on narrow screens: the filter buttons
  no longer break into vertical letters; they scroll sideways instead,
  and Rotate/Crop sit centred.
- Smoother chat: the screen now follows new messages only when you are
  reading the latest, so browsing older messages is never interrupted.

## 1.9.2 - 2026-09-26

- Full-quality camera photos: the in-app camera now captures at full
  resolution (the old preview mode was small and blurry on modern phones).
- Videos now show as a big thumbnail in the chat with a play button,
  instead of a small row. The preview is sent encrypted along with the
  video.
- Sharper photos: sent photos keep more detail (higher quality encoding).
- New tagline: "Where friends and family meet".

## 1.9.1 - 2026-09-26

- Much faster chats: while a chat is open only new messages are fetched
  instead of the whole conversation, so updates arrive quickly even on
  slow connections.
- Chats now open instantly from a saved copy and are never blank when
  the network is slow - messages reappear as soon as the sync succeeds.
- Crop tool fixed: it now starts with the whole photo visible and lets
  you pinch to zoom in on exactly the part you want.
- Smooth list animations for messages and the chats list.
- The Sangam logo now sits next to the app name at the top of the chats
  screen, matching both light and dark themes.

## 1.9.0 - 2026-09-26

- Videos now play inside the app: tap a video to watch it, with a save
  button if you also want it in Downloads.
- Photo editor: crop, rotate and filter (B&W, Warm, Cool, Bright) a photo
  before sending it - from both the gallery and the camera.
- New app lock: full password (numbers, letters, symbols) instead of a
  4-digit PIN, with bigger text and a fingerprint button.
- Restyled message bar: a clean pill input with the camera, gallery,
  document and time-capsule buttons on a slim row above, and a round
  button that switches between microphone and send.
- Chat header now shows animated dots while the friend is typing.

## 1.8.0 - 2026-09-26

- Share to Sangam: share a photo from the gallery (or anywhere) with
  Android's Share button, pick the friend, it sends encrypted.
- Edit sent messages: long-press your own text message -> Edit; shows
  "edited" on both phones.
- Friend profile: tap the friend's name in the chat header to see their
  profile.
- Forward messages (text and photos) to another friend.
- Delete for me: hide a message on this phone only.
- Send documents (PDF etc.) with the new attach button - encrypted,
  tap to save to Downloads.
- Send videos from the gallery with the photo button - encrypted,
  tap to save to Downloads.
- Privacy: turn off read receipts and online/last seen (menu -> Privacy).
- Backup & restore: save chats to a password-encrypted file and view it
  again on any phone with the password (menu -> Backup & restore).

## 1.7.1 - 2026-09-26

- Fixed: the app lock no longer asks for the PIN when taking a photo
  with the camera or picking one from the gallery - and the photo now
  always sends instead of being lost.
- The "New updates" screen now shows only the current version's
  changes, with no other app names mentioned.

## 1.7.0 - 2026-09-26

- App lock: set a 4-digit PIN in the ⋮ menu (chats list → App lock);
  Sangam then asks for your PIN or fingerprint when it opens or comes
  back from the background.
- Status line: add a short status on your profile (My profile →
  Status). Friends see it in the chat header. Needs the one-line
  v1.7.0 SQL on the server.
- Notification options: sound on/off, vibration on/off, and showing
  or hiding message text in notifications (chats list → ⋮ →
  Notifications). Mute a single chat from its ⋮ menu.
- Media gallery: see all photos and voice notes of a chat in one grid
  (chat ⋮ menu → Media).
- Star messages: long-press a message → Star; find them again in the
  chat ⋮ menu → Starred messages.
- In-app camera: take a photo right inside the chat with the new
  camera button.

## 1.6.1 - 2026-09-26

- Fixed: tapping a reply's quote now jumps to the right original
  message (it previously scrolled past it).
- Faster loading: opening a chat and refreshing the chats list now
  fetch data in parallel instead of one request after another, so
  messages and online statuses appear noticeably sooner.

## 1.6.0 - 2026-09-26

- Reply to messages: long-press any message -> Reply. Your reply shows
  the original as a quote block in the bubble; tapping the quote jumps
  back to the original message. Works for text, photos, voice, and
  time capsules. Fully Signal Protocol encrypted.
- Chat themes: in a chat, tap the ⋮ menu -> Chat theme to pick a
  wallpaper and matching bubble colour for that chat (Sangam teal,
  Sunset, Forest, Lavender, Rose, Midnight). Saved per chat on your
  phone only.
- Search inside a chat: tap the search icon in the chat header and type
  a word; the arrows jump between matches and highlight them.

## 1.5.1 - 2026-09-26

- Typing indicator fixed: the chat screen now checks every 2 seconds and
  the signal stays alive 8 seconds, so "typing…" shows reliably and stays
  while your friend types.
- Faster, more accurate status: chats list refreshes every 8 seconds (was
  15), the green online dot turns off sooner, and the chat header shows
  "last seen" time when your friend is not online.
- App opens instantly: your chats list is saved on the phone and shown
  immediately, then quietly updated from the server.
- Message and chat times now show in 12-hour format with AM/PM.

## 1.5.0 - 2026-09-26

- Voice messages: with an empty message box, the send button becomes a
  microphone. Tap it, record, then send (✓) or cancel (✕). Tap a voice
  bubble to play it back with a progress bar. Encrypted with Signal Protocol like
  photos. No server changes needed.
- Chats list: green dot next to friends who are online, and "typing…"
  as the live preview while they type.

## 1.4.0 - 2026-09-26

- Log in with your username or your email address (username lookup uses the
  new login-lookup function; the app stores your email on your profile
  after each login).
- Long-press your own message -> Delete for everyone: it disappears from
  both phones.
- Chat header now shows "typing..." live while your friend types, and
  their online / last seen status otherwise.
- Magnifier button on the chats screen to search your chats by name.
- Requires the one-time v1.4.0 server setup (sql/v1_4_0_features.sql +
  login-lookup function) for the new features.

## 1.3.5 - 2026-09-26

- The Tamil letter in the logo now matches the selected theme: white in
  light mode, dark in dark mode (the gold variant is gone). The dark splash
  screen uses the dark letter too.

## 1.3.4 - 2026-09-26

- New theme system: Auto (follows the device theme, the new default),
  Light and Dark, chosen from the three-dots menu. Previously-chosen dark
  mode carries over.
- Fixed the login screen keeping a light background with dark-styled text
  after logging out in dark mode; every screen now paints its own themed
  background and the status bar icons follow the theme.
- Profile photos now also load when the stored link is a bare file name
  (older format), fixing photos not showing for some accounts.
- The Tamil letter in the logo is gold in dark mode (white in light mode);
  the dark splash uses the gold-letter mark too.
- Empty chats screen now points at the "Find a friend" button instead of
  telling you the message button is "above".

## 1.3.3 - 2026-09-26

- The three-dots menu: tapping your photo/name/id at the top opens your
  profile for editing. The separate "My profile" button is gone.
- Logo background is now transparent - no more white box around it on dark
  screens (splash, login and New updates screens).
- Opening the app in dark mode no longer flashes white; the system splash
  matches the theme and the logo gently pulses while loading.
- Profile photos that fail to load now show the colored letter avatar
  instead of a blank circle.

## 1.3.2 - 2026-09-26

- Read-receipt ticks: one tick = sent, two ticks = delivered, highlighted = read.
- "Check for updates" button on the New updates screen.
- Red-dot badge on the menu and the New updates item when a new version is out.
- Uploads get 60 seconds instead of 10, so photos survive slow connections.
- Error texts now show the actual reason, without the long URL.

## 1.3.1 - 2026-09-26

- Profile photo cropping: drag to move, pinch to zoom, then save.
- Photos keep their correct orientation (portrait photos no longer sideways).
- Clearer read ticks, timestamps and bubble colours in light and dark mode.
- The chats menu shows your own photo and name, refreshed instantly.
- "New updates" menu item with the full release history.
- Errors are now one short friendly line — no more walls of technical text.
- The login session is kept fresh automatically, so restarting the app no
  longer lands on the login screen.
- One-time SQL: supabase/sql/v1_3_1_images.sql (photo upload permission).

## 1.3.0 - 2026-09-25

- Profile screen: set your own profile photo and edit your name
  (menu -> My profile). Photos appear in the chats list and chat headers.
- Dark mode: menu -> Dark mode, applies instantly and is remembered.
- Photo sending made robust: the photo is copied safely before reading, with
  retries around flaky gallery permissions and precise error messages.
- Chat polish: day chips ("Today", "Yesterday", dates) between message groups.
- Incoming bubbles follow the theme, so chats look right in dark mode too.
- Needs the one-time SQL in supabase/sql/v1_3_0_profile.sql (avatar column +
  public "avatars" bucket).

## 1.2.0 - 2026-09-25

- Account deletion: tap the menu (three dots, top right of the chats list)
  then "Delete account" to permanently erase your account, profile and
  messages from the server, and wipe this phone. Needs the one-time
  delete-account server function (supabase/functions/delete-account).
- Much faster app opening: Sangam now reuses your saved login instead of
  signing in from scratch, and encryption-key uploads happen quietly in
  the background instead of blocking your chats.
- Old messages appear sooner: one profile query instead of one per chat,
  decryption runs off the UI thread, and the server is no longer pinged
  with unnecessary read/delivered updates on every refresh.

## 1.1.1 - 2026-09-25

- Push notifications: get an alert on your phone when a message arrives,
  even when Sangam is closed. Tapping the alert opens that chat. (Needs a
  one-time server setup - see supabase/functions/push - and Android will
  ask once to allow notifications.)
- Smoother scrolling and typing: photos now decode off the main thread and
  message bubbles only redraw when they actually change.
- Error messages under the chat now clear themselves after a few seconds
  instead of staying on screen.
- Fixed the time capsule clock wrongly saying a valid future time was
  "already past" (this happened between midnight and 5:30 AM India time).
- The chats list no longer flashes "No chats yet" while loading.
- Opening a chat from the list shows your friend's name instantly.

## 1.1.0 - 2026-09-23

- Time capsules: write a message today, seal it, and it appears for your
  friend at the exact date and time you choose (tap the clock in a chat).
- Emoji reactions: long-press any message to react with an emoji; tap the
  same reaction again to remove it.
- Emoji picker: tap the smiley next to the message box for quick emojis.
- Photo viewer: tap a photo to view it full screen, pinch to zoom, and
  save it to your gallery with one button.
- In-app updates: when a new version is out, Sangam shows a banner and
  downloads it for you — no browser, no GitHub login (needs the one-time
  "allow installs" toggle Android shows).
- Invite links: "Invite friends" now shares your personal
  sangam://add?u=yourname link that opens straight into your chat.
- Photo send errors now say exactly what went wrong instead of a vague
  "Try again".
- Every friend gets their own stable avatar color in the chat list.
- A daily keep-alive check in CI keeps the free Supabase project from
  ever pausing for inactivity.

## 1.0.5 - 2026-09-22

- Fixed messages showing as "Encrypted message" instead of their text. Two
  causes: the app tried to decrypt each incoming message again on every
  refresh, but the Signal protocol allows exactly one successful decrypt
  per message; and the local message cache stored its index in a corrupted
  format, so even our own sent messages never rendered.
- Incoming messages are now decrypted exactly once and the plaintext is
  cached safely on-device; the cache is stored per message so it cannot
  corrupt as a whole.
- Note: the few messages sent with v1.0.4 before this fix cannot be
  recovered (their one-time decrypt was already consumed); send new
  messages after updating.

## 1.0.4 - 2026-09-22

- Fixed the real cause of the sign-up "Unknown Error": every new account
  crashed the database trigger that creates its starter profile, because the
  auto-generated default username (taken from the user ID, e.g. `558ca79f-7da`)
  contained a hyphen, which breaks the username rule. Migration
  `supabase/migrations/0002_fix_signup_username.sql` repairs it — run it once
  in the Supabase SQL editor.
- The username field now accepts only a-z, 0-9 and _, matching the rule.
- Friendlier error message when the chat service is temporarily unreachable.

## 1.0.3 - 2026-09-22

- Fixed "Unknown Error" on the sign-up screen: the app was sending the
  Supabase publishable key in the `Authorization: Bearer` header, which the
  backend rejects. The key now travels only in the `apikey` header; login
  session tokens are unaffected.

## 1.0.2 - 2026-09-21

- Connected Sangam to its live Supabase backend: sign-up, sign-in and
  messaging are now enabled. Project URL and publishable key are injected
  at build time via encrypted repository secrets.
- The build pipeline now verifies the backend (URL, key and database
  tables) before producing an APK, so a broken backend can never ship.

## 1.0.1 - 2026-09-21

- Applied the official Sangam logo (supplied by SEKELAN) everywhere: launcher
  icon (adaptive, all shapes) and the in-app branding on the auth and setup
  screens. Replaces the temporary confluence mark.

## 1.0.0 - 2026-09-21

First release of Sangam.

- Account setup: sign up / log in with email, pick a username and display name.
- 1:1 chats with text messages and photos (picked from gallery).
- Signal Protocol encryption on by default: Signal protocol (X3DH key agreement +
  Double Ratchet messaging) via the vetted libsignal library; the server only
  ever stores ciphertext.
- Delivery (double check) and read (green double check) status.
- Contact search by name or @username, plus a share-sheet invite.
- Parent-friendly light UI in the Sangam teal palette, lock icon with a
  plain-language "your chats are Signal Protocol encrypted" note.
- Automatic build pipeline: signed release APK built by GitHub Actions and
  published to GitHub Releases.
