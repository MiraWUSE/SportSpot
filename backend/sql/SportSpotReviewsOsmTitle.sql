-- Run as the migration owner, after the base schema. Contains no data deletion.
begin;
set local lock_timeout = '5s';

alter table public.reviews
  add column if not exists title text,
  drop constraint if exists reviews_spot_id_fkey;

do $$
begin
  if not exists (
    select 1 from pg_constraint
    where conrelid = 'public.reviews'::regclass and conname = 'reviews_canonical_spot_id'
  ) then
    alter table public.reviews add constraint reviews_canonical_spot_id check (
      case
        when spot_id ~ '^osm:(node|way|relation):(0|-?[1-9][0-9]{0,18})$'
        then split_part(spot_id, ':', 3)::numeric between -9223372036854775808 and 9223372036854775807
        else spot_id ~ '^community:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
      end
    ) not valid;
  end if;
  if not exists (
    select 1 from pg_constraint
    where conrelid = 'public.reviews'::regclass and conname = 'reviews_title_length'
  ) then
    alter table public.reviews add constraint reviews_title_length
      check (title is null or char_length(btrim(title)) between 1 and 120) not valid;
  end if;
end;
$$;

create index if not exists reviews_spot_created_id
  on public.reviews (spot_id, created_at desc, id);
grant insert (title), update (title) on public.reviews to authenticated;
notify pgrst, 'reload schema';
commit;
