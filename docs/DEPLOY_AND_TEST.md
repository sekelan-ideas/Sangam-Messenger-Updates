# Sangam Deployment, Verification & Real-Device Test Guide (`DEPLOY_AND_TEST.md`)

This guide walks you step by step—with exact button clicks and SQL/curl commands—through deploying the updated Sangam Android app, Sangam for Windows app, Supabase Edge Functions, and database security scripts, and running the 13-step real-device verification plan.

---

## A. GitHub Setup, Workflows & Privacy Policy Hosting

### A.1. How to Upload the New Files to Your GitHub Repository

#### Folder Structure Mapping
- **Main repository (`Sangam-main-Fixed-3.zip`)**: Everything inside `Sangam-main/` goes at the **root** of your main GitHub repository:
  - `.github/workflows/` (`android.yml`, `windows.yml`, `keepalive.yml`, `diagnose.yml`, `publishtemp.yml`)
  - `app/` (Android Kotlin source code)
  - `docs/` (`PRIVACY_POLICY.html`, `DEPLOY_AND_TEST.md`, `Sangam-Windows-Store-Guide.docx`)
  - `scripts/` (`check_profile_columns.py`)
  - `supabase/` (`verify.sql`, `migrations/0004a_run_now.sql` .. `0004d_storage_policies.sql`, `maintenance/`, `functions/push/index.ts`, `functions/login-lookup/index.ts`, `functions/delete-account/index.ts`, `functions/README.md`)
  - `tests/` (`selftest.js`, `selfimpl_interop.py`, `independent_interop.py`, `independent_bridge.js`, `independent_load.js`, `robustness_test.js`, `acceptance_checks.py`)
  - `windows/` (the complete Windows Electron project from `Sangam-Windows-Kit-Fixed-3.zip`)

#### Option 1: Upload Using Git on Your Computer (Recommended)
1. Unzip `Sangam-main-Fixed-3.zip` on your computer.
2. Open a terminal (Command Prompt / PowerShell / Terminal) inside your cloned repository folder and copy the extracted files over, then run:
   ```bash
   git status
   git add -A
   git commit -m "Security migration split, username_available RPC, Edge Function hardening, and Windows kit"
   git push origin main
   ```

#### Option 2: Upload Using the GitHub Web Interface (No Git Installed)
1. Open your repository on GitHub in your browser.
2. Click **Add file** (top-right above the file list) → **Upload files**.
3. Drag and drop the updated folders/files from the unzipped `Sangam-main` folder into the browser window (keep the folder structure so `app/`, `windows/`, `supabase/`, `docs/`, `scripts/`, and `tests/` land in their matching folders).
4. Scroll down to **Commit changes**, type a short message (e.g., `Update Android, Windows, and Supabase files`), select **Commit directly to the `main` branch**, and click **Commit changes**.
5. For `.github/workflows/windows.yml` and `.github/workflows/keepalive.yml`, open each file in the GitHub web editor, click the **pencil icon (Edit this file)** at the top-right, paste the updated file content, and click **Commit changes...**.

---

### A.2. Every Secret and Variable Used by GitHub Actions Workflows

Open your repository on GitHub → click **Settings** (top tab bar) → in the left sidebar under **Security**, click **Secrets and variables** → **Actions**.

#### 1. Repository Secrets (**Secrets** tab → **New repository secret**)

