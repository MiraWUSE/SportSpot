-- Apply after the existing SportSpot schema and reviews/OSM migration.
begin;
set local lock_timeout = '5s';

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values
  ('sportspot-photo-pending', 'sportspot-photo-pending', false, 5242880, array['image/jpeg']),
  ('sportspot-photo-approved', 'sportspot-photo-approved', false, 5242880, array['image/jpeg']);

-- Photos in either bucket are read only through the authorized application API.
create policy sportspot_photos_server_only on storage.objects
  as restrictive for all to anon, authenticated
  using (bucket_id not in ('sportspot-photo-pending', 'sportspot-photo-approved'))
  with check (bucket_id not in ('sportspot-photo-pending', 'sportspot-photo-approved'));

create function app_private.photo_canonical_spot(p_id text) returns boolean
language sql immutable strict set search_path = ''
as $$
  select case
    when p_id ~ '^osm:(node|way|relation):(0|-?[1-9][0-9]{0,18})$'
      then split_part(p_id, ':', 3)::numeric between -9223372036854775808 and 9223372036854775807
    else p_id ~ '^community:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
  end
$$;
alter table public.spot_photos drop constraint spot_photos_spot_id_fkey,
  add constraint spot_photos_canonical_spot check (app_private.photo_canonical_spot(spot_id)) not valid;

create table public.photo_submissions (
  id uuid primary key,
  owner_id uuid not null references public.profiles(id) on delete cascade,
  target_type text not null check (target_type in ('spot', 'review', 'proposal')),
  target_id text not null,
  review_id uuid references public.reviews(id) on delete cascade,
  proposal_id uuid references public.spot_proposals(id) on delete cascade,
  object_key text not null unique,
  content_hash text not null check (content_hash ~ '^[0-9a-f]{64}$'),
  status text not null default 'uploading' check (status in ('uploading', 'pending', 'approved', 'rejected')),
  rejection_reason text,
  moderator_id uuid references public.profiles(id) on delete set null,
  created_at timestamptz not null default now(),
  reviewed_at timestamptz,
  check (object_key ~ ('^' || owner_id::text || '/[0-9a-f-]{36}[.]jpg$')),
  check (
    case target_type
      when 'spot' then app_private.photo_canonical_spot(target_id) and review_id is null and proposal_id is null
      when 'review' then review_id is not null and review_id::text = target_id and proposal_id is null
      when 'proposal' then proposal_id is not null and proposal_id::text = target_id and review_id is null
    end
  )
);
create index photo_submissions_owner_target on public.photo_submissions (owner_id, target_type, target_id);
create index photo_submissions_gallery on public.photo_submissions (target_type, target_id, created_at, id) where status = 'approved';
create index photo_submissions_queue on public.photo_submissions (created_at, id) where status = 'pending';
create index photo_submissions_uploading on public.photo_submissions (created_at) where status = 'uploading';
create index photo_submissions_review on public.photo_submissions (review_id) where review_id is not null;
create index photo_submissions_proposal on public.photo_submissions (proposal_id) where proposal_id is not null;
create index photo_submissions_moderator on public.photo_submissions (moderator_id) where moderator_id is not null;

create table app_private.photo_cleanup (
  bucket text not null,
  object_key text not null,
  -- Give in-flight Edge requests time to finish before deleting their objects.
  available_at timestamptz not null default now() + interval '15 minutes',
  primary key (bucket, object_key)
);
create index photo_cleanup_ready on app_private.photo_cleanup (available_at);
alter table app_private.photo_cleanup enable row level security;
alter table public.photo_submissions enable row level security;

revoke all on public.photo_submissions from public, anon, authenticated, service_role;
grant select (id, owner_id, target_type, target_id, status, rejection_reason, created_at, reviewed_at)
  on public.photo_submissions to authenticated;
create policy photo_submissions_read on public.photo_submissions for select to authenticated
  using (owner_id = (select auth.uid()) or
    (select exists (select 1 from public.profiles where id = (select auth.uid()) and role = 'moderator')));

