create table if not exists public.profiles (
    id uuid primary key references auth.users (id) on delete cascade,
    username text not null,
    display_name text not null,
    created_at timestamptz not null default now()
);

alter table public.profiles drop constraint if exists profiles_username_key;

alter table public.profiles add constraint profiles_username_key unique (username);

alter table public.profiles enable row level security;

drop policy if exists profiles_select_all on public.profiles;

create policy profiles_select_all
    on public.profiles for select to authenticated using (true);

drop policy if exists profiles_insert_own on public.profiles;

create policy profiles_insert_own
    on public.profiles for insert to authenticated with check (auth.uid() = id);

drop policy if exists profiles_update_own on public.profiles;

create policy profiles_update_own
    on public.profiles for update to authenticated using (auth.uid() = id);

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    insert into public.profiles (id, username, display_name)
    values (new.id, substr(new.id::text, 1, 12), coalesce(new.raw_user_meta_data ->> 'name', 'Sangam friend'))
    on conflict (id) do nothing;
    return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;

create trigger on_auth_user_created
    after insert on auth.users
    for each row execute function public.handle_new_user();

create table if not exists public.prekey_bundles (
    user_id uuid primary key references public.profiles (id) on delete cascade,
    registration_id integer not null,
    identity_key text not null,
    signed_prekey_id integer not null,
    signed_prekey text not null,
    signed_prekey_signature text not null,
    one_time_prekeys text[] not null default '{}'
);

alter table public.prekey_bundles enable row level security;

drop policy if exists bundles_select_all on public.prekey_bundles;

create policy bundles_select_all
    on public.prekey_bundles for select to authenticated using (true);

drop policy if exists bundles_insert_own on public.prekey_bundles;

create policy bundles_insert_own
    on public.prekey_bundles for insert to authenticated with check (auth.uid() = user_id);

drop policy if exists bundles_update_own on public.prekey_bundles;

create policy bundles_update_own
    on public.prekey_bundles for update to authenticated using (auth.uid() = user_id);

create or replace function public.take_one_time_prekey(p_user_id uuid)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
    b public.prekey_bundles%rowtype;
    k text;
begin
    select * into b from public.prekey_bundles where user_id = p_user_id for update;
    if not found or cardinality(b.one_time_prekeys) = 0 then
        return '';
    end if;
    k := b.one_time_prekeys[1];
    update public.prekey_bundles
       set one_time_prekeys = b.one_time_prekeys[2:cardinality(b.one_time_prekeys)]
     where user_id = p_user_id;
    return k;
end;
$$;

create or replace function public.add_one_time_prekeys(new_keys text[])
returns void
language sql
security definer
set search_path = public
as $$
    insert into public.prekey_bundles (user_id, one_time_prekeys)
    values (auth.uid(), new_keys)
    on conflict (user_id)
    do update set one_time_prekeys = public.prekey_bundles.one_time_prekeys || new_keys;
$$;

create table if not exists public.messages (
    id uuid primary key default gen_random_uuid(),
    sender_id uuid not null references public.profiles (id) on delete cascade,
    recipient_id uuid not null references public.profiles (id) on delete cascade,
    ciphertext text not null,
    created_at timestamptz not null default now(),
    delivered_at timestamptz,
    read_at timestamptz
);

create index if not exists messages_sender_idx on public.messages (sender_id, created_at desc);

create index if not exists messages_recipient_idx on public.messages (recipient_id, created_at desc);

alter table public.messages enable row level security;

drop policy if exists messages_read_own on public.messages;

create policy messages_read_own
    on public.messages for select to authenticated
    using (auth.uid() = sender_id or auth.uid() = recipient_id);

drop policy if exists messages_insert_own on public.messages;

create policy messages_insert_own
    on public.messages for insert to authenticated
    with check (auth.uid() = sender_id);

drop policy if exists messages_update_received on public.messages;

create policy messages_update_received
    on public.messages for update to authenticated
    using (auth.uid() = recipient_id);

insert into storage.buckets (id, name, public)
values ('message-images', 'message-images', true)
on conflict (id) do nothing;

drop policy if exists images_insert on storage.objects;

create policy images_insert
    on storage.objects for insert to authenticated
    with check (bucket_id = 'message-images');

drop policy if exists images_update on storage.objects;

create policy images_update
    on storage.objects for update to authenticated
    using (bucket_id = 'message-images');
