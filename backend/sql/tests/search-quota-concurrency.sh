#!/usr/bin/env bash
set -euo pipefail

container="${1:?Pass the disposable postgres service container ID}"
database=sportspot_search_test

sql() {
  docker exec -i "$container" psql -XqAt -U postgres -d "$database" -v ON_ERROR_STOP=1 "$@"
}

sql -c 'CREATE ROLE anon; CREATE ROLE authenticated; CREATE ROLE service_role BYPASSRLS;'
sql < backend/sql/10_overpass_integration.sql
sql < backend/supabase/migrations/20261128163136_search_cache_retention_and_quota.sql

submit_parallel() {
  local day_limit="$1"
  seq 20 | xargs -P 20 -I '{}' docker exec "$container" psql -XqAt -U postgres \
    -d "$database" -v ON_ERROR_STOP=1 \
    -c "/* Client {} */ SET ROLE service_role; SELECT public.spot_search_take_overpass_quota(5, $day_limit);"
}

wait_for_window() {
  # Leave time for all clients to start before the minute and UTC day end.
  sql -c "SELECT pg_sleep(CASE WHEN extract(second FROM clock_timestamp()) >= 50
    THEN (62 - extract(second FROM clock_timestamp()))::double precision ELSE 0 END)" >/dev/null
}

# The daily limit is lower than the minute limit. Failed reservations must not charge either counter.
wait_for_window
results="$(submit_parallel 3)"
[[ "$(grep -cx '0' <<< "$results")" == 3 ]]
[[ "$(grep -cEx '[1-9][0-9]*' <<< "$results")" == 17 ]]
[[ "$(sql -c "SELECT sum(hits) FROM app_private.spot_search_quota WHERE scope = 'overpass-outbound'")" == 3 ]]
[[ "$(sql -c "SELECT sum(hits) FROM app_private.spot_search_quota WHERE scope = 'overpass-outbound-daily'")" == 3 ]]

sql -c 'DELETE FROM app_private.spot_search_quota;'
wait_for_window
results="$(submit_parallel 500)"
admitted="$(grep -cx '0' <<< "$results")"
[[ "$(wc -l <<< "$results")" == 20 ]]
[[ "$(sql -c "SELECT sum(hits) FROM app_private.spot_search_quota WHERE scope = 'overpass-outbound-daily'")" == "$admitted" ]]
[[ "$(sql -c "SELECT sum(hits) FROM app_private.spot_search_quota WHERE scope = 'overpass-outbound'")" == "$admitted" ]]
[[ "$(sql -c "SELECT max(hits) FROM app_private.spot_search_quota WHERE scope = 'overpass-outbound'")" == 5 ]]
echo "PASS concurrent quotas in PostgreSQL: 20 connections, day admitted 3, minute admitted $admitted"
