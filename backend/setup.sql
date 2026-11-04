BEGIN;

DO $check$
BEGIN
  IF to_regclass('public.profiles') IS NOT NULL
     OR to_regclass('public.spots') IS NOT NULL
     OR to_regclass('public.spot_proposals') IS NOT NULL THEN
    RAISE EXCEPTION 'SportSpot setup.sql must run on a fresh Supabase project only';
  END IF;
END;
$check$;


-- 01. Профиль.
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

-- ==== 02. Default permissions: deny by default ====
-- Supabase may provide broad default privileges on SQL-created public tables.
-- Explicitly opt in to only what the client needs; leave photo tables closed.
REVOKE ALL ON TABLE
  public.profiles, public.spots, public.reviews, public.favorites,
  public.spot_proposals, public.spot_photos, public.review_photos,
  public.proposal_photos
FROM PUBLIC, anon, authenticated, service_role;

GRANT USAGE ON SCHEMA public TO anon, authenticated, service_role;






CREATE SCHEMA IF NOT EXISTS app_private;
REVOKE ALL ON SCHEMA app_private FROM PUBLIC, anon, authenticated, service_role;

CREATE OR REPLACE FUNCTION app_private.create_profile_for_new_auth_user()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $function$
BEGIN
  INSERT INTO public.profiles (id, name, role)
  VALUES (
    NEW.id,
    COALESCE(
      NULLIF(btrim(NEW.raw_user_meta_data ->> 'name'), ''),
      NULLIF(btrim(NEW.email), ''),
      ''
    ),
    'user'
  )
  ON CONFLICT (id) DO NOTHING;
  RETURN NEW;
END;
$function$;

REVOKE ALL ON FUNCTION app_private.create_profile_for_new_auth_user()
FROM PUBLIC, anon, authenticated, service_role;

CREATE TRIGGER sportspot_auth_user_created
  AFTER INSERT ON auth.users
  FOR EACH ROW
  EXECUTE FUNCTION app_private.create_profile_for_new_auth_user();


-- ==== 04. Public read: spots + reviews ====
grant usage on schema public to anon, authenticated;
grant select on table public.spots, public.reviews to anon, authenticated;

create policy "Public can read spots"
on public.spots for select to anon, authenticated
using (true);

create policy "Public can read reviews"
on public.reviews for select to anon, authenticated
using (true);

-- ==== 05. Reviews: author CRUD ====
grant insert (spot_id, rating, text) on table public.reviews to authenticated;
grant update (rating, text) on table public.reviews to authenticated;
grant delete on table public.reviews to authenticated;

create policy "Users can create own reviews"
on public.reviews
for insert to authenticated
with check (user_id = (select auth.uid()));

create policy "Users can update own reviews"
on public.reviews
for update to authenticated
using (user_id = (select auth.uid()))
with check (user_id = (select auth.uid()));

create policy "Users can delete own reviews"
on public.reviews
for delete to authenticated
using (user_id = (select auth.uid()));

-- ==== 06. Favorites: owner access ====
grant select on table public.favorites to authenticated;
grant insert (spot_id) on table public.favorites to authenticated;
grant delete on table public.favorites to authenticated;

-- SELECT: пользователь видит только свое избранное.
create policy "Users can read own favorites"
on public.favorites
for select to authenticated
using (user_id = (select auth.uid()));

-- INSERT: пользователь добавляет площадку только в свое избранное.
create policy "Users can create own favorites"
on public.favorites
for insert to authenticated
with check (user_id = (select auth.uid()));

-- DELETE: пользователь удаляет только собственные записи.
create policy "Users can delete own favorites"
on public.favorites
for delete to authenticated
using (user_id = (select auth.uid()));

-- ==== 07. Proposals: submit + read own ====
grant select on table public.spot_proposals to authenticated;

-- Пользователь может отправлять описание площадки.
-- Поля user_id, status и поля модерации недоступны для прямой вставки.
grant insert (
  name, sport_type, latitude, longitude,
  address, surface, lighting, equipment, description
) on table public.spot_proposals to authenticated;

create policy "Users can read own spot proposals"
on public.spot_proposals
for select to authenticated
using (user_id = (select auth.uid()));

create policy "Users can submit own pending spot proposals"
on public.spot_proposals
for insert to authenticated
with check (
  user_id = (select auth.uid())
  and status = 'pending'
  and moderator_id is null
  and reviewed_at is null
  and reject_reason is null
);

