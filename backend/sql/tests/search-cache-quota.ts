import { PGlite } from "npm:@electric-sql/pglite@0.3.15";
import { deepEqual, equal, rejects } from "node:assert/strict";

const bootstrap = await Deno.readTextFile(
  new URL("../10_overpass_integration.sql", import.meta.url),
);
const migration = await Deno.readTextFile(
  new URL(
    "../../supabase/migrations/20261128163136_search_cache_retention_and_quota.sql",
    import.meta.url,
  ),
);

const pg = new PGlite();
try {
  await pg.exec(
    "create role anon; create role authenticated; create role service_role bypassrls",
  );
  await pg.exec(bootstrap);
  await pg.exec(
    "create table public.spots (id text primary key); insert into public.spots values ('independent-fixture')",
  );
  await pg.exec(
    "insert into app_private.spot_search_cache (cache_key, payload, expires_at) values " +
      "('legacy-warm', '{\"spots\":[]}', now() + interval '30 minutes'), " +
      "('legacy-expired', '{\"spots\":[]}', now() - interval '2 days'), " +
      "('recently-expired', '{\"spots\":[]}', now() - interval '1 second')",
  );
  await pg.exec(
    "insert into app_private.spot_search_quota values " +
      "('search-global', now() - interval '3 days', 1)," +
      "('search-cache-hit', now() - interval '3 days', 1)," +
      "('overpass-outbound', now() - interval '1 day', 1)," +
      "('overpass-outbound-daily', now() - interval '9 days', 2)," +
      "('overpass-outbound-daily', now() - interval '4 days', 2)",
  );
  await pg.exec(migration);
  await pg.exec(migration);
  equal(
    (await pg.query<{ total: number }>(
      "select count(*)::int as total from app_private.spot_search_cache",
    ))
      .rows[0].total,
    3,
  );
  const expired = await pg.query<{ payload: unknown }>(
    "select public.spot_search_cache_read('recently-expired') as payload",
  );
  equal(expired.rows[0].payload, null);

  const cleanup = (batch: number) =>
    pg.query("select * from app_private.spot_search_cleanup($1)", [batch]).then(
      (result) => result.rows[0],
    );
  deepEqual(await cleanup(1), { cache_deleted: 1, quota_deleted: 1 });
  deepEqual(await cleanup(1000), { cache_deleted: 1, quota_deleted: 2 });
  deepEqual(await cleanup(1000), { cache_deleted: 0, quota_deleted: 0 });
  await rejects(cleanup(0), /invalid search cleanup batch size/);
  await rejects(cleanup(1001), /invalid search cleanup batch size/);
  deepEqual(
    (await pg.query("select cache_key from app_private.spot_search_cache"))
      .rows,
    [{ cache_key: "legacy-warm" }],
  );
  deepEqual(
    (await pg.query(
      "select scope from app_private.spot_search_quota order by scope",
    )).rows,
    [{ scope: "overpass-outbound" }, { scope: "overpass-outbound-daily" }],
  );
  equal(
    (await pg.query<{ id: string }>("select id from public.spots")).rows[0].id,
    "independent-fixture",
  );

  for (const role of ["anon", "authenticated"]) {
    await pg.exec("set role " + role);
    await rejects(
      pg.query("select public.spot_search_lookup('legacy-warm')"),
      /permission denied/,
    );
    await rejects(
      pg.query("select public.spot_search_take_overpass_quota(1, 1)"),
      /permission denied/,
    );
    await rejects(
      pg.query("select * from app_private.spot_search_cache"),
      /permission denied/,
    );
    await rejects(
      pg.query("select * from app_private.spot_search_quota"),
      /permission denied/,
    );
    await rejects(cleanup(1), /permission denied/);
    await pg.exec("reset role");
  }

  await pg.exec("set role service_role");
  await rejects(cleanup(1), /permission denied/);
  await pg.exec("reset role");

  await pg.exec(
    "begin; set local role service_role; set local timezone = 'UTC'",
  );
  await pg.query(
    "select public.spot_search_cache_write('fresh-empty', '{\"spots\":[],\"count\":0}')",
  );
  equal(
    (await pg.query<{ correct: boolean }>(
      "select (expires_at = now() + interval '1 hour') as correct from app_private.spot_search_cache where cache_key = 'fresh-empty'",
    )).rows[0].correct,
    true,
  );
  const lookup = async (key: string) =>
    (await pg.query<
      { result: { cache: string; retry_after: number; payload: unknown } }
    >(
      "select public.spot_search_lookup($1) as result",
      [key],
    )).rows[0].result;
  deepEqual(await lookup("fresh-empty"), {
    cache: "hit",
    retry_after: 0,
    payload: { spots: [], count: 0 },
  });
  await pg.exec(
    "insert into app_private.spot_search_quota values ('search-cache-miss', date_trunc('minute', now()), 60)",
  );
  const miss = await lookup("not-present");
  equal(miss.cache, "miss");
  equal(miss.payload, null);
  equal(miss.retry_after >= 1 && miss.retry_after <= 60, true);
  equal((await lookup("legacy-warm")).retry_after, 0);
  await pg.exec(
    "update app_private.spot_search_quota set hits = 300 where scope = 'search-cache-hit'",
  );
  const warmLimited = await lookup("legacy-warm");
  equal(warmLimited.cache, "hit");
  equal(warmLimited.payload, null);
  equal(warmLimited.retry_after >= 1 && warmLimited.retry_after <= 60, true);

  const reserve = async (minute: number, day: number) =>
    (await pg.query<{ delay: number }>(
      "select public.spot_search_take_overpass_quota($1, $2) as delay",
      [minute, day],
    )).rows[0].delay;
  const count = async (scope: string, window: "minute" | "day") =>
    (await pg.query<{ hits: number }>(
      "select hits from app_private.spot_search_quota where scope = $1 and bucket = date_trunc($2, now())",
      [scope, window],
    )).rows[0]?.hits ?? 0;

  // Fully spent past windows do not contribute to the current minute or UTC day.
  await pg.exec(
    "insert into app_private.spot_search_quota values " +
      "('overpass-outbound', date_trunc('minute', now()) - interval '1 minute', 5)," +
      "('overpass-outbound-daily', date_trunc('day', now()) - interval '1 day', 500)",
  );
  equal(await reserve(5, 1), 0);
  const dayDelay = await reserve(5, 1);
  equal(dayDelay >= 1 && dayDelay <= 86400, true);
  equal(await count("overpass-outbound", "minute"), 1);
  equal(await count("overpass-outbound-daily", "day"), 1);

  const attempts = await Promise.all(
    Array.from({ length: 20 }, () => reserve(5, 500)),
  );
  equal(attempts.filter((delay) => delay === 0).length, 4);
  equal(attempts.filter((delay) => delay >= 1 && delay <= 60).length, 16);
  equal(await count("overpass-outbound", "minute"), 5);
  equal(await count("overpass-outbound-daily", "day"), 5);
  await pg.exec("rollback");

  await rejects(reserve(6, 500), /invalid Overpass quota/);
  await rejects(reserve(1, 501), /invalid Overpass quota/);
  await rejects(reserve(0, 1), /invalid Overpass quota/);

  // The same setup sequence also applies to a new database with no search rows.
  const fresh = new PGlite();
  try {
    await fresh.exec(
      "create role anon; create role authenticated; create role service_role bypassrls",
    );
    await fresh.exec(bootstrap);
    await fresh.exec(migration);
    deepEqual(
      (await fresh.query("select * from app_private.spot_search_cleanup()"))
        .rows,
      [{ cache_deleted: 0, quota_deleted: 0 }],
    );
  } finally {
    await fresh.close();
  }
  console.log(
    "PASS isolated search cache retention, grants, windows and quota rollback",
  );
} finally {
  await pg.close();
}
