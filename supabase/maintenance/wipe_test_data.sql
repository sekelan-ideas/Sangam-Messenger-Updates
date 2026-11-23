-- ============================================================================
-- WIPE TEST DATA (PREVIEW FIRST, THEN OPTIONAL DELETE SECTION)
--
-- STEP 1 (SAFE): Run the SELECT query below to preview row counts across every
-- user table before deleting anything.
-- ============================================================================

select 'call_chunks' as table_name, count(*) as row_count from public.call_chunks
union all select 'calls', count(*) from public.calls
union all select 'messages', count(*) from public.messages
union all select 'group_members', count(*) from public.group_members
union all select 'chat_groups', count(*) from public.chat_groups
union all select 'devices', count(*) from public.devices
union all select 'prekey_bundles', count(*) from public.prekey_bundles
union all select 'backups', count(*) from public.backups
union all select 'abuse_reports', count(*) from public.abuse_reports
union all select 'desktop_pairings', count(*) from public.desktop_pairings
union all select 'profiles', count(*) from public.profiles;

-- ============================================================================
-- STEP 2 (DESTRUCTIVE - UNCOMMENT ONLY WHEN YOU WANT TO WIPE ALL TEST DATA):
-- ============================================================================
-- delete from public.call_chunks;
-- delete from public.calls;
-- delete from public.messages;
-- delete from public.group_members;
-- delete from public.chat_groups;
-- delete from public.devices;
-- delete from public.prekey_bundles;
-- delete from public.backups;
-- delete from public.abuse_reports;
-- delete from public.desktop_pairings;
-- delete from public.profiles;

-- ============================================================================
-- STEP 3 (DASHBOARD CLEANUP):
-- Auth users and Storage files must be deleted from the Supabase Dashboard:
--   1. Authentication -> Users -> select test users -> Delete user.
--   2. Storage -> avatars -> delete:
--        - <userId>/ folders and <userId>.jpg / <userId>.png
--        - devs_<userId>.json
--        - pair_<codeId>.bin
--        - outbox_<userId>.bin
--        - typing_<userId>.json
--        - grp_meta_<groupId>.json
--   3. Storage -> message-images -> delete:
--        - <userId>/ folders, img/*, doc/*, audio/*
-- ============================================================================
