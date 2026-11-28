BEGIN;

SET LOCAL lock_timeout = '2s';
SET LOCAL statement_timeout = '30s';

CREATE INDEX IF NOT EXISTS spot_search_cache_expires_at_idx
    ON app_private.spot_search_cache (expires_at);
CREATE INDEX IF NOT EXISTS spot_search_quota_bucket_idx
    ON app_private.spot_search_quota (bucket);

-- Run with the database owner's permissions. Each call takes at most one batch per table.
CREATE OR REPLACE FUNCTION app_private.spot_search_cleanup(p_batch_size integer DEFAULT 1000)
RETURNS TABLE(cache_deleted integer, quota_deleted integer)
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = ''
AS $function$
BEGIN
    IF p_batch_size IS NULL OR p_batch_size NOT BETWEEN 1 AND 1000 THEN
        RAISE EXCEPTION 'invalid search cleanup batch size' USING ERRCODE = '22023';
    END IF;

    WITH expired AS (
        SELECT cache_key FROM app_private.spot_search_cache
        WHERE expires_at <= now()
        ORDER BY expires_at
        LIMIT p_batch_size FOR UPDATE SKIP LOCKED
    )
    DELETE FROM app_private.spot_search_cache AS cache
    USING expired WHERE cache.cache_key = expired.cache_key;
    GET DIAGNOSTICS cache_deleted = ROW_COUNT;

    WITH expired AS (
        SELECT scope, bucket FROM app_private.spot_search_quota
        WHERE bucket < now() - interval '8 days'
           OR (
               bucket < now() - interval '2 days'
               AND scope IN ('search-global', 'search-cache-hit', 'search-cache-miss', 'overpass-outbound')
           )
        ORDER BY bucket
        LIMIT p_batch_size FOR UPDATE SKIP LOCKED
    )
    DELETE FROM app_private.spot_search_quota AS quota
    USING expired WHERE quota.scope = expired.scope AND quota.bucket = expired.bucket;
    GET DIAGNOSTICS quota_deleted = ROW_COUNT;

    RETURN NEXT;
END;
$function$;

-- Warm reads have their own guard and use no miss or Overpass budget.
CREATE OR REPLACE FUNCTION public.spot_search_lookup(p_key text)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = ''
AS $function$
DECLARE
    v_payload jsonb;
    v_cache text;
    v_limit integer;
    v_retry_after integer := 0;
BEGIN
    IF p_key IS NULL OR length(p_key) NOT BETWEEN 1 AND 200 THEN
        RAISE EXCEPTION 'invalid search cache key' USING ERRCODE = '22023';
    END IF;

    v_payload := public.spot_search_cache_read(p_key);
    IF v_payload IS NULL THEN
        v_cache := 'miss';
        v_limit := 60;
    ELSE
        v_cache := 'hit';
        v_limit := 300;
    END IF;
    IF NOT public.spot_search_take_quota('search-cache-' || v_cache, 'minute', v_limit) THEN
        v_retry_after := greatest(1, ceil(extract(epoch FROM (date_trunc('minute', now()) + interval '1 minute' - now())))::integer);
        v_payload := NULL;
    END IF;

    RETURN jsonb_build_object('cache', v_cache, 'payload', v_payload, 'retry_after', v_retry_after);
END;
$function$;

-- Return 0 on admission or seconds to the relevant UTC window reset.
CREATE OR REPLACE FUNCTION public.spot_search_take_overpass_quota(p_minute_limit integer, p_day_limit integer)
RETURNS integer
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = ''
SET timezone = 'UTC'
AS $function$
BEGIN
    IF p_minute_limit IS NULL OR p_minute_limit NOT BETWEEN 1 AND 5
       OR p_day_limit IS NULL OR p_day_limit NOT BETWEEN 1 AND 500 THEN
        RAISE EXCEPTION 'invalid Overpass quota' USING ERRCODE = '22023';
    END IF;

    IF NOT public.spot_search_take_quota('overpass-outbound', 'minute', p_minute_limit) THEN
        RETURN greatest(1, ceil(extract(epoch FROM (date_trunc('minute', now()) + interval '1 minute' - now())))::integer);
    END IF;
    IF NOT public.spot_search_take_quota('overpass-outbound-daily', 'day', p_day_limit) THEN
        -- The exception block rolls back the minute increment before returning the day delay.
        RAISE SQLSTATE 'SP001';
    END IF;
    RETURN 0;
EXCEPTION WHEN SQLSTATE 'SP001' THEN
    RETURN greatest(1, ceil(extract(epoch FROM (date_trunc('day', now()) + interval '1 day' - now())))::integer);
END;
$function$;

REVOKE ALL ON FUNCTION app_private.spot_search_cleanup(integer)
    FROM PUBLIC, anon, authenticated, service_role;
REVOKE ALL ON FUNCTION public.spot_search_lookup(text),
                       public.spot_search_take_overpass_quota(integer, integer)
    FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.spot_search_lookup(text),
                          public.spot_search_take_overpass_quota(integer, integer)
    TO service_role;

NOTIFY pgrst, 'reload schema';
COMMIT;
