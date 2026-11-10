-- ============================================================================
-- MIGRATION 0004c: RLS POLICY DEDUPLICATION & LOGIN-LOOKUP RATE LIMIT TABLE
--
-- WHAT IT DOES:
--   1. Lists current policies on devices, messages, prekey_bundles, profiles.
--   2. Removes duplicate policies on messages, prekey_bundles, profiles, and
--      devices, keeping one clean policy per action (roles TO authenticated;
--      profiles SELECT policy does NOT allow anon).
--   3. Creates public.login_lookup_rate_limits (service_role only) used by the
--      login-lookup Edge Function for per-IP rate limiting.
--   4. Previews duplicate (user_id, device_key) rows in public.devices with a
--      commented-out DELETE section below it.
--
-- RISK:
--   Low once 0004a_run_now.sql and the new apps are live. Restricts profiles
--   SELECT policy to authenticated users.
--
-- HOW TO VERIFY:
--   Re-run the opening SELECT query below and confirm one policy per action
--   per table with roles = {authenticated}.
--
-- HOW TO ROLL BACK:
--   drop policy if exists "profiles_select_authenticated" on public.profiles;
--   create policy "profiles_select_all" on public.profiles for select using (true);
-- ============================================================================

-- Preview current policies before cleanup
select tablename, policyname, cmd, roles, qual, with_check
  from pg_policies
 where schemaname = 'public'
   and tablename in ('devices', 'messages', 'prekey_bundles', 'profiles')
 order by tablename, cmd, policyname;

-- 1) public.devices: one policy per action (TO authenticated)
alter table public.devices enable row level security;
drop policy if exists "dev own" on public.devices;
drop policy if exists "devices_delete_own" on public.devices;
drop policy if exists "devices_insert_own" on public.devices;
drop policy if exists "devices_select_authenticated" on public.devices;
drop policy if exists "devices_update_own" on public.devices;

create policy "devices_select_authenticated"
  on public.devices for select to authenticated using (true);
create policy "devices_insert_own"
  on public.devices for insert to authenticated with check (auth.uid() = user_id);
create policy "devices_update_own"
  on public.devices for update to authenticated using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "devices_delete_own"
  on public.devices for delete to authenticated using (auth.uid() = user_id);

-- 2) public.messages: one policy per action (TO authenticated)
alter table public.messages enable row level security;
drop policy if exists "messages_insert_own" on public.messages;
drop policy if exists "messages_read_own" on public.messages;
drop policy if exists "messages_update_own" on public.messages;
drop policy if exists "messages_update_received" on public.messages;
drop policy if exists "own rows delete" on public.messages;
drop policy if exists "participants can read their messages" on public.messages;
drop policy if exists "recipients can update delivery/read state" on public.messages;
drop policy if exists "users send as themselves" on public.messages;
drop policy if exists "messages_select_participant" on public.messages;
drop policy if exists "messages_update_participant" on public.messages;
drop policy if exists "messages_delete_participant" on public.messages;

create policy "messages_select_participant"
  on public.messages for select to authenticated
  using ((auth.uid() = sender_id) or (auth.uid() = recipient_id));
create policy "messages_insert_own"
  on public.messages for insert to authenticated
  with check (auth.uid() = sender_id);
create policy "messages_update_participant"
  on public.messages for update to authenticated
  using ((auth.uid() = sender_id) or (auth.uid() = recipient_id))
  with check ((auth.uid() = sender_id) or (auth.uid() = recipient_id));
create policy "messages_delete_participant"
  on public.messages for delete to authenticated
  using ((auth.uid() = sender_id) or (auth.uid() = recipient_id));

