-- Sangam v1.3.0: one-time setup for profile photos.
-- Run in the Supabase SQL editor.

alter table public.profiles
  add column if not exists avatar_url text;

-- Public bucket for profile photos (small squares, set from the app).
insert into storage.buckets (id, name, public)
values ('avatars', 'avatars', true)
on conflict (id) do nothing;

create policy "avatars insert" on storage.objects
  for insert to authenticated
  with check (bucket_id = 'avatars');

create policy "avatars read" on storage.objects
  for select
  using (bucket_id = 'avatars');

create policy "avatars update" on storage.objects
  for update to authenticated
  using (bucket_id = 'avatars');
