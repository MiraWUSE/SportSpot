import { PGlite } from "npm:@electric-sql/pglite@0.3.15";
import jpeg from "npm:jpeg-js@0.4.4";
import { deepEqual, equal, ok, rejects } from "node:assert/strict";
import { photoHandler } from "../../supabase/functions/user-photos-v1/photo-handler.ts";

const pg = new PGlite();
const alice = "11111111-1111-4111-8111-111111111111";
const bob = "22222222-2222-4222-8222-222222222222";
const moderator = "33333333-3333-4333-8333-333333333333";
const spot = "osm:way:123";
const image = new Uint8Array(
  jpeg.encode({ width: 1, height: 1, data: new Uint8Array([0, 200, 0, 255]) })
    .data,
);
const origin = "https://photos.invalid";
const cleanupToken = "isolated test cleanup authorization only";
const files = new Map<string, Uint8Array<ArrayBuffer>>();
let failFinalization = false;

async function role(name = "", user = "") {
  await pg.exec("reset role");
  await pg.query("select set_config('request.jwt.claim.sub', $1, false)", [
    user,
  ]);
  if (name) await pg.exec(`set role ${name}`);
}

const handler = photoHandler({
  origin,
  serverKey: "fixture-only-admin",
  cleanupToken,
}, async (input, init) => {
  const req = new Request(input, init);
  const url = new URL(req.url);
  equal(url.origin, origin);
  if (url.pathname === "/auth/v1/user") {
    const user = [alice, bob, moderator].find((id) =>
      req.headers.get("authorization") === "Bearer " + id
    );
    return user
      ? Response.json({ id: user })
      : Response.json({}, { status: 401 });
  }
  equal(req.headers.get("authorization"), "Bearer fixture-only-admin");
  if (url.pathname.startsWith("/rest/v1/rpc/")) {
    await role("service_role");
    try {
      const name = url.pathname.split("/").at(-1);
      const body = await req.json();
      if (
        name === "photo_command" && body.p_action === "approve" &&
        failFinalization
      ) {
        failFinalization = false;
        return Response.json({}, { status: 503 });
      }
      const query = name === "photo_command"
        ? {
          sql: "select public.photo_command($1,$2,$3,$4,$5,$6,$7) result",
          args: [
            "p_action",
            "p_actor",
            "p_id",
            "p_target_type",
            "p_target_id",
            "p_hash",
            "p_reason",
          ].map((name) => body[name] ?? null),
        }
        : name === "photo_cleanup_ack"
        ? {
          sql: "select public.photo_cleanup_ack($1,$2) result",
          args: [body.p_bucket, body.p_keys],
        }
        : { sql: "select public.photo_cleanup_batch() result", args: [] };
      return Response.json(
        (await pg.query<{ result: unknown }>(query.sql, query.args)).rows[0]
          .result ?? null,
      );
    } catch (error) {
      const pgError = error as Error & { code?: string };
      return Response.json({ code: pgError.code, message: pgError.message }, {
        status: 400,
      });
    } finally {
      await role();
    }
  }
  const path = url.pathname.replace("/storage/v1/object/", "").replace(
    /^authenticated\//,
    "",
  );
  ok(path.startsWith("sportspot-photo-"));
  if (req.method === "POST") {
    equal(req.headers.get("x-upsert"), "false");
    if (files.has(path)) {
      return Response.json({ statusCode: "409" }, { status: 400 });
    }
    files.set(path, new Uint8Array(await req.arrayBuffer()));
    return Response.json({}, { status: 201 });
  }
  if (req.method === "DELETE") {
    for (const key of (await req.json()).prefixes) {
      files.delete(path + "/" + key);
    }
    return Response.json([]);
  }
  return files.has(path)
    ? new Response(files.get(path))
    : new Response(null, { status: 404 });
});

function action(
  name: string,
  params: Record<string, string>,
  user?: string,
  body?: BodyInit,
  mime?: string,
) {
  const post = ["upload", "approve", "reject", "remove", "cleanup"].includes(
    name,
  );
  const headers = new Headers();
  if (user) headers.set("authorization", "Bearer " + user);
  if (mime) headers.set("content-type", mime);
  return handler(
    new Request(
      origin + "/functions/v1/user-photos-v1?" +
        new URLSearchParams({ action: name, ...params }),
      {
        method: post ? "POST" : "GET",
        headers,
        body,
      },
    ),
  );
}

