import { PGlite } from "npm:@electric-sql/pglite@0.3.15";
import { equal, rejects } from "node:assert/strict";

const pg = new PGlite();
const alice = "11111111-1111-4111-8111-111111111111";
const bob = "22222222-2222-4222-8222-222222222222";
const community = "community:33333333-3333-4333-8333-333333333333";
const osm = "osm:way:42";

function snapshot(id: string, extra: Record<string, unknown> = {}) {
  return JSON.stringify({
    version: 1,
    id,
    name: "Test Court",
    sport_type: "soccer;ice_hockey",
    latitude: 56.3,
    longitude: 44.0,
    lighting: false,
    ...extra,
  });
}

async function role(name: "anon" | "authenticated", uid = "") {
  await pg.exec("reset role");
  await pg.query("select set_config('request.jwt.claim.sub', $1, false)", [
    uid,
  ]);
  await pg.exec(`set role ${name}`);
}

try {
  await pg.exec(`
    create role anon;
    create role authenticated;
    create role service_role;
    create schema auth;
    create table auth.users (id uuid primary key, email text, raw_user_meta_data jsonb);
    create function auth.uid() returns uuid language sql stable as
      $$ select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid $$;
    grant usage on schema auth to anon, authenticated;
  `);
  await pg.exec(
    await Deno.readTextFile(new URL("../../setup.sql", import.meta.url)),
  );
  await pg.query(
    'insert into auth.users values ($1, \'a@example.test\', \'{"name":"Alice","role":"moderator"}\'), ($2, \'b@example.test\', null)',
    [alice, bob],
  );
  equal(
    (await pg.query("select role from public.profiles where id=$1", [alice]))
      .rows[0].role,
    "user",
  );
  await pg.query(
    "insert into public.spots (id,name,sport_type,latitude,longitude,source) values ($1,'Old Court','tennis',1,2,'community')",
    [community],
  );
  await role("authenticated", alice);
  await pg.query("insert into public.favorites (spot_id) values ($1)", [
    community,
  ]);
  const before =
    (await pg.query("select created_at from public.favorites")).rows[0]
      .created_at;
  await pg.exec("reset role");
  await pg.exec(
    await Deno.readTextFile(
      new URL("../SportSpotFavoritesSnapshot.sql", import.meta.url),
    ),
  );

  await role("authenticated", alice);
  const migrated =
    (await pg.query("select created_at,spot_snapshot from public.favorites"))
      .rows[0];
  equal(String(migrated.created_at), String(before));
  equal((migrated.spot_snapshot as { name: string }).name, "Old Court");

  const insert =
    "insert into public.favorites (spot_id, spot_snapshot) values ($1,$2)";
  await pg.query(insert, [osm, snapshot(osm)]);
  await pg.query(insert + " on conflict (user_id,spot_id) do nothing", [
    osm,
    snapshot(osm),
  ]);
  equal(
    (await pg.query(
      "select count(*)::int as n from public.favorites where spot_id=$1",
      [osm],
    )).rows[0].n,
    1,
  );
  equal(
    (await pg.query("select count(*)::int as n from public.spots where id=$1", [
      osm,
    ])).rows[0].n,
    0,
  );
  const validPhoto = {
    url:
      "https://thumb.wikimedia.org/wikipedia/commons/thumb/a/a1/Fixture.jpg/500px-Fixture.jpg",
    source_url: "https://commons.wikimedia.org/wiki/File:Fixture.jpg",
    author: "Fixture author",
    license: "CC BY-SA 4.0",
    license_url: "https://creativecommons.org/licenses/by-sa/4.0/",
  };
  const node = "osm:node:43";
  await pg.query(insert, [node, snapshot(node, { photo: validPhoto })]);
  const relation = "osm:relation:44";
  await pg.query(insert, [relation, snapshot(relation)]);
  for (
    const [id, data] of [
      ["osm:node:041", snapshot("osm:node:041")],
      [
        "osm:node:9223372036854775808",
        snapshot("osm:node:9223372036854775808"),
      ],
      ["osm:node:50", snapshot("osm:node:51")],
      ["osm:node:50", snapshot("osm:node:50", { name: "x".repeat(251) })],
      ["osm:node:50", snapshot("osm:node:50", { address: "x".repeat(13_000) })],
      ["osm:node:50", snapshot("osm:node:50", { longitude: 181 })],
      ["osm:node:50", snapshot("osm:node:50", { lighting: "false" })],
      [
        "osm:node:50",
        snapshot("osm:node:50", {
          photo: { ...validPhoto, url: "https://invalid.test/photo" },
        }),
      ],
      [
        "osm:node:50",
        snapshot("osm:node:50", {
          photo: { ...validPhoto, license_url: "https://invalid.test/license" },
        }),
      ],
      ["osm:node:50", null],
    ]
  ) {
    await rejects(pg.query(insert, [id, data]), { code: "23514" });
  }
  await role("authenticated", bob);
  equal(
    (await pg.query("select count(*)::int as n from public.favorites")).rows[0]
      .n,
    0,
  );
  equal(
    (await pg.query(
      "delete from public.favorites where spot_id=$1 returning spot_id",
      [osm],
    )).rows.length,
    0,
  );
  await rejects(
    pg.query(
      "insert into public.favorites (user_id,spot_id,spot_snapshot) values ($1,$2,$3)",
      [alice, osm, snapshot(osm)],
    ),
    { code: "42501" },
  );
  await pg.query(insert, [osm, snapshot(osm)]);
  equal(
    (await pg.query("select user_id from public.favorites")).rows[0].user_id,
    bob,
  );
  equal(
    (await pg.query(
      "delete from public.favorites where spot_id=$1 returning spot_id",
      [osm],
    )).rows.length,
    1,
  );
  equal(
    (await pg.query(
      "delete from public.favorites where spot_id=$1 returning spot_id",
      [osm],
    )).rows.length,
    0,
  );
  await role("anon");
  await rejects(pg.query("select * from public.favorites"), { code: "42501" });
  await rejects(pg.query(insert, [osm, snapshot(osm)]), { code: "42501" });
  await role("authenticated", alice);
  equal(
    (await pg.query("select count(*)::int as n from public.favorites")).rows[0]
      .n,
    4,
  );
  console.log(
    "PASS: PostgreSQL migration, legacy row and date, OSM and community snapshots, validation, idempotence, auth trigger, isolated owner RLS and grants",
  );
} finally {
  await pg.close();
}
