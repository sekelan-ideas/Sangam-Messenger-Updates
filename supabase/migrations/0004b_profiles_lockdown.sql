-- ============================================================================
-- MIGRATION 0004b: B) RUN LATER - only AFTER all three are true:
--    1. The patched "push" Edge Function is deployed.
--    2. The new Android app (that checks usernames with the username_available function) is installed.
--    3. You have tested login and sign-up with the new apps.
-- If you run it earlier, the OLD Android app's sign-up screen will say every username is "taken".
-- This hides email and push tokens from other users and from anonymous visitors.
--
-- WHAT IT DOES:
--   Revokes table-wide SELECT on public.profiles from anon and authenticated,
--   and grants SELECT only on (id, username, display_name, created_at,
--   avatar_url, last_seen_at, typing_for, typing_until, status) to authenticated.
--
-- RISK:
--   High if run before the new Android app and patched push function are live
--   (breaks old sign-up username check and old push token lookup). Low once
--   both are live.
--
-- HOW TO VERIFY:
--   Run check 4 in supabase/verify.sql.
--
-- HOW TO ROLL BACK:
--   grant select on public.profiles to anon, authenticated;
-- ============================================================================
revoke select on public.profiles from anon, authenticated;
grant select (id, username, display_name, created_at, avatar_url, last_seen_at, typing_for, typing_until, status)
  on public.profiles to authenticated;