| Secret Name | Used By Workflow(s) | Where to Get It | Required? |
| :--- | :--- | :--- | :--- |
| **`SUPABASE_URL`** | `android.yml`, `keepalive.yml` | Supabase Dashboard → **Project Settings** (gear icon) → **API** → **Project URL** (starts with `https://...supabase.co`). | **Yes** |
| **`SUPABASE_ANON_KEY`** | `android.yml`, `keepalive.yml` | Supabase Dashboard → **Project Settings** → **API** → **Project API keys** → **`anon` `public`** key (click Copy). | **Yes** |
| **`KEYSTORE_BASE64`** | `android.yml` | Base64 string of your Android `release.keystore` file (`base64 -w0 release.keystore` on Linux/macOS or `[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.keystore"))` in Windows PowerShell). | **Yes** (for signed release APK updates) |
| **`KEYSTORE_PASSWORD`** | `android.yml` | Password for your Android `release.keystore`. | **Yes** |
| **`KEY_ALIAS`** | `android.yml` | Key alias inside your Android `release.keystore`. | **Yes** |
| **`KEY_PASSWORD`** | `android.yml` | Password for that key alias. | **Yes** |
| **`RELEASE_TOKEN`** | `android.yml` | GitHub Fine-Grained Personal Access Token (GitHub → top-right profile picture → **Settings** → **Developer settings** → **Personal access tokens** → **Fine-grained tokens**) scoped to your public updates repository (e.g., `sekelan-ideas/Sangam-Messenger-Updates` / `Sangam-releases`) with **Repository permissions → Contents: Read and write**. | Optional (needed only to auto-publish APKs to your public releases repo) |

*(Note: `GITHUB_TOKEN` / `github.token` is built into GitHub Actions automatically; do not add it manually.)*

#### 2. Repository Variables (**Variables** tab → **New repository variable**)

| Variable Name | Used By Workflow(s) | Where to Get It | Required? |
| :--- | :--- | :--- | :--- |
| **`STORE_IDENTITY_NAME`** | `windows.yml` / `windows-store.yml` | Microsoft Partner Center → **Apps and games** → **Sangam** → **Product management** → **Product identity** → **Package/Identity/Name**. | Needed for `.appx` Store build (leave blank if only building `SangamSetup.exe`) |
| **`STORE_PUBLISHER`** | `windows.yml` / `windows-store.yml` | Microsoft Partner Center → **Product management** → **Product identity** → **Package/Identity/Publisher** (starts with `CN=...`). | Needed for `.appx` Store build |
| **`STORE_PUBLISHER_DISPLAY_NAME`** | `windows.yml` / `windows-store.yml` | Microsoft Partner Center → **Product management** → **Product identity** → **Package/Properties/PublisherDisplayName**. | Needed for `.appx` Store build |

---

### A.3. How to Run Each Workflow & Download the Builds

1. Click the **Actions** tab at the top of your GitHub repository.
2. In the left sidebar:
   - Click **Build Sangam APK** (for Android) or **Build Sangam for Windows** (for Windows).
3. Click the **Run workflow** dropdown button on the right → leave **Branch: main** selected → click the green **Run workflow** button.
4. **How long it takes**:
   - **Build Sangam APK**: ~3 to 6 minutes.
   - **Build Sangam for Windows**: ~4 to 8 minutes.
5. **Where to download the outputs**:
   - Click on the completed workflow run (it will have a **green checkmark ✔**).
   - Scroll to the bottom of the run summary page to the **Artifacts** section:
     - For Android: click **`sangam-apk`** to download the zip containing `Sangam-v2.14.2.apk` (also attached under the **Releases** tab on your repository home page).
     - For Windows: click **`sangam-windows`** to download the zip containing `SangamSetup-1.0.0.exe` and (if the three `STORE_*` variables are set) `Sangam-1.0.0-Store.appx`.
6. **What a failure looks like and what to send if it fails**:
   - A failed run shows a **red X (`✖`)** next to the workflow run name.
   - Click the failed run → click the red **`build`** job on the left → the failed step will expand automatically showing red `::error::` lines. Take a screenshot of that expanded step (or copy-paste the text of the failed step) and send it along with the name of the workflow.

---

### A.4. How to Host the Privacy Policy Free on GitHub Pages