-- ==== 08. Profile: read own + update name ====
grant select on table public.profiles to authenticated;

-- Клиент может менять только имя, но не роль, ID или дату создания.
grant update (name) on table public.profiles to authenticated;

create policy "Users can read own profile"
on public.profiles
for select to authenticated
using (id = (select auth.uid()));

create policy "Users can update own profile"
on public.profiles
for update to authenticated
using (id = (select auth.uid()))
with check (id = (select auth.uid()));

-- ==== 09. Moderator: read proposal queue ====
create policy "Moderators can read proposals"
on public.spot_proposals
for select to authenticated
using (
  exists (
    select 1
    from public.profiles as my_profile
    where my_profile.id = (select auth.uid())
      and my_profile.role = 'moderator'
  )
);

-- ==== 10. Atomic moderation RPC, callable only via server role ====
GRANT USAGE ON SCHEMA public TO service_role;
GRANT SELECT ON public.profiles TO service_role;
GRANT SELECT, UPDATE ON public.spot_proposals TO service_role;
GRANT INSERT ON public.spots TO service_role;

CREATE OR REPLACE FUNCTION public.moderate_spot_proposal(
  p_proposal_id uuid,
  p_moderator_id uuid,
  p_decision text,
  p_reason text DEFAULT NULL
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = ''
AS $function$
DECLARE
  v_proposal public.spot_proposals%rowtype;
  v_spot_id text;
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM public.profiles
    WHERE id = p_moderator_id AND role = 'moderator'
  ) THEN
    RAISE EXCEPTION USING ERRCODE = '42501', MESSAGE = 'NOT_MODERATOR';
  END IF;

  IF p_proposal_id IS NULL OR p_decision IS NULL
     OR p_decision NOT IN ('approved', 'rejected') THEN
    RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'INVALID_DECISION';
  END IF;

  IF p_decision = 'rejected' AND
     (p_reason IS NULL OR length(btrim(p_reason)) = 0 OR length(p_reason) > 1000) THEN
    RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'REASON_REQUIRED';
  END IF;

  SELECT * INTO v_proposal
  FROM public.spot_proposals
  WHERE id = p_proposal_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION USING ERRCODE = 'P0002', MESSAGE = 'PROPOSAL_NOT_FOUND';
  END IF;

  IF v_proposal.status <> 'pending' THEN
    RAISE EXCEPTION USING ERRCODE = 'P0001', MESSAGE = 'PROPOSAL_ALREADY_REVIEWED';
  END IF;

  IF p_decision = 'approved' THEN
    v_spot_id := 'community:' || v_proposal.id::text;
    INSERT INTO public.spots (
      id, name, sport_type, latitude, longitude,
      address, surface, lighting, equipment, source
    ) VALUES (
      v_spot_id, v_proposal.name, v_proposal.sport_type,
      v_proposal.latitude, v_proposal.longitude,
      v_proposal.address, v_proposal.surface, v_proposal.lighting,
      v_proposal.equipment, 'community'
    );
    UPDATE public.spot_proposals
    SET status = 'approved', reject_reason = NULL,
        moderator_id = p_moderator_id, reviewed_at = now()
    WHERE id = v_proposal.id;
    RETURN jsonb_build_object(
      'proposal_id', v_proposal.id, 'status', 'approved', 'spot_id', v_spot_id
    );
  END IF;

  UPDATE public.spot_proposals
  SET status = 'rejected', reject_reason = btrim(p_reason),
      moderator_id = p_moderator_id, reviewed_at = now()
  WHERE id = v_proposal.id;
  RETURN jsonb_build_object('proposal_id', v_proposal.id, 'status', 'rejected');
END;
$function$;

REVOKE ALL ON FUNCTION public.moderate_spot_proposal(uuid, uuid, text, text)
  FROM public, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.moderate_spot_proposal(uuid, uuid, text, text)
  TO service_role;

-- Secure the existing auto-RLS event trigger function against API calls.
DO $lock$
BEGIN
  IF to_regprocedure('public.rls_auto_enable()') IS NOT NULL THEN
    REVOKE ALL ON FUNCTION public.rls_auto_enable() FROM public, anon, authenticated;
  END IF;
END;
$lock$;

COMMIT;
