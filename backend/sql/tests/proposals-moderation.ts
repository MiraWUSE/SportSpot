import { PGlite } from "npm:@electric-sql/pglite@0.3.15";
import { equal, rejects } from "node:assert/strict";

const pg = new PGlite();
const alice = "11111111-1111-4111-8111-111111111111";
const bob = "22222222-2222-4222-8222-222222222222";
const insert =
  "insert into public.spot_proposals(name,sport_type,latitude,longitude,address) values ($1,'tennis',$2,$3,$4) returning id,user_id,status";
const proposal = ["Корт", 56.3269, 44.0059, null];

async function role(name: "anon" | "authenticated" | "service_role", uid = "") {
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
    create role service_role bypassrls;
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
    `insert into auth.users values ($1, 'a@example.test', '{"role":"moderator"}'), ($2, 'b@example.test', null)`,
    [alice, bob],
  );

  await role("anon");
  await rejects(pg.query(insert, proposal), { code: "42501" });
  await rejects(pg.query("select * from public.spot_proposals"), {
    code: "42501",
  });

  await role("authenticated", alice);
  equal(
    (await pg.query<{ role: string }>("select role from public.profiles"))
      .rows[0].role,
    "user",
  );
  await rejects(
    pg.query("update public.profiles set role='moderator' where id=$1", [
      alice,
    ]),
    { code: "42501" },
  );
  await rejects(
    pg.query(
      "insert into public.spot_proposals(name,sport_type,latitude,longitude,status) values ('X','tennis',1,2,'approved')",
    ),
    { code: "42501" },
  );
  await rejects(
    pg.query(
      "insert into public.spot_proposals(name,sport_type,latitude,longitude,user_id) values ('X','tennis',1,2,$1)",
      [bob],
    ),
    { code: "42501" },
  );
  for (const [lat, lon] of [[91, 0], [0, 181], ["NaN", 0]]) {
    await rejects(pg.query(insert, ["Неверные координаты", lat, lon, null]), {
      code: "23514",
    });
  }
  const own = (await pg.query<{ id: string; user_id: string; status: string }>(
    insert,
    proposal,
  )).rows[0];
  equal(own.user_id, alice);
  equal(own.status, "pending");
  equal(
    (await pg.query<{ address: string | null }>(
      "select address from public.spot_proposals where id=$1",
      [
        own.id,
      ],
    )).rows[0].address,
    null,
  );
  await rejects(
    pg.query("update public.spot_proposals set status='approved' where id=$1", [
      own.id,
    ]),
    { code: "42501" },
  );
  await rejects(
    pg.query(
      "insert into public.spots(id,name,sport_type,latitude,longitude,source) values ($1,'X','tennis',1,2,'community')",
      [`community:${own.id}`],
    ),
    { code: "42501" },
  );

  await role("authenticated", bob);
  equal(
    (await pg.query("select id from public.spot_proposals where id=$1", [
      own.id,
    ])).rows.length,
    0,
  );
  await pg.query(insert, ["Площадка второго пользователя", 1, 2, "Адрес"]);
  equal(
    (await pg.query("select id from public.spot_proposals")).rows.length,
    1,
  );
  await role("anon");
  equal((await pg.query("select id from public.spots")).rows.length, 0);

  console.log(
    "PASS: proposal default owner/status, coordinates, isolated owner RLS, guest denial, forbidden role/status spoof",
  );
} finally {
  await pg.close();
}