create function app_private.photo_is_public(p_id uuid) returns boolean
language sql stable security definer set search_path = ''
as $$
  select exists (
    select 1 from public.photo_submissions f
    where f.id = p_id and f.status = 'approved' and (
      f.proposal_id is null or exists (
        select 1 from public.spot_proposals p
        join public.spots s on s.id = 'community:' || p.id::text
        where p.id = f.proposal_id and p.status = 'approved' and s.source = 'community'
      )
    )
  )
$$;
revoke all on function app_private.photo_is_public(uuid) from public;
grant execute on function app_private.photo_is_public(uuid) to anon, authenticated;
revoke all on public.spot_photos, public.review_photos, public.proposal_photos from public, anon, authenticated;
grant select (id, spot_id, created_at) on public.spot_photos to anon, authenticated;
grant select (id, review_id, created_at) on public.review_photos to anon, authenticated;
grant select (id, proposal_id, created_at) on public.proposal_photos to anon, authenticated;
create policy spot_photos_approved on public.spot_photos for select to anon, authenticated
  using (app_private.photo_is_public(id));
create policy review_photos_approved on public.review_photos for select to anon, authenticated
  using (app_private.photo_is_public(id));
create policy proposal_photos_approved on public.proposal_photos for select to anon, authenticated
  using (app_private.photo_is_public(id));

create function app_private.photo_enqueue_cleanup() returns trigger
language plpgsql security definer set search_path = ''
as $$
begin
  if tg_op = 'DELETE' or new.status = 'rejected' then
    delete from public.spot_photos where id = old.id and storage_path = old.object_key;
    delete from public.review_photos where id = old.id and storage_path = old.object_key;
    delete from public.proposal_photos where id = old.id and storage_path = old.object_key;
    insert into app_private.photo_cleanup(bucket, object_key)
      values ('sportspot-photo-pending', old.object_key), ('sportspot-photo-approved', old.object_key)
      on conflict (bucket, object_key) do update set available_at = excluded.available_at;
  elsif new.status = 'approved' and old.status <> 'approved' then
    insert into app_private.photo_cleanup(bucket, object_key)
      values ('sportspot-photo-pending', new.object_key) on conflict do nothing;
  end if;
  return null;
end
$$;
create trigger photo_enqueue_cleanup after delete or update of status on public.photo_submissions
  for each row execute function app_private.photo_enqueue_cleanup();

create function app_private.photo_parent_change() returns trigger
language plpgsql security definer set search_path = ''
as $$
begin
  if tg_table_name = 'spots' then
    delete from public.photo_submissions
      where (target_type = 'spot' and target_id = old.id)
        or (proposal_id is not null and 'community:' || proposal_id::text = old.id);
  elsif new.status = 'rejected' and old.status <> 'rejected' then
    update public.photo_submissions set status = 'rejected', rejection_reason = 'Предложение отклонено',
      moderator_id = new.moderator_id, reviewed_at = now()
      where proposal_id = new.id and status <> 'rejected';
  end if;
  return null;
end
$$;
create trigger photos_spot_deleted after delete on public.spots
  for each row execute function app_private.photo_parent_change();
create trigger photos_proposal_rejected after update of status on public.spot_proposals
  for each row execute function app_private.photo_parent_change();

-- The Edge Function verifies the caller with Auth before passing p_actor.
-- All actions below are callable only by the trusted server role.
create function public.photo_command(
  p_action text, p_actor uuid default null, p_id uuid default null,
  p_target_type text default null, p_target_id text default null,
  p_hash text default null, p_reason text default null
) returns jsonb
language plpgsql security definer set search_path = ''
as $$
declare
  v public.photo_submissions%rowtype;
  v_mod boolean;
  v_owned boolean;
  v_limit integer;
  v_rows jsonb;
