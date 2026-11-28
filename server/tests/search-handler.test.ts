import { deepEqual, equal, ok } from "node:assert/strict";
import {
  createSearchHandler,
  retryAfterSeconds,
} from "../../backend/supabase/functions/search-sports-spots-v1/search-handler.ts";

const lookupPath = "/rest/v1/rpc/spot_search_lookup";
const quotaPath = "/rest/v1/rpc/spot_search_take_overpass_quota";
const cacheWritePath = "/rest/v1/rpc/spot_search_cache_write";
const upstreamPath = "/v1/interpreter";
const url = "https://edge.example.test/functions/v1/search-sports-spots-v1";
const upstreamKey = "fixture-overpass-key";
const serviceKey = "fixture-service-key";
const cached = {
  spots: [],
  count: 0,
  source: "Overpass API",
  attribution: "© OpenStreetMap contributors",
};

function fixture(
  replies: Partial<Record<string, () => Response | Promise<Response>>> = {},
  config: Partial<Parameters<typeof createSearchHandler>[0]> = {},
) {
  const calls: { path: string; init: RequestInit }[] = [];
  const logs: Record<string, unknown>[] = [];
  const defaults: Record<string, () => Response> = {
    [lookupPath]: () =>
      Response.json({ cache: "miss", payload: null, retry_after: 0 }),
    [quotaPath]: () => Response.json(0),
    [upstreamPath]: () => Response.json({ elements: [] }),
    [cacheWritePath]: () => new Response(null, { status: 204 }),
  };
  const handler = createSearchHandler(
    {
      supabaseUrl: "https://supabase.example.test",
      serviceKey,
      overpassKey: upstreamKey,
      ...config,
    },
    async (input, init = {}) => {
      const path = new URL(String(input)).pathname;
      calls.push({ path, init });
      const reply = replies[path] ?? defaults[path];
      if (!reply) throw new Error("Unexpected fixture URL");
      return await reply();
    },
    (line) => logs.push(JSON.parse(line)),
  );
  return {
    async get(query = "lat=0&lon=0&radius=100") {
      return await handler(new Request(url + "?" + query));
    },
    handler,
    calls,
    logs,
    paths: () => calls.map(({ path }) => path),
  };
}

async function expectError(
  response: Response,
  status: number,
  code: string,
  retry?: number,
) {
  equal(response.status, status);
  equal(response.headers.get("cache-control"), "no-store");
  equal(
    response.headers.get("retry-after"),
    retry === undefined ? null : String(retry),
  );
  const body = await response.json();
  deepEqual(Object.keys(body.error).sort(), ["code", "message", "request_id"]);
  equal(body.error.code, code);
  equal(body.error.request_id, response.headers.get("x-request-id"));
  ok(body.error.request_id);
}

Deno.test("valid cached response uses the warm path even with no Overpass key", async () => {
  const test = fixture({
    [lookupPath]: () =>
      Response.json({ cache: "hit", payload: cached, retry_after: 0 }),
  }, { overpassKey: undefined });
  const response = await test.get();
  equal(response.status, 200);
  equal(response.headers.get("cache-control"), "no-store");
  deepEqual(await response.json(), { ...cached, cache: "hit" });
  deepEqual(test.paths(), [lookupPath]);
  equal(test.logs[0].reason, "cache_hit");
});

Deno.test("non-empty Overpass data keeps the Android spot fields and cached payload", async () => {
  const test = fixture({
    [upstreamPath]: () =>
      Response.json({
        elements: [{
          type: "node",
          id: 42,
          lat: 0,
          lon: 0,
          tags: { leisure: "pitch", sport: "soccer", name: "Fixture pitch" },
        }],
      }),
  });
  const response = await test.get();
  equal(response.status, 200);
  const { cache, ...payload } = await response.json();
  equal(cache, "miss");
  equal(payload.count, 1);
  const spot = payload.spots[0];
  equal(spot.id, "osm:node:42");
  equal(spot.name, "Fixture pitch");
  equal(spot.sport_type, "soccer");
  equal(spot.latitude, 0);
  equal(spot.longitude, 0);
  deepEqual(JSON.parse(test.calls[3].init.body as string).p_payload, payload);
});