-- 3) public.prekey_bundles: one policy per action (TO authenticated)
alter table public.prekey_bundles enable row level security;
drop policy if exists "bundles are readable by signed-in users" on public.prekey_bundles;
drop policy if exists "bundles_insert_own" on public.prekey_bundles;
drop policy if exists "bundles_select_all" on public.prekey_bundles;
drop policy if exists "bundles_update_own" on public.prekey_bundles;
drop policy if exists "users manage their own bundle" on public.prekey_bundles;
drop policy if exists "users update their own bundle" on public.prekey_bundles;
drop policy if exists "bundles_delete_own" on public.prekey_bundles;

create policy "bundles_select_all"
  on public.prekey_bundles for select to authenticated using (true);
create policy "bundles_insert_own"
  on public.prekey_bundles for insert to authenticated with check (auth.uid() = user_id);
create policy "bundles_update_own"
  on public.prekey_bundles for update to authenticated using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "bundles_delete_own"
  on public.prekey_bundles for delete to authenticated using (auth.uid() = user_id);

-- 4) public.profiles: one policy per action (TO authenticated; anon disallowed)
alter table public.profiles enable row level security;
drop policy if exists "profiles are readable by signed-in users" on public.profiles;
drop policy if exists "profiles self update" on public.profiles;
drop policy if exists "profiles_insert_own" on public.profiles;
drop policy if exists "profiles_select_all" on public.profiles;
drop policy if exists "profiles_select_authenticated" on public.profiles;
drop policy if exists "profiles_update_own" on public.profiles;
drop policy if exists "users insert their own profile" on public.profiles;
drop policy if exists "users update their own profile" on public.profiles;
drop policy if exists "profiles_delete_own" on public.profiles;

create policy "profiles_select_authenticated"
  on public.profiles for select to authenticated using (true);
create policy "profiles_insert_own"
  on public.profiles for insert to authenticated with check (auth.uid() = id);
create policy "profiles_update_own"
  on public.profiles for update to authenticated
  using (auth.uid() = id)
  with check ((auth.uid() = id) and (username = (select p.username from public.profiles p where p.id = auth.uid())));
create policy "profiles_delete_own"
  on public.profiles for delete to authenticated using (auth.uid() = id);

-- 5) Rate-limit table for login-lookup Edge Function (service_role only)
create table if not exists public.login_lookup_rate_limits (
  ip_hash text not null,
  window_start timestamptz not null,
  request_count integer not null default 1,
  primary key (ip_hash, window_start)
);
alter table public.login_lookup_rate_limits enable row level security;
revoke all on public.login_lookup_rate_limits from public, anon, authenticated;
grant all on public.login_lookup_rate_limits to service_role;

-- ============================================================================
-- OPTIONAL MANUAL STEP: Preview duplicate (user_id, device_key) rows in devices
-- Run the SELECT below first to inspect any duplicates before uncommenting DELETE.
-- ============================================================================
select d.id, d.user_id, d.device_key, d.label, d.created_at
  from public.devices d
  join public.devices newer
    on d.user_id = newer.user_id
   and coalesce(d.device_key, 1) = coalesce(newer.device_key, 1)
   and d.id <> newer.id
   and (
     coalesce(d.created_at, '1970-01-01'::timestamptz) < coalesce(newer.created_at, '1970-01-01'::timestamptz)
     or (coalesce(d.created_at, '1970-01-01'::timestamptz) = coalesce(newer.created_at, '1970-01-01'::timestamptz) and d.id::text < newer.id::text)
   );

-- Uncomment ONLY after reviewing the SELECT preview above:
-- delete from public.devices d
--  using public.devices newer
--  where d.user_id = newer.user_id
--    and coalesce(d.device_key, 1) = coalesce(newer.device_key, 1)
--    and d.id <> newer.id
--    and (
--      coalesce(d.created_at, '1970-01-01'::timestamptz) < coalesce(newer.created_at, '1970-01-01'::timestamptz)
--      or (coalesce(d.created_at, '1970-01-01'::timestamptz) = coalesce(newer.created_at, '1970-01-01'::timestamptz) and d.id::text < newer.id::text)
--    );
-- create unique index if not exists devices_user_device_key_uniq on public.devices (user_id, device_key);
