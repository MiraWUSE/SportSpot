import { PGlite } from "npm:@electric-sql/pglite@0.3.15";
import { equal, rejects } from "node:assert/strict";

const pg = new PGlite();
const alice = "11111111-1111-4111-8111-111111111111";
const bob = "22222222-2222-4222-8222-222222222222";
const reviewer = "33333333-3333-4333-8333-333333333333";
const missing = "44444444-4444-4444-8444-444444444444";
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

async function isolatedEdge() {
  let handle: ((request: Request) => Promise<Response>) | undefined;
  const originalServe = Deno.serve;
  const originalFetch = globalThis.fetch;
  const origin = "https://sportspot.invalid";
  const serviceKey = "fixture-only-service-role";
  Deno.env.set("SUPABASE_URL", origin);
  Deno.env.set("SUPABASE_SERVICE_ROLE_KEY", serviceKey);
  Deno.serve = ((handler: typeof handle) => {
    handle = handler;
  }) as typeof Deno.serve;
  try {
    await import("../../supabase/functions/moderate-spot-proposal-v1/index.ts");
  } finally {
    Deno.serve = originalServe;
  }
  globalThis.fetch = async (input, init) => {
    const request = new Request(input, init);
    const url = new URL(request.url);
    equal(url.origin, origin);
    equal(request.headers.get("apikey"), serviceKey);
    const bearer = request.headers.get("authorization");
    if (url.pathname === "/auth/v1/user") {
      const account = [alice, bob, reviewer].find((id) =>
        bearer === `Bearer fixture-${id}`
      );
      return account
        ? Response.json({ id: account })
        : Response.json({}, { status: 401 });
    }
    equal(bearer, `Bearer ${serviceKey}`);
    await role("service_role");
    if (url.pathname === "/rest/v1/profiles") {
      const uid = url.searchParams.get("id")?.slice(3);
      const result = await pg.query(
        "select role from public.profiles where id=$1",
        [uid],
      );
      return Response.json(result.rows);
    }
    equal(url.pathname, "/rest/v1/rpc/moderate_spot_proposal");
    const args = await request.json();
    try {
      const result = await pg.query<{ result: unknown }>(
        "select public.moderate_spot_proposal($1,$2,$3,$4) as result",
        [
          args.p_proposal_id,
          args.p_moderator_id,
          args.p_decision,
          args.p_reason,
        ],
      );
      return Response.json(result.rows[0].result);
    } catch (error) {
      const failure = error as Error & { code?: string };
      return Response.json({ code: failure.code, message: failure.message }, {
        status: 400,
      });
    }
  };
  return {
    decide: (uid: string | null, body: Record<string, string>) => {
      if (handle === undefined) {
        throw new Error("The Edge handler was not registered");
      }
      return handle(
        new Request(`${origin}/functions/v1/moderate-spot-proposal-v1`, {
          method: "POST",
          body: JSON.stringify(body),
          headers: uid === null
            ? {}
            : { authorization: `Bearer fixture-${uid}` },
        }),
      );
    },
    close: () => {
      globalThis.fetch = originalFetch;
    },
  };
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
    `insert into auth.users values ($1, 'a@example.test', '{"role":"moderator"}'), ($2, 'b@example.test', null), ($3, 'moderator@example.test', null)`,
    [alice, bob, reviewer],
  );
  await pg.query("update public.profiles set role='moderator' where id=$1", [
    reviewer,
  ]);

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
  const other = (await pg.query<{ id: string }>(insert, [
    "Площадка второго пользователя",
    1,
    2,
    "Адрес",
  ])).rows[0];
  equal(
    (await pg.query("select id from public.spot_proposals")).rows.length,
    1,
  );
  await role("anon");
  equal((await pg.query("select id from public.spots")).rows.length, 0);

  await role("authenticated", reviewer);
  equal(
    (await pg.query(
      "select id from public.spot_proposals where status='pending'",
    )).rows.length,
    2,
  );
  await rejects(
    pg.query("select public.moderate_spot_proposal($1,$2,'approved',null)", [
      own.id,
      reviewer,
    ]),
    { code: "42501" },
  );
  const edge = await isolatedEdge();
  try {
    const approve = { proposal_id: own.id, decision: "approved" };
    equal((await edge.decide(null, approve)).status, 401);
    equal((await edge.decide("expired", approve)).status, 401);
    equal((await edge.decide(alice, approve)).status, 403);
    equal(
      (await edge.decide(alice, { ...approve, role: "moderator" })).status,
      400,
    );
    equal(
      (await edge.decide(reviewer, { ...approve, proposal_id: missing }))
        .status,
      404,
    );
    equal(
      (await edge.decide(reviewer, {
        proposal_id: other.id,
        decision: "rejected",
        reason: "  ",
      })).status,
      400,
    );
    const response = await edge.decide(reviewer, approve);
    const communityId = `community:${own.id}`;
    equal(response.status, 200);
    equal((await response.json()).spot_id, communityId);
    equal((await edge.decide(reviewer, approve)).status, 409);

    const reason = "Уточните адрес";
    const rejected = await edge.decide(reviewer, {
      proposal_id: other.id,
      decision: "rejected",
      reason,
    });
    equal(rejected.status, 200);
    equal("spot_id" in await rejected.json(), false);
    await role("authenticated", bob);
    const myRejected =
      (await pg.query<{ status: string; reject_reason: string }>(
        "select status,reject_reason from public.spot_proposals",
      )).rows[0];
    equal(myRejected.status, "rejected");
    equal(myRejected.reject_reason, reason);
    await role("authenticated", alice);
    equal(
      (await pg.query<{ status: string }>(
        "select status from public.spot_proposals",
      )).rows[0].status,
      "approved",
    );

    await pg.exec("reset role");
    await pg.exec(
      await Deno.readTextFile(
        new URL("../SportSpotReviewsOsmTitle.sql", import.meta.url),
      ),
    );
    await role("authenticated", alice);
    await pg.query(
      "insert into public.reviews(spot_id,rating,text,title) values ($1,4,'Есть сетка','Корт')",
      [communityId],
    );

    await role("anon");
    const publicSpots = await pg.query<{ id: string }>(
      "select id from public.spots where source='community' and latitude between 56.318 and 56.335 and longitude between 43.99 and 44.02 and sport_type='tennis'",
    );
    equal(publicSpots.rows.length, 1);
    equal(publicSpots.rows[0].id, communityId);
    equal(
      (await pg.query("select id from public.spots where id=$1", [
        `community:${other.id}`,
      ])).rows.length,
      0,
    );
    equal(
      (await pg.query<{ title: string }>(
        "select title from public.reviews where spot_id=$1",
        [communityId],
      )).rows[0].title,
      "Корт",
    );
    await role("authenticated", reviewer);
    equal(
      (await pg.query(
        "select id from public.spot_proposals where status='pending'",
      )).rows.length,
      0,
    );
  } finally {
    edge.close();
  }
  console.log(
    "PASS: proposal RLS and real Edge handler over isolated PostgreSQL; approve, reject, conflict, publication and community reviews",
  );
} finally {
  await pg.close();
}