Deno.test("radius boundaries and six decimals reach the Overpass payload unchanged", async () => {
  for (const radius of [100, 3000]) {
    const test = fixture();
    const response = await test.get(
      "lat=12.000001&lon=-8.123456&radius=" + radius,
    );
    equal(response.status, 200);
    deepEqual(await response.json(), { ...cached, cache: "miss" });
    deepEqual(test.paths(), [
      lookupPath,
      quotaPath,
      upstreamPath,
      cacheWritePath,
    ]);
    const values = JSON.parse(test.calls[0].init.body as string);
    equal(values.p_key, "v3:12.000001:-8.123456:" + radius + ":all");
    const body = test.calls[2].init.body as URLSearchParams;
    for (const type of ["node", "way", "relation"]) {
      ok(
        body.get("data")?.includes(
          type + "(around:" + radius + ",12.000001,-8.123456)",
        ),
      );
    }
    deepEqual(JSON.parse(test.calls[1].init.body as string), {
      p_minute_limit: 5,
      p_day_limit: 500,
    });
    equal(test.calls[2].init.redirect, "error");
    equal(
      new Headers(test.calls[2].init.headers).get("x-api-key"),
      upstreamKey,
    );
    const logs = JSON.stringify(test.logs);
    for (
      const secret of [upstreamKey, serviceKey, "12.000001", "-8.123456", "v3:"]
    ) {
      ok(!logs.includes(secret));
    }
  }
});

Deno.test("zero coordinates are valid, malformed and duplicate parameters never reach dependencies", async () => {
  const zero = fixture();
  equal((await zero.get("lat=0&lon=0")).status, 200);
  ok(
    (zero.calls[2].init.body as URLSearchParams).get("data")?.includes(
      "around:1000,0,0",
    ),
  );
  for (
    const query of [
      "",
      "lat=&lon=0",
      "lat=0&lon=null",
      "lat=0&lon=0&lat=0",
      "lat=0&lon=0&radius=800&radius=800",
      "lat=0&lon=0&other=1",
      "lat=0&lon=0&radius=0",
      "lat=0&lon=0&radius=99",
      "lat=0&lon=0&radius=3001",
      "lat=0&lon=0&radius=1.5",
      "lat=90.000001&lon=0",
      "lat=0&lon=180.000001",
      "lat=0.0000001&lon=0",
      "lat=0&lon=0&sport=cricket",
    ]
  ) {
    const test = fixture();
    await expectError(await test.get(query), 400, "INVALID_REQUEST");
    deepEqual(test.paths(), []);
  }
  const post = fixture();
  const response = await post.handler(new Request(url, { method: "POST" }));
  await expectError(response, 405, "METHOD_NOT_ALLOWED");
  equal(response.headers.get("allow"), "GET");
  deepEqual(post.paths(), []);
});

Deno.test("hit, miss and global outbound limits each return the supplied retry delay", async () => {
  for (const lookup of ["hit", "miss"]) {
    const test = fixture({
      [lookupPath]: () =>
        Response.json({ cache: lookup, payload: null, retry_after: 12 }),
    });
    await expectError(await test.get(), 429, "RATE_LIMITED", 12);
    deepEqual(test.paths(), [lookupPath]);
    equal(test.logs[0].reason, "cache_" + lookup + "_limit");
  }
  const outbound = fixture({ [quotaPath]: () => Response.json(19_280) });
  await expectError(await outbound.get(), 429, "RATE_LIMITED", 19_280);
  deepEqual(outbound.paths(), [lookupPath, quotaPath]);
  equal(outbound.logs[0].reason, "overpass_project_limit");
});

Deno.test("Overpass 429 preserves valid Retry-After and records only safe provider metrics", async () => {
  const test = fixture({
    [upstreamPath]: () =>
      new Response("provider body not for clients", {
        status: 429,
        headers: {
          "retry-after": "1929200",
          "x-ratelimit-limit": "10000",
          "x-ratelimit-remaining": "0",
        },
      }),
  });
  await expectError(await test.get(), 429, "RATE_LIMITED", 1929200);
  deepEqual(test.paths(), [lookupPath, quotaPath, upstreamPath]);
  equal(test.logs[0].reason, "overpass_provider_limit");
  equal(test.logs[0].upstream_status, 429);
  equal(test.logs[0].upstream_limit, 10000);
  equal(test.logs[0].upstream_remaining, 0);
  ok(!JSON.stringify(test.logs).includes("provider body"));
});

