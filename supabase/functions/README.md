# Sangam Supabase Edge Functions

This folder contains the three Supabase Edge Functions used by Sangam. Every function is a single `index.ts` file that you paste into the Supabase Dashboard under **Edge Functions**.

## Summary of Edge Functions & Client Usage

| Function Name | Dashboard Location | Supabase Client(s) Used | Reads `email` or `fcm_token`? | Custom Secrets Required | Keep "Verify JWT" ON? |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **`push`** | `Edge Functions` → `push` → `Code` | 1. `supa` (`SUPABASE_ANON_KEY` + caller's `Authorization` JWT) to verify caller (`auth.getUser()`) and read caller's own `username, display_name`.<br>2. `admin` (`SUPABASE_SERVICE_ROLE_KEY`) solely to read recipient's `id, fcm_token`. | Reads `fcm_token` using **`admin` (`service_role`) only** — never with a user JWT. | **`FCM_SERVICE_ACCOUNT`** (Firebase service-account JSON) | **ON** (called by signed-in users with their user JWT) |
| **`login-lookup`** | `Edge Functions` → `login-lookup` → `Code` | `admin` (`SUPABASE_SERVICE_ROLE_KEY`) to check `public.login_lookup_rate_limits` (10 requests / 10 minutes per IP hash) and resolve `username` → `email` in `public.profiles`. | Reads `email` using **`admin` (`service_role`) only** — never with a user JWT. | **None** (`SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY` are built in) | **ON** (called before login with `Authorization: Bearer <SUPABASE_ANON_KEY>`, which is a valid project JWT) |
| **`delete-account`** | `Edge Functions` → `delete-account` → `Code` | 1. `supa` (`SUPABASE_ANON_KEY` + caller's `Authorization` JWT) to verify the signed-in user (`auth.getUser()`).<br>2. `admin` (`SUPABASE_SERVICE_ROLE_KEY`) to remove user files from `avatars` and `message-images`, transfer group leadership or delete empty groups, delete rows across user tables, and call `auth.admin.deleteUser(uid)`. | Does not read `profiles.email` or `profiles.fcm_token` with a user JWT (uses `user.email` from `supa.auth.getUser()`). | **None** (`SUPABASE_URL`, `SUPABASE_ANON_KEY`, and `SUPABASE_SERVICE_ROLE_KEY` are built in) | **ON** (called by signed-in users with their user JWT) |

## Verification that No Function Reads `email` or `fcm_token` with a User JWT

- **`push/index.ts`**: Uses `supa` (user JWT) only for `.from("profiles").select("username, display_name").eq("id", user.id).single()`. Uses `admin` (`SUPABASE_SERVICE_ROLE_KEY`) for `.from("profiles").select("id, fcm_token").eq("id", String(to)).single()`.
- **`login-lookup/index.ts`**: Uses `admin` (`SUPABASE_SERVICE_ROLE_KEY`) only.
- **`delete-account/index.ts`**: Uses `supa` (user JWT) only for `supa.auth.getUser()`. All database and storage operations use `admin` (`SUPABASE_SERVICE_ROLE_KEY`).
