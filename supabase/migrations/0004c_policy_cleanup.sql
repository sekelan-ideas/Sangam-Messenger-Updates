-- ============================================================================
-- 0004c (FIXED) - clean up duplicate policies and protect message rows
-- Run in Supabase -> SQL Editor -> new tab -> paste ALL -> Run.
-- It runs as ONE all-or-nothing batch: if anything fails, nothing is changed.
-- Why this replaces Gemini's Round-4 0004c:
--   * Gemini's file dropped policy names it guessed. Your real policies have other names,
--     so the old duplicates stayed and new ones were added on top.
--   * Gemini's trigger blocked recipients from setting "deleted", which would break
--     "clear group chat" in the Android app (clearGroupChat sets deleted as the recipient).
-- Verify afterwards:
--   select tablename, policyname, cmd from pg_policies
--   where schemaname='public' and tablename in ('messages','prekey_bundles','profiles','devices')
--   order by 1, 3, 2;
-- ============================================================================

-- 1) Column-level update rights on messages
revoke update on public.messages from anon, authenticated;
grant update (delivered_at, read_at, deleted) on public.messages to authenticated;

-- 2) Row protection that works no matter which policies exist
create or replace function public.enforce_message_update_actor()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is not null then
    -- only the recipient may mark delivered / read
    if (new.delivered_at is distinct from old.delivered_at or new.read_at is distinct from old.read_at)
       and auth.uid() <> old.recipient_id then
      raise exception 'Only the recipient may update delivered_at or read_at';
    end if;
    -- sender or recipient may delete (the sender for everyone, the recipient for their own chat), never undelete
    if new.deleted is distinct from old.deleted then
      if new.deleted = false then
        raise exception 'A deleted message cannot be restored';
      end if;
      if auth.uid() <> old.sender_id and auth.uid() <> old.recipient_id then
        raise exception 'Only the sender or recipient may delete a message';
      end if;
    end if;
    -- these never change after sending
    if new.sender_id <> old.sender_id or new.recipient_id <> old.recipient_id
       or new.ciphertext is distinct from old.ciphertext or new.group_id is distinct from old.group_id then
      raise exception 'This message column cannot be changed';
    end if;
  end if;
  return new;
end;
$$;

drop trigger if exists trg_messages_enforce_update_actor on public.messages;
create trigger trg_messages_enforce_update_actor
  before update on public.messages
  for each row execute function public.enforce_message_update_actor();

-- 3) Remove EVERY existing policy on these four tables (whatever its name), then recreate one clean set
alter table public.messages enable row level security;
alter table public.prekey_bundles enable row level security;
alter table public.profiles enable row level security;
alter table public.devices enable row level security;

do $$
declare pol record;
begin
  for pol in
    select schemaname, tablename, policyname from pg_policies
    where schemaname = 'public' and tablename in ('messages', 'prekey_bundles', 'profiles', 'devices')
  loop
    execute format('drop policy if exists %I on %I.%I', pol.policyname, pol.schemaname, pol.tablename);
  end loop;
end $$;

-- messages
create policy messages_select_own on public.messages for select to authenticated
  using (auth.uid() = sender_id or auth.uid() = recipient_id);
create policy messages_insert_sender on public.messages for insert to authenticated
  with check (auth.uid() = sender_id);
create policy messages_update_participant on public.messages for update to authenticated
  using (auth.uid() = sender_id or auth.uid() = recipient_id)
  with check (auth.uid() = sender_id or auth.uid() = recipient_id);
create policy messages_delete_recipient on public.messages for delete to authenticated
  using (auth.uid() = recipient_id);

-- prekey_bundles
create policy prekey_bundles_select on public.prekey_bundles for select to authenticated using (true);
create policy prekey_bundles_insert_own on public.prekey_bundles for insert to authenticated
  with check (auth.uid() = user_id);
create policy prekey_bundles_update_own on public.prekey_bundles for update to authenticated
  using (auth.uid() = user_id) with check (auth.uid() = user_id);

-- profiles (no client delete; account deletion goes through the delete-account function)
create policy profiles_select on public.profiles for select to authenticated using (true);
create policy profiles_insert_own on public.profiles for insert to authenticated
  with check (auth.uid() = id);
create policy profiles_update_own on public.profiles for update to authenticated
  using (auth.uid() = id) with check (auth.uid() = id);

-- devices
create policy devices_select on public.devices for select to authenticated using (true);
create policy devices_insert_own on public.devices for insert to authenticated
  with check (auth.uid() = user_id);
create policy devices_update_own on public.devices for update to authenticated
  using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy devices_delete_own on public.devices for delete to authenticated
  using (auth.uid() = user_id);
