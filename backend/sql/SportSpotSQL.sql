-- SportSpot
-- PostgreSQL / Supabase: базы данных
-- Доступ клиентским ролям пока не выдаётся.

begin;

-- 1. Профили. Пользователи и пароли управляются Supabase Auth.
create table public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  name text not null default '',
  role text not null default 'user'
    check (role in ('user', 'moderator')),
  created_at timestamptz not null default now()
);

-- 2. Спортивные площадки
create table public.spots (
  id text primary key,
  name text not null,
  sport_type text not null,
  latitude numeric(9,6) not null check (latitude between -90 and 90),
  longitude numeric(9,6) not null check (longitude between -180 and 180),
  address text,
  surface text,
  lighting boolean,
  equipment text,
  source text not null check (source in ('osm', 'community')),
  osm_type text check (osm_type in ('node', 'way', 'relation')),
  osm_id bigint,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint spots_osm_identity check (
    (source = 'osm' and osm_type is not null and osm_id is not null)
    or
    (source = 'community' and osm_type is null and osm_id is null)
  ),
  constraint spots_osm_unique unique (osm_type, osm_id)
);

-- 3. Отзывы
create table public.reviews (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid()
    references public.profiles(id) on delete cascade,
  spot_id text not null references public.spots(id) on delete cascade,
  rating smallint not null check (rating between 1 and 5),
  text text not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint reviews_user_spot_unique unique (user_id, spot_id)
);

-- 4. Избранное
create table public.favorites (
  user_id uuid not null default auth.uid()
    references public.profiles(id) on delete cascade,
  spot_id text not null references public.spots(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (user_id, spot_id)
);

-- 5. Предложения новых площадок с полями модерации.
create table public.spot_proposals (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid()
    references public.profiles(id) on delete cascade,
  name text not null,
  sport_type text not null,
  latitude numeric(9,6) not null check (latitude between -90 and 90),
  longitude numeric(9,6) not null check (longitude between -180 and 180),
  address text,
  surface text,
  lighting boolean,
  equipment text,
  description text,
  status text not null default 'pending'
    check (status in ('pending', 'approved', 'rejected')),
  reject_reason text,
  moderator_id uuid references public.profiles(id) on delete set null,
  reviewed_at timestamptz,
  created_at timestamptz not null default now()
);

-- 6. Фотографии площадок; файлы хранятся в Supabase Storage.
create table public.spot_photos (
  id uuid primary key default gen_random_uuid(),
  spot_id text not null references public.spots(id) on delete cascade,
  storage_path text not null,
  uploaded_by uuid references public.profiles(id) on delete set null,
  created_at timestamptz not null default now()
);

-- 7. Фотографии отзывов.
create table public.review_photos (
  id uuid primary key default gen_random_uuid(),
  review_id uuid not null references public.reviews(id) on delete cascade,
  storage_path text not null,
  created_at timestamptz not null default now()
);

-- 8. Фотографии предложений.
create table public.proposal_photos (
  id uuid primary key default gen_random_uuid(),
  proposal_id uuid not null references public.spot_proposals(id) on delete cascade,
  storage_path text not null,
  created_at timestamptz not null default now()
);

-- Включаем RLS на каждой таблице, независимо от настроек проекта.
alter table public.profiles enable row level security;
alter table public.spots enable row level security;
alter table public.reviews enable row level security;
alter table public.favorites enable row level security;
alter table public.spot_proposals enable row level security;
alter table public.spot_photos enable row level security;
alter table public.review_photos enable row level security;
alter table public.proposal_photos enable row level security;

commit;
