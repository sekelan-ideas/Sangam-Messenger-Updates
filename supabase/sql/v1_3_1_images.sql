-- Sangam v1.3.1: one-time fix for photo sending.
-- The message-images bucket needs an upload permission rule so the app
-- can save photos into it. Run in the Supabase SQL editor.

create policy "images insert" on storage.objects
  for insert to authenticated
  with check (bucket_id = 'message-images');