1. Before committing, open `docs/PRIVACY_POLICY.html` (and `windows/docs/PRIVACY_POLICY.html`), replace `[DATE]` with today's date (for example, `November 10, 2026`), and replace every `[YOUR SUPPORT EMAIL]` with your real support email address.
2. On GitHub, open your repository → click **Settings** → in the left sidebar under **Code and automation**, click **Pages**.
3. Under **Build and deployment**:
   - Set **Source** to **Deploy from a branch**.
   - Under **Branch**, select **`main`**, change the folder dropdown next to it from `/ (root)` to **`/docs`**, and click **Save**.
4. Wait ~1–2 minutes and refresh the **Settings → Pages** screen. At the top you will see:
   > **Your site is live at `https://<your-github-username>.github.io/<your-repo-name>/`**
5. Your Privacy Policy URL is:
   ```text
   https://<your-github-username>.github.io/<your-repo-name>/PRIVACY_POLICY.html
   ```
6. Open `windows/app/config.js` and fill both fields:
   ```javascript
   window.SANGAM_CONFIG = {
     privacyUrl: "https://<your-github-username>.github.io/<your-repo-name>/PRIVACY_POLICY.html",
     supportEmail: "your-real-email@example.com"
   };
   ```
7. Paste that same `PRIVACY_POLICY.html` URL into Microsoft Partner Center (**Properties → Privacy policy URL**) and Google Play Console.

---

## B. Supabase Deployment & Security Lockdown (Exact Order)

> **Important rule**:
> - **TypeScript (`.ts`) files** (`push/index.ts`, `login-lookup/index.ts`, `delete-account/index.ts`) go in **Supabase → Edge Functions**.
> - **SQL (`.sql`) files** (`0004a_run_now.sql`, `verify.sql`, etc.) go in **Supabase → SQL Editor**. Never paste TypeScript into the SQL Editor or SQL into Edge Functions.

### Step B.1. Export a Quick Backup of Your Tables
1. Free Supabase projects do not have automatic point-in-time backups.
2. In your Supabase Dashboard, click **Table Editor** in the left sidebar.
3. For each table you care about (`profiles`, `devices`, `prekey_bundles`, `messages`, `chat_groups`, `group_members`), click the table name → if it has rows you want to keep, click **Export** (or select all rows → **Export to CSV**) at the top-right.
4. **What you should see**: A downloaded `.csv` file for each table on your computer.

### Step B.2. Deploy the Three Edge Functions (`push`, `login-lookup`, `delete-account`)
1. In the Supabase Dashboard left sidebar, click **Edge Functions**.
2. **Deploy `push`**:
   - Click on **`push`** (or click **Deploy a new function → Via Editor**, name it `push`).
   - Keep **Verify JWT with legacy secret / Enforce JWT Verification** turned **ON**.
   - Click the **Code** tab, select all existing code, delete it, and paste the entire contents of `supabase/functions/push/index.ts` (identical to `push-index.ts`).
   - Click **Deploy updates** (bottom-right).
   - Ensure `FCM_SERVICE_ACCOUNT` is set in **Edge Functions → Secrets**.
3. **Deploy `login-lookup`**:
   - Open **`login-lookup`** (or create it with exact name `login-lookup`).
   - Keep **Verify JWT** turned **ON**. No custom secrets are needed.
   - Replace its code with the entire contents of `supabase/functions/login-lookup/index.ts` and click **Deploy updates**.
4. **Deploy `delete-account`**:
   - Open **`delete-account`** (or create it with exact name `delete-account`).
   - Keep **Verify JWT** turned **ON**. No custom secrets are needed.
   - Replace its code with the entire contents of `supabase/functions/delete-account/index.ts` and click **Deploy updates**.
5. **What you should see**: All three functions (`push`, `login-lookup`, `delete-account`) show status **Active** in the Edge Functions list.

