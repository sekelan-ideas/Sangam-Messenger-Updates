-- 0003: minimal fix for sign-ups failing with HTTP 500.
-- Replaces the trigger function so new accounts get a rule-safe
-- default username (u + 8 characters of the user id, no hyphens).
-- Short on purpose: one statement, safe to run more than once.

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
        'u' || substr(replace(new.id::text, '-', ''), 1, 8),
        coalesce(new.raw_user_meta_data ->> 'name', 'Sangam friend')
    )
    on conflict (id) do nothing;
    return new;
end;
$$;
