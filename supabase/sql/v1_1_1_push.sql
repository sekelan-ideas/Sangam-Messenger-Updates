-- Sangam v1.1.1: one-time database setup for push notifications.
-- Run this in the Supabase SQL editor, then follow the function setup in
-- supabase/functions/push/README (see chat for instructions).

alter table public.profiles
  add column if not exists fcm_token text;

-- Users may update their own profile row (needed to store their phone's
-- push token), but they still cannot change their username.
drop policy if exists "profiles self update" on public.profiles;
create policy "profiles self update"
  on public.profiles
  for update
  to authenticated
  using (auth.uid() = id)
  with check (
    auth.uid() = id
    and username = (
      select p.username from public.profiles p where p.id = auth.uid()
    )
  );