### Step B.3. Run `0004a_run_now.sql` (Safe to Run Immediately)
1. In the Supabase Dashboard left sidebar, click **SQL Editor**.
2. Click **+ New query**.
3. Open `supabase/migrations/0004a_run_now.sql` (identical to `A_run_now.sql`), copy the **entire file**, and paste it into the SQL Editor.
4. Click **Run** (bottom-right).
5. **Destructive operation warning popup**: Because the script contains `drop trigger if exists` and `update public.messages`, Supabase will show a warning dialog saying *"Potential issue detected with your query / Destructive operation"*. Click **`Run this query`** to confirm.
6. **What you should see**: The output pane at the bottom says **`Success. No rows returned`**. (If it shows a red `ERROR:`, take a screenshot of the full window and stop here.)

### Step B.4. Run `supabase/verify.sql` (Checks 1, 2, 3, and 5)
1. In the **SQL Editor**, open a **+ New query** tab.
2. Copy and run each query from `supabase/verify.sql` one by one:
   - **Check 1 (Strip trigger)**:
     - **What you should see**: `1 row` with `tgname = trg_messages_strip_companion`.
   - **Check 2 (Readable messages count)**:
     - **What you should see**: `readable_count = 0`. (If it is `1` or more, proceed to Step B.5 immediately.)
   - **Check 3 (Prekey function privileges)**:
     - **What you should see**: Only `authenticated`, `postgres`, and `service_role` in the `grantee` column. `PUBLIC` and `anon` **must not** appear.
   - **Check 5 (`username_available` privileges)**:
     - **What you should see**: `2 rows` showing `anon` and `authenticated` with `EXECUTE`.

### Step B.5. Find and Delete Any Remaining Plaintext (`9:`) Rows
1. If Check 2 returned `readable_count > 0` (because `0004a_run_now.sql` strips `|w:` suffixes automatically, any remaining counted row is an old row starting with `9:`):
2. Paste and run `supabase/maintenance/find_plaintext_rows.sql`:
   ```sql
   select id,
          created_at,
          left(ciphertext, 12) as head,
          (position('|w:' in ciphertext) > 0) as has_w_suffix,
          (ciphertext like '9:%') as starts_with_9
     from public.messages
    where ciphertext like '%|w:%'
       or ciphertext like '9:%'
    order by created_at desc;
   ```
3. Copy the `id` UUID of the row(s) shown and delete them:
   ```sql
   delete from public.messages where id = 'PASTE-THE-ROW-UUID-HERE';
   ```
4. Re-run **Check 2** from `supabase/verify.sql`.
5. **What you should see**: `readable_count = 0`.

### Step B.6. Install the New Apps, Test Sign-Up/Login, Then Run `0004b_profiles_lockdown.sql`
1. Build and install the new Android APK and Windows app (from Section A.3).
2. Test signing up a new account (verifying the username check works) and signing in on both Android and Windows.
3. **Only after the new apps and patched `push` function are live**, open a **+ New query** tab in the Supabase SQL Editor, paste `supabase/migrations/0004b_profiles_lockdown.sql` (`B_run_after_new_apps.sql`), and click **Run**:
   ```sql
   revoke select on public.profiles from anon, authenticated;
   grant select (id, username, display_name, created_at, avatar_url, last_seen_at, typing_for, typing_until, status)
     on public.profiles to authenticated;
   ```
4. **What you should see**: `Success. No rows returned`.
5. Now run **Check 4** from `supabase/verify.sql`:
   ```sql
   select grantee, column_name, privilege_type
     from information_schema.column_privileges
    where table_schema = 'public'
      and table_name = 'profiles'
      and grantee in ('anon', 'authenticated')
    order by grantee, column_name;
   ```
6. **What you should see**:
   - `anon` appears **0 times**.
   - `authenticated` has **only 9 rows**: `avatar_url`, `created_at`, `display_name`, `id`, `last_seen_at`, `status`, `typing_for`, `typing_until`, `username`.
   - `email` and `fcm_token` **do not appear**.

