-- Sangam v1.4.0 features (run once in the Supabase SQL editor)

alter table public.profiles add column if not exists email text;
alter table public.profiles add column if not exists last_seen_at timestamptz;
alter table public.profiles add column if not exists typing_for uuid;
alter table public.profiles add column if not exists typing_until timestamptz;

alter table public.messages add column if not exists deleted boolean not null default false;

drop policy if exists messages_update_own on public.messages;
create policy messages_update_own
    on public.messages for update to authenticated
    using (auth.uid() = sender_id)
    with check (auth.uid() = sender_id);