const target = (type: string, id: string) => ({
  target_type: type,
  target_id: id,
});
const upload = (
  who: string | undefined,
  where: Record<string, string>,
  id: string,
  bytes: Uint8Array<ArrayBuffer> = image,
) => action("upload", { ...where, id }, who, bytes, "image/jpeg");

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
    create schema storage;
    grant usage on schema storage to anon, authenticated;
    create table storage.buckets (id text primary key, name text, public boolean, file_size_limit bigint, allowed_mime_types text[]);
    create table storage.objects (id uuid default gen_random_uuid(), bucket_id text, name text);
    alter table storage.objects enable row level security;
    grant select, insert, update, delete on storage.objects to anon, authenticated;
    create policy fixture_other_storage_policy on storage.objects for all to anon, authenticated using (true) with check (true);
  `);
  for (
    const path of [
      "../../setup.sql",
      "../SportSpotReviewsOsmTitle.sql",
      "../../supabase/migrations/20261124181925_moderated_user_photos.sql",
    ]
  ) {
    await pg.exec(await Deno.readTextFile(new URL(path, import.meta.url)));
  }
  await pg.query("insert into auth.users(id) values ($1),($2),($3)", [
    alice,
    bob,
    moderator,
  ]);
  await pg.query("update public.profiles set role='moderator' where id=$1", [
    moderator,
  ]);
  equal(
    (await pg.query(
      "select id from storage.buckets where not public and file_size_limit=5242880",
    )).rows.length,
    2,
  );

  const spotTarget = target("spot", spot);
  const id = crypto.randomUUID();
  equal((await upload(undefined, spotTarget, id)).status, 401);
  equal(
    (await upload(alice, target("spot", "community:" + id), id)).status,
    403,
  );
  equal(
    (await upload(alice, target("spot", "osm:node:9223372036854775808"), id))
      .status,
    403,
  );
  equal(
    (await upload(
      alice,
      spotTarget,
      id,
      new Uint8Array([0xff, 0xd8, 1, 2, 0xff, 0xd9]),
    )).status,
    415,
  );
  equal(
    (await upload(alice, spotTarget, id, new Uint8Array(5242881))).status,
    413,
  );
  const pending = await upload(alice, spotTarget, id);
  equal(pending.status, 201);
  equal((await pending.json()).status, "pending");
  equal((await upload(alice, spotTarget, id)).status, 200);
  equal(files.size, 1);
  equal((await upload(bob, spotTarget, id)).status, 409);
  equal((await upload(alice, target("spot", "osm:node:1"), id)).status, 409);
  equal((await action("approve", { id }, alice)).status, 403);
  equal((await action("preview", { id }, alice)).status, 200);
  equal((await action("preview", { id }, moderator)).status, 200);
  equal((await action("preview", { id }, bob)).status, 403);
  equal((await action("image", { id })).status, 404);
  deepEqual(await (await action("gallery", spotTarget)).json(), []);

  await pg.exec(
    "insert into storage.objects(bucket_id,name) values ('sportspot-photo-pending','fixture')",
  );
  await role("authenticated", bob);
  equal(
    (await pg.query("select id from public.photo_submissions")).rows.length,
    0,
  );
  equal((await pg.query("select * from storage.objects")).rows.length, 0);
  await rejects(
    pg.query(
      "insert into storage.objects(bucket_id,name) values ('sportspot-photo-approved','fixture')",
    ),
    { code: "42501" },
  );
  await rejects(
    pg.query("update public.photo_submissions set status='approved'"),
    { code: "42501" },
  );
  await rejects(
    pg.query(
      "update public.photo_submissions set owner_id=$1, target_id='x', object_key='x'",
      [bob],
    ),
    { code: "42501" },
  );
  await rejects(
    pg.query("select public.photo_command('approve',$1,$2)", [moderator, id]),
    { code: "42501" },
  );
  await role("authenticated", alice);
  equal(
    (await pg.query("select id from public.photo_submissions")).rows.length,
    1,
  );
  await role("authenticated", moderator);
  equal(
    (await pg.query("select id from public.photo_submissions")).rows.length,
    1,
  );
  await role();

  failFinalization = true;
  equal((await action("approve", { id }, moderator)).status, 502);
  equal(files.size, 2);
  equal((await action("image", { id })).status, 404);
  equal((await action("approve", { id }, moderator)).status, 200);
  equal((await action("approve", { id }, moderator)).status, 200);
  await role("anon");
  equal((await pg.query("select id from public.spot_photos")).rows.length, 1);
  await rejects(pg.query("select storage_path from public.spot_photos"), {
    code: "42501",
  });
  await role();
  deepEqual(await (await action("gallery", spotTarget)).json(), [id]);
  equal((await action("image", { id })).status, 200);
  equal((await action("remove", { id }, bob)).status, 403);
  equal((await action("remove", { id }, alice)).status, 200);
  equal((await action("image", { id })).status, 404);

  const review = crypto.randomUUID();
  await pg.query(
    "insert into public.reviews(id,user_id,spot_id,rating,text) values ($1,$2,$3,5,'Court')",
    [review, alice, spot],
  );
  const reviewTarget = target("review", review);
  equal((await upload(bob, reviewTarget, crypto.randomUUID())).status, 403);
  for (let i = 0; i < 3; i++) {
    equal((await upload(alice, reviewTarget, crypto.randomUUID())).status, 201);
  }
  equal((await upload(alice, reviewTarget, crypto.randomUUID())).status, 409);
  const reviewId =
    (await (await action("mine", reviewTarget, alice)).json())[0].id;
  equal((await action("approve", { id: reviewId }, moderator)).status, 200);
  await pg.query("delete from public.reviews where id=$1", [review]);
  equal((await action("image", { id: reviewId })).status, 404);
  equal((await pg.query("select id from public.review_photos")).rows.length, 0);

  for (const decision of ["approved", "rejected"]) {
    const proposal = crypto.randomUUID();
    await pg.query(
      "insert into public.spot_proposals(id,user_id,name,sport_type,latitude,longitude) values ($1,$2,'Court','tennis',56,44)",
      [proposal, alice],
    );
    const proposalTarget = target("proposal", proposal);
    const photo = crypto.randomUUID();
    equal((await upload(bob, proposalTarget, photo)).status, 403);
    equal((await upload(alice, proposalTarget, photo)).status, 201);
    equal(
      (await action("proposal_queue", { target_id: proposal }, moderator))
        .status,
      200,
    );
    equal((await action("approve", { id: photo }, moderator)).status, 200);
    equal((await action("image", { id: photo })).status, 404);
    equal((await action("preview", { id: photo }, alice)).status, 200);
    await pg.query("select public.moderate_spot_proposal($1,$2,$3,'Reason')", [
      proposal,
      moderator,
      decision,
    ]);
    deepEqual(
      await (await action("gallery", target("spot", "community:" + proposal)))
        .json(),
      decision === "approved" ? [photo] : [],
    );
    equal(
      (await action("image", { id: photo })).status,
      decision === "approved" ? 200 : 404,
    );
  }

  const rejected = crypto.randomUUID();
  equal((await upload(alice, spotTarget, rejected)).status, 201);
  equal(
    (await action(
      "reject",
      { id: rejected },
      moderator,
      JSON.stringify({ reason: "" }),
    )).status,
    400,
  );
  equal(
    (await action(
      "reject",
      { id: rejected },
      moderator,
      JSON.stringify({ reason: "No consent" }),
    )).status,
    200,
  );
  equal((await action("preview", { id: rejected }, alice)).status, 403);
  equal((await action("cleanup", {})).status, 403);
  await pg.exec("update app_private.photo_cleanup set available_at=now()");
  const cleanup = await handler(
    new Request(origin + "/?action=cleanup", {
      method: "POST",
      headers: { "x-photo-cleanup-token": cleanupToken },
    }),
  );
  equal(cleanup.status, 200);
  equal(
    (await pg.query("select * from app_private.photo_cleanup")).rows.length,
    0,
  );
  equal(files.size, 1);
  console.log("PASS isolated photo SQL/RLS and Edge/Storage lifecycle");
} finally {
  await pg.close();
}
