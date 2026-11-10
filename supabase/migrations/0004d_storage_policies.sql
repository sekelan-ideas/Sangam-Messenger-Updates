-- ============================================================================
-- MIGRATION 0004d: STORAGE OWNERSHIP POLICIES (avatars & message-images)
--
-- ROLE REQUIREMENT & ERROR NOTE:
--   Must be run as role "postgres" in the Supabase SQL Editor (the default in
--   the top-right Role dropdown). On some hosted Supabase instances where
--   storage.objects is owned by supabase_storage_admin rather than postgres,
--   running this script directly may return:
--     ERROR: 42501: must be owner of table objects
--   If you see that error, create these policies in the Supabase Dashboard
--   under Storage -> Policies -> avatars / message-images instead.
--
-- WHAT IT DOES:
--   Enforces per-user ownership on storage.objects for buckets 'avatars' and
--   'message-images' while allowing public reads of avatar files and encrypted
--   media blobs.
--
-- RISK:
--   Low. Does not delete or modify existing files.
--
-- HOW TO VERIFY:
--   select policyname, cmd, roles from pg_policies
--    where schemaname = 'storage' and tablename = 'objects';
--
-- HOW TO ROLL BACK:
--   drop policy if exists "sangam_avatars_select" on storage.objects;
--   drop policy if exists "sangam_avatars_insert" on storage.objects;
--   drop policy if exists "sangam_avatars_update" on storage.objects;
--   drop policy if exists "sangam_avatars_delete" on storage.objects;
--   drop policy if exists "sangam_media_select" on storage.objects;
--   drop policy if exists "sangam_media_insert" on storage.objects;
--   drop policy if exists "sangam_media_delete" on storage.objects;
-- ============================================================================

drop policy if exists "sangam_avatars_select" on storage.objects;
drop policy if exists "sangam_avatars_insert" on storage.objects;
drop policy if exists "sangam_avatars_update" on storage.objects;
drop policy if exists "sangam_avatars_delete" on storage.objects;
drop policy if exists "sangam_media_select" on storage.objects;
drop policy if exists "sangam_media_insert" on storage.objects;
drop policy if exists "sangam_media_delete" on storage.objects;

create policy "sangam_avatars_select"
  on storage.objects for select to public
  using (bucket_id = 'avatars');

create policy "sangam_avatars_insert"
  on storage.objects for insert to authenticated
  with check (
    bucket_id = 'avatars' and (
      name like (auth.uid()::text || '%')
      or name like ('devs_' || auth.uid()::text || '.json')
      or name like ('outbox_' || auth.uid()::text || '.%')
      or name like ('typing_' || auth.uid()::text || '.json')
      or name like 'pair_%'
      or name like 'grp_meta_%'
    )
  );

create policy "sangam_avatars_update"
  on storage.objects for update to authenticated
  using (
    bucket_id = 'avatars' and (
      name like (auth.uid()::text || '%')
      or name like ('devs_' || auth.uid()::text || '.json')
      or name like ('outbox_' || auth.uid()::text || '.%')
      or name like ('typing_' || auth.uid()::text || '.json')
      or name like 'pair_%'
      or name like 'grp_meta_%'
    )
  );

create policy "sangam_avatars_delete"
  on storage.objects for delete to authenticated
  using (
    bucket_id = 'avatars' and (
      name like (auth.uid()::text || '%')
      or name like ('devs_' || auth.uid()::text || '.json')
      or name like ('outbox_' || auth.uid()::text || '.%')
      or name like ('typing_' || auth.uid()::text || '.json')
      or name like 'pair_%'
      or name like 'grp_meta_%'
    )
  );

create policy "sangam_media_select"
  on storage.objects for select to public
  using (bucket_id = 'message-images');

create policy "sangam_media_insert"
  on storage.objects for insert to authenticated
  with check (bucket_id = 'message-images');

create policy "sangam_media_delete"
  on storage.objects for delete to authenticated
  using (bucket_id = 'message-images' and owner = auth.uid());
