-- 0002: fix signup failing with HTTP 500.
-- handle_new_user() derived the default username from the first 12
-- characters of the user's UUID, e.g. '558ca79f-7da'. The hyphen violates
-- the username rule (a-z, 0-9, _ only), so EVERY new sign-up crashed with
-- "new row for relation profiles violates check constraint
-- profiles_username_check".
--
-- This migration regenerates a rule-safe default username ('u_' + the
-- UUID's hex characters) and normalises the username rule.
-- Idempotent: safe to run more than once.

alter table public.profiles drop constraint if exists profiles_username_check;

alter table public.profiles add constraint profiles_username_check
    check (username ~ '^[a-z0-9_]{3,20}$');

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    insert into public.profiles (id, username, display_name)
    values (
        new.id,
        'u_' || substr(replace(new.id::text, '-', ''), 1, 18),
        coalesce(new.raw_user_meta_data ->> 'name', 'Sangam friend')
    )
    on conflict (id) do nothing;
    return new;
end;
$$;