Deno.test("Retry-After accepts delta-seconds and HTTP date, with safe fallbacks", () => {
  const now = Date.UTC(2026, 0, 1);
  for (
    const [header, seconds] of [
      [" 42 ", 42],
      ["0", 1],
      [new Date(now + 2501).toUTCString(), 2],
      [new Date(now - 1010).toUTCString(), 1],
      ["-20", 60],
      ["1.2", 60],
      ["9999999999999999999999", 60],
      ["Thu, invalid", 60],
      [null, 60],
    ] as const
  ) {
    equal(retryAfterSeconds(header, now), seconds);
  }
});

Deno.test("Overpass 503 or timeout never retries upstream and never writes a cache entry", async () => {
  const busy = fixture({
    [upstreamPath]: () => new Response("provider busy", { status: 503 }),
  });
  await expectError(await busy.get(), 503, "UPSTREAM_UNAVAILABLE", 60);
  deepEqual(busy.paths(), [lookupPath, quotaPath, upstreamPath]);
  const timeout = fixture({
    [upstreamPath]: () =>
      Promise.reject(new DOMException("fixture", "TimeoutError")),
  });
  await expectError(await timeout.get(), 503, "UPSTREAM_UNAVAILABLE", 60);
  deepEqual(timeout.paths(), [lookupPath, quotaPath, upstreamPath]);
});

Deno.test("malformed and oversized upstream results are not cached", async () => {
  for (
    const reply of [
      () => Response.json({ remark: "incomplete", elements: [] }),
      () => new Response("malformed"),
      () => new Response(" ".repeat(1_500_001)),
    ]
  ) {
    const test = fixture({ [upstreamPath]: reply });
    await expectError(await test.get(), 503, "UPSTREAM_UNAVAILABLE", 60);
    deepEqual(test.paths(), [lookupPath, quotaPath, upstreamPath]);
  }
});

Deno.test("invalid quota config fails closed, valid lower limits reach the reservation", async () => {
  for (
    const config of [
      { overpassPerMinute: "6" },
      { overpassPerMinute: "1.1" },
      { overpassPerDay: "0" },
      { overpassPerDay: "501" },
      { overpassPerDay: "" },
    ]
  ) {
    const invalid = fixture({}, config);
    await expectError(await invalid.get(), 500, "SERVER_CONFIG_ERROR");
    deepEqual(invalid.paths(), [lookupPath]);
  }
  const valid = fixture({}, { overpassPerMinute: "2", overpassPerDay: "100" });
  equal((await valid.get()).status, 200);
  deepEqual(JSON.parse(valid.calls[1].init.body as string), {
    p_minute_limit: 2,
    p_day_limit: 100,
  });
});

Deno.test("a failed lookup or ambiguous reservation cannot trigger Overpass; cache failure keeps live data", async () => {
  for (const path of [lookupPath, quotaPath]) {
    const test = fixture({ [path]: () => new Response(null, { status: 503 }) });
    await expectError(await test.get(), 503, "UPSTREAM_UNAVAILABLE", 60);
    ok(!test.paths().includes(upstreamPath));
  }
  const failedWrite = fixture({
    [cacheWritePath]: () =>
      Promise.reject(new DOMException("fixture timeout", "TimeoutError")),
  });
  const response = await failedWrite.get();
  equal(response.status, 200);
  deepEqual(await response.json(), { ...cached, cache: "miss" });
  equal(failedWrite.logs[0].cache_write, "failed");
});

Deno.test("20 simultaneous cold requests send only the five admitted attempts upstream", async () => {
  let remaining = 5;
  const test = fixture({
    [quotaPath]: () => Response.json(remaining-- > 0 ? 0 : 40),
  });
  const responses = await Promise.all(
    Array.from({ length: 20 }, () => test.get()),
  );
  equal(responses.filter(({ status }) => status === 200).length, 5);
  equal(responses.filter(({ status }) => status === 429).length, 15);
  equal(test.paths().filter((path) => path === upstreamPath).length, 5);
});