### Step B.7. Run `0004c_policy_cleanup.sql` and `0004d_storage_policies.sql`
1. Open a **+ New query** tab in the SQL Editor, paste `supabase/migrations/0004c_policy_cleanup.sql`, click **Run**, and click **`Run this query`** if prompted.
   - **What you should see**: The query outputs any duplicate `(user_id, device_key)` rows in `public.devices` (if 0 rows, you have no duplicate device rows; if rows appear, you can optionally uncomment the `DELETE` at the bottom of `0004c_policy_cleanup.sql` to remove the older duplicate rows).
2. Open a **+ New query** tab, ensure the top-right role dropdown says **`Role: postgres`**, paste `supabase/migrations/0004d_storage_policies.sql`, and click **Run**.
   - **What you should see**: `Success. No rows returned`. (If your hosted project restricts `storage.objects` ownership and prints `ERROR: 42501: must be owner of table objects`, configure the policies in **Storage → Policies** in the left sidebar instead.)

### Step B.8. Rollback Instructions for Each Script
If you ever need to undo a migration step while debugging, run the corresponding snippet in the SQL Editor:
- **Rollback `0004a_run_now.sql`**:
  ```sql
  drop trigger if exists trg_messages_strip_companion on public.messages;
  drop function if exists public.strip_message_companion_suffix();
  grant update on public.messages to authenticated;
  grant execute on function public.take_one_time_prekey(uuid) to anon;
  grant execute on function public.take_device_one_time_prekey(uuid, integer) to anon;
  drop function if exists public.username_available(text);
  ```
- **Rollback `0004b_profiles_lockdown.sql`**:
  ```sql
  grant select on public.profiles to anon, authenticated;
  ```
- **Rollback `0004c_policy_cleanup.sql`**:
  ```sql
  drop policy if exists "profiles_select_authenticated" on public.profiles;
  create policy "profiles_select_all" on public.profiles for select using (true);
  ```
- **Rollback `0004d_storage_policies.sql`**:
  ```sql
  drop policy if exists "sangam_avatars_select" on storage.objects;
  drop policy if exists "sangam_avatars_insert" on storage.objects;
  drop policy if exists "sangam_avatars_update" on storage.objects;
  drop policy if exists "sangam_avatars_delete" on storage.objects;
  drop policy if exists "sangam_media_select" on storage.objects;
  drop policy if exists "sangam_media_insert" on storage.objects;
  drop policy if exists "sangam_media_delete" on storage.objects;
  ```

### Step B.9. Anonymous-Access Verification with `curl`
Open a terminal on your computer, set your project URL and anon key (replace the placeholders), and run these four commands:

```bash
export SUPABASE_URL="https://YOUR_PROJECT_REF.supabase.co"
export SUPABASE_ANON_KEY="YOUR_SUPABASE_ANON_KEY"

# 1. Anonymous profiles SELECT (must be DENIED with HTTP 401/403 permission denied for table profiles)
curl -i "$SUPABASE_URL/rest/v1/profiles?select=id,username,email,fcm_token" \
  -H "apikey: $SUPABASE_ANON_KEY" \
  -H "Authorization: Bearer $SUPABASE_ANON_KEY"

# 2. Anonymous take_one_time_prekey RPC (must be DENIED with HTTP 401/403 permission denied for function)
curl -i -X POST "$SUPABASE_URL/rest/v1/rpc/take_one_time_prekey" \
  -H "apikey: $SUPABASE_ANON_KEY" \
  -H "Authorization: Bearer $SUPABASE_ANON_KEY" \
  -H "Content-Type: application/json" \
  -d '{"p_user_id":"00000000-0000-0000-0000-000000000000"}'

# 3. Anonymous messages SELECT (must return empty array [] or HTTP 401/403)
curl -i "$SUPABASE_URL/rest/v1/messages?select=*" \
  -H "apikey: $SUPABASE_ANON_KEY" \
  -H "Authorization: Bearer $SUPABASE_ANON_KEY"

# 4. Anonymous username_available RPC (MUST SUCCEED with HTTP 200 and body `true` or `false`)
curl -i -X POST "$SUPABASE_URL/rest/v1/rpc/username_available" \
  -H "apikey: $SUPABASE_ANON_KEY" \
  -H "Authorization: Bearer $SUPABASE_ANON_KEY" \
  -H "Content-Type: application/json" \
  -d '{"p_username":"some_unclaimed_username_999"}'
```

