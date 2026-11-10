-- Sangam v1.7.0 - adds the status/about line on profiles
alter table public.profiles
add column if not exists status text;