begin
  if p_action is null then raise exception using errcode = '22023', message = 'INVALID_ACTION'; end if;
  if p_action = 'gallery' then
    select coalesce(jsonb_agg(x.id order by x.created_at desc, x.id), '[]'::jsonb)
    into v_rows from (
      select f.id, f.created_at from public.photo_submissions f
      where f.status = 'approved'
        and ((p_target_type in ('spot','review') and f.target_type = p_target_type and f.target_id = p_target_id)
          or (p_target_type = 'spot' and f.target_type = 'proposal' and f.target_id = substring(p_target_id from 11)
              and p_target_id like 'community:%'))
        and app_private.photo_is_public(f.id)
      order by f.created_at desc, f.id limit 100
    ) x;
    return v_rows;
  end if;

  if p_action = 'image' then
    select * into v from public.photo_submissions where id = p_id;
    if not found or not app_private.photo_is_public(p_id) then
      raise exception using errcode = 'P0002', message = 'PHOTO_NOT_FOUND';
    end if;
    return to_jsonb(v);
  end if;

  if p_actor is null then
    raise exception using errcode = '42501', message = 'FORBIDDEN';
  end if;
  select exists (select 1 from public.profiles where id = p_actor and role = 'moderator') into v_mod;

  if p_action in ('mine', 'queue', 'proposal_queue') then
    if p_action <> 'mine' and not v_mod then
      raise exception using errcode = '42501', message = 'FORBIDDEN';
    end if;
    select coalesce(jsonb_agg(to_jsonb(x)), '[]'::jsonb) into v_rows from (
      select * from public.photo_submissions
      where (p_action = 'mine' and owner_id = p_actor and target_type = p_target_type and target_id = p_target_id)
         or (p_action = 'queue' and status = 'pending')
         or (p_action = 'proposal_queue' and proposal_id::text = p_target_id and status in ('pending','approved'))
      order by created_at, id limit 100
    ) x;
    return v_rows;
  end if;

  if p_action = 'reserve' then
    if p_id is null or p_target_type is null or p_target_id is null or p_hash is null or p_hash !~ '^[0-9a-f]{64}$' then
      raise exception using errcode = '22023', message = 'INVALID_TARGET';
    end if;
    perform pg_advisory_xact_lock(hashtextextended(p_actor::text, 710241));
    select * into v from public.photo_submissions where id = p_id;
    if found then
      if v.owner_id <> p_actor or v.target_type <> p_target_type or v.target_id <> p_target_id
         or v.content_hash <> p_hash or v.status = 'rejected' then
        raise exception using errcode = 'P0001', message = 'UPLOAD_CONFLICT';
      end if;
      return to_jsonb(v);
    end if;
    case p_target_type
      when 'spot' then
        v_limit := 5;
        v_owned := app_private.photo_canonical_spot(p_target_id) and
          (p_target_id like 'osm:%' or exists (
            select 1 from public.spots where id = p_target_id and source = 'community' for key share
          ));
      when 'review' then
        v_limit := 3;
        v_owned := exists (select 1 from public.reviews where id = p_target_id::uuid and user_id = p_actor for key share);
      when 'proposal' then
        v_limit := 3;
        v_owned := exists (
          select 1 from public.spot_proposals
          where id = p_target_id::uuid and user_id = p_actor and status <> 'rejected' for update
        );
      else raise exception using errcode = '22023', message = 'INVALID_TARGET';
    end case;
    if not v_owned then raise exception using errcode = '42501', message = 'TARGET_FORBIDDEN'; end if;
    if (select count(*) from public.photo_submissions
        where owner_id = p_actor and target_type = p_target_type and target_id = p_target_id and status <> 'rejected') >= v_limit then
      raise exception using errcode = 'P0001', message = 'PHOTO_LIMIT';
    end if;
    insert into public.photo_submissions(id, owner_id, target_type, target_id, review_id, proposal_id, object_key, content_hash)
      values (p_id, p_actor, p_target_type, p_target_id,
        case when p_target_type = 'review' then p_target_id::uuid end,
        case when p_target_type = 'proposal' then p_target_id::uuid end,
        p_actor::text || '/' || gen_random_uuid()::text || '.jpg', p_hash)
      on conflict (id) do nothing returning * into v;
    if not found then raise exception using errcode = 'P0001', message = 'UPLOAD_CONFLICT'; end if;
    return to_jsonb(v);
  end if;

  select * into v from public.photo_submissions where id = p_id for update;
  if not found then raise exception using errcode = 'P0002', message = 'PHOTO_NOT_FOUND'; end if;
  if p_action = 'preview' then
    if (v.owner_id <> p_actor and not v_mod) or v.status not in ('pending', 'approved') then
      raise exception using errcode = '42501', message = 'FORBIDDEN';
    end if;
    return to_jsonb(v);
  end if;
  if p_action in ('submit','remove') then
    if v.owner_id <> p_actor then raise exception using errcode = '42501', message = 'FORBIDDEN'; end if;
    if p_action = 'remove' then
      delete from public.photo_submissions where id = v.id;
      return 'null'::jsonb;
    end if;
    if v.status = 'uploading' then
      update public.photo_submissions set status = 'pending' where id = v.id returning * into v;
    end if;
    if v.status not in ('pending', 'approved') then raise exception using errcode = 'P0001', message = 'UPLOAD_CONFLICT'; end if;
    return to_jsonb(v);
  end if;
  if p_action not in ('moderate','approve','reject') or not v_mod then
    raise exception using errcode = '42501', message = 'FORBIDDEN';
  end if;
  if v.status = 'approved' and p_action in ('moderate','approve') or v.status = 'rejected' and p_action = 'reject' then
    return to_jsonb(v);
  end if;
  if v.status <> 'pending' then raise exception using errcode = 'P0001', message = 'PHOTO_CONFLICT'; end if;
  if p_action = 'moderate' then return to_jsonb(v); end if;
  if p_action = 'reject' and (p_reason is null or length(btrim(p_reason)) not between 1 and 500) then
    raise exception using errcode = '22023', message = 'REASON_REQUIRED';
  end if;
  if p_action = 'approve' then
    case v.target_type
      when 'spot' then insert into public.spot_photos(id, spot_id, storage_path, uploaded_by)
        values (v.id, v.target_id, v.object_key, v.owner_id);
      when 'review' then insert into public.review_photos(id, review_id, storage_path)
        values (v.id, v.review_id, v.object_key);
      when 'proposal' then insert into public.proposal_photos(id, proposal_id, storage_path)
        values (v.id, v.proposal_id, v.object_key);
    end case;
  end if;
  update public.photo_submissions
  set status = case when p_action = 'approve' then 'approved' else 'rejected' end,
      rejection_reason = case when p_action = 'reject' then btrim(p_reason) end,
      moderator_id = p_actor, reviewed_at = now()
  where id = v.id returning * into v;
  return to_jsonb(v);
