-- ============================================================================
-- MIGRATION 0004a: A) RUN NOW  (safe: does not break sign-up or push)
--
-- WHAT IT DOES:
--   1. Creates public.strip_message_companion_suffix() and trigger
--      trg_messages_strip_companion (BEFORE INSERT OR UPDATE OF ciphertext ON
--      public.messages) to strip any '|w:' suffix, and scrubs existing rows.
--   2. Restricts UPDATE on public.messages to (delivered_at, read_at, deleted)
--      for authenticated users only.
--   3. Revokes EXECUTE on take_one_time_prekey(uuid) and
--      take_device_one_time_prekey(uuid, integer) from PUBLIC and anon; grants
--      EXECUTE to authenticated only.
--   4. Creates public.username_available(p_username text) for pre-login
--      sign-up username checks (returns boolean only) and grants EXECUTE to
--      anon and authenticated.
--
-- RISK:
--   Low. Safe to run immediately before updating the Android or Windows apps.
--
-- HOW TO VERIFY:
--   Run checks 1, 2, 3, and 5 in supabase/verify.sql.
--
-- HOW TO ROLL BACK:
--   drop trigger if exists trg_messages_strip_companion on public.messages;
--   drop function if exists public.strip_message_companion_suffix();
--   grant update on public.messages to authenticated;
--   grant execute on function public.take_one_time_prekey(uuid) to anon;
--   grant execute on function public.take_device_one_time_prekey(uuid, integer) to anon;
--   drop function if exists public.username_available(text);
--
-- Supabase -> SQL Editor -> New query -> paste ALL of this -> Run.
-- If a "destructive operation" warning appears, click "Run this query".
-- Then read the result: it must say Success. If it shows an error, send me a screenshot.
-- ============================================================================

-- 1) Stop any app from storing a readable copy of a message, and clean existing rows
create or replace function public.strip_message_companion_suffix()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if new.ciphertext is not null and position('|w:' in new.ciphertext) > 0 then
    new.ciphertext := split_part(new.ciphertext, '|w:', 1);
  end if;
  return new;
end;
$$;

drop trigger if exists trg_messages_strip_companion on public.messages;
create trigger trg_messages_strip_companion
  before insert or update of ciphertext on public.messages
  for each row execute function public.strip_message_companion_suffix();

update public.messages set ciphertext = split_part(ciphertext, '|w:', 1)
 where ciphertext like '%|w:%';

-- 2) Clients may only update delivered_at, read_at, deleted on messages
revoke update on public.messages from authenticated, anon;
grant update (delivered_at, read_at, deleted) on public.messages to authenticated;

-- 3) Only signed-in users may take prekeys
revoke execute on function public.take_one_time_prekey(uuid) from public, anon;
revoke execute on function public.take_device_one_time_prekey(uuid, integer) from public, anon;
grant execute on function public.take_one_time_prekey(uuid) to authenticated;
grant execute on function public.take_device_one_time_prekey(uuid, integer) to authenticated;

-- 4) Safe username check for the sign-up screen (returns only true/false, no data)
create or replace function public.username_available(p_username text)
returns boolean language sql security definer set search_path = public stable as $$
  select not exists (select 1 from public.profiles where username = lower(trim(p_username)));
$$;
revoke execute on function public.username_available(text) from public;
grant execute on function public.username_available(text) to anon, authenticated;