---

## C. Real-Device Verification Test Plan (Android Phone + Windows 10/11 PC)

Prepare **two test accounts** (`Account A` on an Android phone and Windows PC, and `Account B` on a second phone or PC). After **every message-sending step**, run the ciphertext check query in Step 12.

| Step | Exact Action | Expected Result in App | How to Verify in the Supabase Database (SQL Editor) |
| :--- | :--- | :--- | :--- |
| **1. Sign-up username check** | On the Android sign-up screen, type an existing `@username`, wait 1s, then type a brand-new `@username` and complete sign-up. | Existing username shows `"already taken"`; free username shows `"available"`; sign-up succeeds even after `0004b_profiles_lockdown.sql` is applied. | `select id, username, display_name from public.profiles where username = 'NEW_USERNAME';` returns 1 row. |
| **2. Login by email & by `@username`** | Sign out and sign back in using (a) email + password, then (b) `@username` + password on both Android and Windows. | Both logins succeed. Typing a wrong username 11 times in 10 minutes returns a rate-limit error (HTTP 429). | `select ip_hash, window_start, request_count from public.login_lookup_rate_limits;` shows incremented `request_count`. |
| **3. QR pairing (Phone → PC)** | Open Sangam for Windows on the login screen (showing the QR code). On the signed-in Android phone, open **Menu → Linked Devices → Link a Device** and scan the PC's QR code. | Windows signs in automatically, registers `Sangam for Windows Desktop` (`device_key >= 2`), and deletes the one-time `pair_<codeId>.bin` file. | `select device_key, label, left(identity_key,12) from public.devices where user_id = 'USER_A_UUID' order by device_key;` shows Device 1 (phone) and Device 2+ (Windows). |
| **4. 5-message burst & reply (Phone ↔ PC)** | From `Account A` on Windows, send **5 text messages in a row** to `Account B` on Android before `Account B` replies. Then reply with **3 messages** from `Account B` on Android to `Account A`. | All 5 burst messages decrypt in order on `Account B`'s phone; all 3 replies decrypt on both `Account A`'s phone and `Account A`'s Windows PC. | `select left(ciphertext, 40) from public.messages order by created_at desc limit 8;` shows `d1@2:3:...` for the burst and `d1@1:2:...;d2@1:3:...` for the replies, with **no `|w:` and no `9:`**. |
| **5. Groups (create, add, send, leave)** | Create a group on Windows or Android, add `Account B`, send a group message from Windows and from Android, promote a co-leader, then leave the group. | Both members read group messages; roles update; leaving removes only the leaving member. | `select group_id, user_id, is_admin from public.group_members;` reflects membership and admin changes; group messages in `public.messages` have `group_id` set and no `|w:`. |
| **6. Photo, document & voice note (both ways)** | Send a photo, a PDF/text document, and a recorded voice note from Windows to Android, and from Android to Windows. Open the photo/doc and play the voice note on both sides. | Photos render, documents download and open, and recorded voice notes play audio on both Android and Windows. | Check **Storage → `message-images`** (`img/`, `docs/`, `audio/`): uploaded `.bin` files are unreadable AES-256-GCM binary blobs, not raw JPEGs/WebM files. |
| **7. Push notification (phone app closed)** | Swipe-close Sangam on `Account B`'s Android phone and lock the screen. Send a message from `Account A` on Windows to `Account B`. | `Account B`'s phone wakes up with a `"You have a new message"` notification; tapping it opens the chat with `Account A`. | Check **Edge Functions → `push` → Logs**: status `200` (`{"sent":true}`). |
| **8. Windows restart & DPAPI key persistence** | Completely quit Sangam for Windows (close window / system tray) and relaunch it. Open existing chats and receive a new message. | Existing conversations remain readable and the new message decrypts without re-linking or resetting sessions. | Inspect DevTools Application → Local Storage on Windows: `sangam_win_signal_v3` is **absent** (keys live only in the DPAPI `vault.bin` file). |
| **9. Disappearing message & view-once photo** | Set disappearing timer in a chat, send a message, and send a view-once photo (`View Once: On`). Open the view-once photo on the recipient device, then leave the chat. | View-once photo opens once and switches to `"Opened"` upon leaving; disappearing messages are purged after their timer expires. | `select id, deleted from public.messages order by created_at desc limit 5;` |
| **10. Second linked device delivery** | Sign in to `Account A` on a second Android phone or Windows PC and send a message from `Account B` to `Account A`. | The message arrives and decrypts on `Account A`'s primary phone and linked device(s). | `select ciphertext from public.messages order by created_at desc limit 1;` contains `d1@...;d2@...` segments for each registered device. |
| **11. Account deletion (`delete-account`)** | Create a temporary `Account C`, send a message, upload an avatar, create a 2-person group with `Account A`, then click **Delete my account** in Settings/Menu. | `Account C` is signed out; the shared group transfers leadership to `Account A` instead of being deleted; `Account C` can no longer log in. | `select count(*) from public.profiles where id = 'USER_C_UUID';` → `0`; `select count(*) from public.messages where sender_id = 'USER_C_UUID' or recipient_id = 'USER_C_UUID';` → `0`; `select created_by from public.chat_groups where id = 'GROUP_UUID';` → `USER_A_UUID`. |
| **12. Ciphertext cleanliness check (run after every step)** | Run the SQL query in the right column after Steps 4, 5, 6, 9, and 10. | Every row starts with `d<dev>@<senderDev>:<type>:` or `<type>:`; zero rows contain `|w:` or start with `9:`. | `select left(ciphertext, 40) from public.messages order by created_at desc limit 5;` and `select count(*) from public.messages where ciphertext like '%|w:%' or ciphertext like '9:%';` → **`0`**. |
| **13. Anonymous `curl` security checks** | Run the four `curl` commands from **Section B.9**. | Commands 1 and 2 are denied (`401`/`403`); Command 3 returns `[]` or `401`/`403`; Command 4 (`username_available`) returns `200` (`true`/`false`). | Confirm all 5 queries in `supabase/verify.sql` match their `EXPECTED` results. |

---

### Final Pass / Fail Checklist (Tick Before Public Release or Store Submission)

- [ ] **Step B.2**: `push`, `login-lookup`, and `delete-account` Edge Functions deployed and **Active**.
- [ ] **Step B.3–B.5**: `0004a_run_now.sql` applied; `verify.sql` Checks 1, 2, 3, and 5 pass (`readable_count = 0`).
- [ ] **Step B.6**: New Android APK and Windows build installed; `0004b_profiles_lockdown.sql` applied; `verify.sql` Check 4 passes (`email` and `fcm_token` hidden from `anon` and `authenticated`).
- [ ] **Step B.7**: `0004c_policy_cleanup.sql` and `0004d_storage_policies.sql` applied.
- [ ] **Steps C.1–C.13**: All 13 real-device and anonymous `curl` tests pass with `0` plaintext/`|w:`/`9:` rows in `public.messages`.

#### What to Send If Anything Fails
If any step fails, send:
1. **Which step number** you were on (for example, `Step B.6` or `Step C.4`).
2. **A screenshot** of the error message (in the Supabase SQL Editor, GitHub Actions log, Android screen, or Windows app).
3. **The exact text** of any SQL query output or `curl` response.
