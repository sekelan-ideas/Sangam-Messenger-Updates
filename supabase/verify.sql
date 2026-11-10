-- ============================================================================
-- SANGAM DATABASE VERIFICATION QUERIES
-- Run each query below in the Supabase SQL Editor and compare with EXPECTED.
-- ============================================================================

-- CHECK 1: Companion-suffix strip trigger exists on public.messages
-- EXPECTED: 1 row (tgname = 'trg_messages_strip_companion')
select tgname
  from pg_trigger
 where tgrelid = 'public.messages'::regclass
   and tgname = 'trg_messages_strip_companion'
   and not tgisinternal;

-- CHECK 2: No readable '|w:' or '9:' rows remain in public.messages
-- EXPECTED: 1 row with readable_count = 0
select count(*) as readable_count
  from public.messages
 where ciphertext like '%|w:%'
    or ciphertext like '9:%';

-- CHECK 3: Prekey functions are NOT executable by PUBLIC or anon
-- EXPECTED: Only 'authenticated', 'postgres', and 'service_role' appear.
--           'PUBLIC' and 'anon' MUST NOT appear.
select routine_name, grantee, privilege_type
  from information_schema.routine_privileges
 where routine_schema = 'public'
   and routine_name in ('take_one_time_prekey', 'take_device_one_time_prekey')
 order by routine_name, grantee;

-- CHECK 4: Profiles column privileges (after running 0004b_profiles_lockdown.sql)
-- EXPECTED:
--   - 'anon' appears 0 times.
--   - 'authenticated' has ONLY the 9 safe columns:
--     (avatar_url, created_at, display_name, id, last_seen_at, status,
--      typing_for, typing_until, username).
--   - 'email' and 'fcm_token' MUST NOT appear for 'anon' or 'authenticated'.
select grantee, column_name, privilege_type
  from information_schema.column_privileges
 where table_schema = 'public'
   and table_name = 'profiles'
   and grantee in ('anon', 'authenticated')
 order by grantee, column_name;

-- CHECK 5: username_available exists and is executable by anon and authenticated
-- EXPECTED: 2 rows for grantees ('anon', 'authenticated')
select routine_name, grantee, privilege_type
  from information_schema.routine_privileges
 where routine_schema = 'public'
   and routine_name = 'username_available'
   and grantee in ('anon', 'authenticated')
 order by grantee;