end
$$;

create function public.photo_cleanup_batch() returns jsonb
language plpgsql security definer set search_path = ''
as $$
begin
  delete from public.photo_submissions where id in (
    select id from public.photo_submissions where status = 'uploading' and created_at < now() - interval '1 hour'
    order by created_at limit 50 for update skip locked
  );
  return (select coalesce(jsonb_agg(to_jsonb(x)), '[]'::jsonb) from (
    select bucket, object_key from app_private.photo_cleanup where available_at <= now() order by available_at limit 50
  ) x);
end
$$;

create function public.photo_cleanup_ack(p_bucket text, p_keys text[]) returns void
language sql security definer set search_path = ''
as $$
  delete from app_private.photo_cleanup
  where bucket = p_bucket and object_key = any(p_keys) and available_at <= now()
$$;

revoke all on function public.photo_command(text,uuid,uuid,text,text,text,text),
  public.photo_cleanup_batch(), public.photo_cleanup_ack(text,text[]) from public, anon, authenticated;
grant execute on function public.photo_command(text,uuid,uuid,text,text,text,text),
  public.photo_cleanup_batch(), public.photo_cleanup_ack(text,text[]) to service_role;
revoke all on function app_private.photo_enqueue_cleanup(), app_private.photo_parent_change() from public, anon, authenticated;
notify pgrst, 'reload schema';
commit;
