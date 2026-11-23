-- SportSpot favorites snapshots. Apply once to the existing schema after review.
-- No changes to auth users, public.spots, or the existing ownership policies.
BEGIN;
SET LOCAL lock_timeout = '5s';

ALTER TABLE public.favorites ADD COLUMN spot_snapshot jsonb;

CREATE FUNCTION app_private.sportspot_favorite_snapshot_valid(spot_id text, snapshot jsonb)
RETURNS boolean
LANGUAGE plpgsql
IMMUTABLE
SECURITY INVOKER
SET search_path = ''
AS $validation$
DECLARE
  field_name text;
  photo jsonb;
BEGIN
  IF spot_id ~ '^osm:(node|way|relation):(0|-?[1-9][0-9]{0,18})$' THEN
    IF split_part(spot_id, ':', 3)::numeric NOT BETWEEN -9223372036854775808 AND 9223372036854775807 THEN
      RETURN false;
    END IF;
  ELSIF spot_id IS NULL OR spot_id !~ '^community:[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$' THEN
    RETURN false;
  END IF;

  IF jsonb_typeof(snapshot) IS DISTINCT FROM 'object'
     OR octet_length(snapshot::text) > 12288
     OR snapshot - ARRAY['version','id','name','sport_type','latitude','longitude','address','surface','lighting','equipment','photo'] <> '{}'::jsonb
     OR snapshot->'version' IS DISTINCT FROM '1'::jsonb
     OR snapshot->'id' IS DISTINCT FROM to_jsonb(spot_id)
     OR jsonb_typeof(snapshot->'name') IS DISTINCT FROM 'string'
     OR char_length(btrim(snapshot->>'name')) NOT BETWEEN 1 AND 250
     OR char_length(snapshot->>'name') > 250
     OR jsonb_typeof(snapshot->'sport_type') IS DISTINCT FROM 'string'
     OR char_length(btrim(snapshot->>'sport_type')) NOT BETWEEN 1 AND 519
     OR char_length(snapshot->>'sport_type') > 519
     OR jsonb_typeof(snapshot->'latitude') IS DISTINCT FROM 'number'
     OR jsonb_typeof(snapshot->'longitude') IS DISTINCT FROM 'number' THEN
    RETURN false;
  END IF;

  IF (snapshot->>'latitude')::numeric NOT BETWEEN -90 AND 90
     OR (snapshot->>'longitude')::numeric NOT BETWEEN -180 AND 180
     OR jsonb_typeof(snapshot->'lighting') NOT IN ('boolean', 'null') THEN
    RETURN false;
  END IF;

  FOREACH field_name IN ARRAY ARRAY['address','surface','equipment'] LOOP
    IF jsonb_typeof(snapshot->field_name) NOT IN ('string', 'null')
       OR char_length(snapshot->>field_name) >
          (CASE WHEN field_name = 'address' THEN 502 ELSE 250 END) THEN
      RETURN false;
    END IF;
  END LOOP;

  photo := snapshot->'photo';
  IF photo IS NULL OR photo = 'null'::jsonb THEN RETURN true; END IF;
  IF jsonb_typeof(photo) <> 'object'
     OR photo - ARRAY['url','source_url','author','license','license_url'] <> '{}'::jsonb THEN
    RETURN false;
  END IF;
  FOREACH field_name IN ARRAY ARRAY['url','source_url','author','license','license_url'] LOOP
    IF jsonb_typeof(photo->field_name) IS DISTINCT FROM 'string'
       OR char_length(photo->>field_name) NOT BETWEEN 1 AND 2048 THEN
      RETURN false;
    END IF;
  END LOOP;

  RETURN photo->>'url' ~ '^https://(thumb|upload)\.wikimedia\.org/wikipedia/commons/thumb/[^[:space:]?#]+$'
     AND photo->>'source_url' ~ '^https://commons\.wikimedia\.org/wiki/File:[^[:space:]?#]+$'
     AND ((photo->>'url') || '/' || (photo->>'source_url')) !~* '(/\.{1,2}(/|$)|%2e|%2f|%5c|[[:cntrl:]])'
     AND strpos((photo->>'url') || (photo->>'source_url'), chr(92)) = 0
     AND char_length(btrim(photo->>'author')) BETWEEN 1 AND 300
     AND photo->>'author' !~ '([<>[:cntrl:]]|://)'
     AND (photo->>'license', photo->>'license_url') IN (
       ('CC BY 4.0', 'https://creativecommons.org/licenses/by/4.0/'),
       ('CC BY-SA 4.0', 'https://creativecommons.org/licenses/by-sa/4.0/')
     );
END;
$validation$;

REVOKE ALL ON FUNCTION app_private.sportspot_favorite_snapshot_valid(text, jsonb) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION app_private.sportspot_favorite_snapshot_valid(text, jsonb) TO authenticated;

-- Keep every legacy row. Backfill snapshots from its original catalog row when valid.
UPDATE public.favorites AS favorite
SET spot_snapshot = existing.snapshot
FROM (
  SELECT id, jsonb_strip_nulls(jsonb_build_object(
    'version', 1, 'id', id, 'name', left(btrim(name), 250), 'sport_type', left(btrim(sport_type), 519),
    'latitude', latitude, 'longitude', longitude,
    'address', left(address, 502), 'surface', left(surface, 250),
    'lighting', lighting, 'equipment', left(equipment, 250)
  )) AS snapshot
  FROM public.spots
) AS existing
WHERE favorite.spot_id = existing.id
  AND app_private.sportspot_favorite_snapshot_valid(favorite.spot_id, existing.snapshot);

-- The check applies to all new writes, even if a historical row has no valid snapshot.
ALTER TABLE public.favorites
  ADD CONSTRAINT favorites_spot_snapshot_valid CHECK (
    spot_snapshot IS NOT NULL AND app_private.sportspot_favorite_snapshot_valid(spot_id, spot_snapshot)
  ) NOT VALID,
  DROP CONSTRAINT favorites_spot_id_fkey;

REVOKE ALL ON TABLE public.favorites FROM PUBLIC, anon;
REVOKE ALL (user_id, spot_id, created_at, spot_snapshot) ON TABLE public.favorites FROM PUBLIC, anon;
REVOKE INSERT, UPDATE ON TABLE public.favorites FROM authenticated;
REVOKE INSERT (user_id, created_at), UPDATE (user_id, spot_id, created_at, spot_snapshot)
  ON TABLE public.favorites FROM authenticated;
GRANT INSERT (spot_id, spot_snapshot) ON TABLE public.favorites TO authenticated;

NOTIFY pgrst, 'reload schema';
COMMIT;

-- Verification: inspect favorites_spot_snapshot_valid and owner SELECT/INSERT/DELETE policies.
-- Before rollback, count rows whose spot_id has no matching public.spots row.
-- Restoring the FK with such rows would fail. Export and keep those rows and their
-- snapshots. Restore the FK only once every retained spot_id can be referenced.
-- Keep the spot_snapshot column when rolling back the client to preserve information.
