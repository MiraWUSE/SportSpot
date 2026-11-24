import { PGlite } from "npm:@electric-sql/pglite@0.3.15";
import { equal, rejects } from "node:assert/strict";

const base = await Deno.readTextFile(
  new URL("../../setup.sql", import.meta.url),
);
const migration = await Deno.readTextFile(
  new URL("../SportSpotReviewsOsmTitle.sql", import.meta.url),
);
const alice = "11111111-1111-4111-8111-111111111111";
const bob = "22222222-2222-4222-8222-222222222222";
const community = "community:33333333-3333-4333-8333-333333333333";
const osm = "osm:way:42";
const insert =
  "insert into public.reviews(spot_id, rating, text, title) values ($1, $2, 'Fixture', $3) returning id, user_id";

for (const legacy of [false, true]) {
  const pg = new PGlite();
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
    await pg.exec(base);
    await pg.query(
      "insert into auth.users values ($1, 'a@example.test', null), ($2, 'b@example.test', null)",
      [alice, bob],
    );
    let legacyRow;
    if (legacy) {
      await pg.query(
        "insert into public.spots(id,name,sport_type,latitude,longitude,source) values ($1,'Legacy','tennis',1,2,'community')",
        [community],
      );
      await role("authenticated", alice);
      legacyRow = (await pg.query(
        "insert into public.reviews(spot_id,rating,text) values ($1,3,'Existing') returning id,created_at",
        [community],
      )).rows[0];
      await pg.exec("reset role");
    }
    await pg.exec(migration);
    await pg.exec(migration);
    await role("anon");
    equal(
      (await pg.query("select id, created_at from public.reviews")).rows.length,
      legacy ? 1 : 0,
    );
    if (legacy) {
      const row =
        (await pg.query<{ title: string | null; id: string; created_at: Date }>(
          "select id,created_at,title from public.reviews",
        )).rows[0];
      equal(row.title, null);
      equal(row.id, (legacyRow as typeof row).id);
      equal(
        String(row.created_at),
        String((legacyRow as typeof row).created_at),
      );
    }
    await rejects(pg.query("select * from public.profiles"), { code: "42501" });
    await rejects(pg.query(insert, [osm, 5, "Title"]), { code: "42501" });

    await role("authenticated", alice);
    const review = (await pg.query<{ id: string; user_id: string }>(insert, [
      osm,
      5,
      "Title",
    ])).rows[0];
    equal(review.user_id, alice);
    await rejects(pg.query(insert, [osm, 4, null]), { code: "23505" });
    for (
      const [id, rating, title] of [
        ["osm:node:01", 4, null],
        ["osm:relation:-0", 4, null],
        ["osm:node:9223372036854775808", 4, null],
        ["osm:node:9", 0, null],
        ["osm:node:9", 6, null],
        ["osm:node:9", 4, " "],
        ["osm:node:9", 4, "z".repeat(121)],
      ]
    ) await rejects(pg.query(insert, [id, rating, title]), { code: "23514" });
    for (const id of ["osm:node:9", "osm:relation:-9223372036854775808"]) {
      await pg.query(insert, [id, 1, null]);
    }
    if (!legacy) await pg.query(insert, [community, 4, null]);
    await rejects(
      pg.query("update public.reviews set user_id = $1 where id = $2", [
        bob,
        review.id,
      ]),
      { code: "42501" },
    );
    await rejects(
      pg.query(
        "insert into public.reviews(user_id,spot_id,rating,text) values ($1,$2,5,'Spoof')",
        [bob, osm],
      ),
      { code: "42501" },
    );
    equal(
      (await pg.query(
        "update public.reviews set title=null, rating=4 where id=$1 returning title",
        [review.id],
      )).rows.length,
      1,
    );
    await role("authenticated", bob);
    equal(
      (await pg.query("select * from public.reviews where id=$1", [review.id]))
        .rows.length,
      1,
    );
    equal(
      (await pg.query(
        "update public.reviews set rating=1 where id=$1 returning id",
        [review.id],
      )).rows.length,
      0,
    );
    equal(
      (await pg.query("delete from public.reviews where id=$1 returning id", [
        review.id,
      ])).rows.length,
      0,
    );
    await pg.query(insert, [osm, 2, null]);
    await role("authenticated", alice);
    equal(
      (await pg.query("delete from public.reviews where id=$1 returning id", [
        review.id,
      ])).rows.length,
      1,
    );
  } finally {
    await pg.close();
  }
}

console.log(
  "PASS: reviews migration on empty and populated PostgreSQL, repeat, OSM/community, unique rating, owner RLS, guest and column grants",
);
