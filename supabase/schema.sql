-- Notifilter cloud backup schema

-- Settings snapshot
create table if not exists public.notifilter_settings (
  user_id uuid primary key default auth.uid(),
  updated_at timestamptz not null default now(),
  prefs jsonb not null
);

-- Archive (last 7 days)
create table if not exists public.notifilter_archive (
  user_id uuid not null default auth.uid(),
  dedupe_key text not null,
  package_name text not null,
  app_name text not null,
  content text not null,
  channel_id text,
  timestamp_ms bigint not null,
  is_blocked boolean not null,
  block_reason text,
  inserted_at timestamptz not null default now(),
  primary key (user_id, dedupe_key)
);

alter table public.notifilter_settings enable row level security;
alter table public.notifilter_archive enable row level security;

create policy "settings_select_own" on public.notifilter_settings
for select to authenticated
using (user_id = auth.uid());

create policy "settings_upsert_own" on public.notifilter_settings
for insert to authenticated
with check (user_id = auth.uid());

create policy "settings_update_own" on public.notifilter_settings
for update to authenticated
using (user_id = auth.uid())
with check (user_id = auth.uid());

create policy "archive_select_own" on public.notifilter_archive
for select to authenticated
using (user_id = auth.uid());

create policy "archive_insert_own" on public.notifilter_archive
for insert to authenticated
with check (user_id = auth.uid());

create policy "archive_update_own" on public.notifilter_archive
for update to authenticated
using (user_id = auth.uid())
with check (user_id = auth.uid());
