BEGIN;

-- Служебные таблицы недоступны напрямую через Data API.
CREATE SCHEMA IF NOT EXISTS app_private;
REVOKE ALL ON SCHEMA app_private FROM PUBLIC, anon, authenticated;
GRANT USAGE ON SCHEMA app_private TO service_role;

-- Внутри каждого временного интервала храним число запросов.
CREATE TABLE IF NOT EXISTS app_private.spot_search_quota (
    scope text NOT NULL,
    bucket timestamptz NOT NULL,
    hits integer NOT NULL CHECK (hits > 0),
    PRIMARY KEY (scope, bucket)
);

-- Кеш результата поиска: действует один час.
CREATE TABLE IF NOT EXISTS app_private.spot_search_cache (
    cache_key text PRIMARY KEY,
    payload jsonb NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL
);

ALTER TABLE app_private.spot_search_quota ENABLE ROW LEVEL SECURITY;
ALTER TABLE app_private.spot_search_cache ENABLE ROW LEVEL SECURITY;

-- Клиентским ролям доступ запрещён. Edge Function использует service_role.
REVOKE ALL ON app_private.spot_search_quota,
              app_private.spot_search_cache
  FROM PUBLIC, anon, authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE
  ON app_private.spot_search_quota, app_private.spot_search_cache
  TO service_role;

-- Атомарная проверка лимита при конкурентных запросах.
-- Возможные периоды: 'minute' и 'day'. Возвращает false при превышении.
CREATE OR REPLACE FUNCTION public.spot_search_take_quota(
    p_scope text,
    p_period text,
    p_limit integer
)
RETURNS boolean
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = ''
AS $function$
DECLARE
    v_bucket timestamptz;
    v_hits integer;
BEGIN
    IF p_scope IS NULL OR length(p_scope) > 150 OR length(p_scope) < 1
       OR p_limit IS NULL OR p_limit < 1 OR p_limit > 10000 THEN
        RETURN false;
    END IF;

    IF p_period = 'minute' THEN
        v_bucket := date_trunc('minute', now());
    ELSIF p_period = 'day' THEN
        v_bucket := date_trunc('day', now());
    ELSE
        RETURN false;
    END IF;

    INSERT INTO app_private.spot_search_quota AS q (scope, bucket, hits)
    VALUES (p_scope, v_bucket, 1)
    ON CONFLICT (scope, bucket) DO UPDATE
       SET hits = q.hits + 1
       WHERE q.hits < p_limit
    RETURNING hits INTO v_hits;

    RETURN v_hits IS NOT NULL;
END;
$function$;

-- Возвращает JSON из кеша, если срок действия ещё не истёк.
CREATE OR REPLACE FUNCTION public.spot_search_cache_read(p_key text)
RETURNS jsonb
LANGUAGE sql
SECURITY INVOKER
SET search_path = ''
AS $function$
    SELECT payload
    FROM app_private.spot_search_cache
    WHERE cache_key = p_key AND expires_at > now()
    LIMIT 1;
$function$;

-- Сохраняет или обновляет кеш поиска; TTL = 1 час.
CREATE OR REPLACE FUNCTION public.spot_search_cache_write(
    p_key text,
    p_payload jsonb
)
RETURNS void
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = ''
AS $function$
BEGIN
    IF p_key IS NULL OR length(p_key) > 200 OR p_payload IS NULL THEN
        RAISE EXCEPTION 'invalid cache input';
    END IF;

    INSERT INTO app_private.spot_search_cache AS c
        (cache_key, payload, updated_at, expires_at)
    VALUES (p_key, p_payload, now(), now() + interval '1 hour')
    ON CONFLICT (cache_key) DO UPDATE SET
        payload = excluded.payload,
        updated_at = excluded.updated_at,
        expires_at = excluded.expires_at;
END;
$function$;

-- RPC доступны только серверной роли. Нельзя вызывать от anon/authenticated.
REVOKE ALL ON FUNCTION public.spot_search_take_quota(text, text, integer),
                       public.spot_search_cache_read(text),
                       public.spot_search_cache_write(text, jsonb)
  FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.spot_search_take_quota(text, text, integer),
                          public.spot_search_cache_read(text),
                          public.spot_search_cache_write(text, jsonb)
  TO service_role;

COMMIT;

-- Фактические лимиты указаны в Edge Function search-sports-spots-v1:
-- 60 входящих запросов в минуту (общий лимит),
-- 5 запросов к Overpass в минуту и 500 в день (общие лимиты).
-- Таблицы содержат временные записи; очистку устаревших записей
-- при необходимости можно настроить отдельным плановым заданием.
