import { searchSpotsWithPhotos } from "./commons-photos.ts";
import { coord, makeCacheKey, query, SPORTS } from "./search-logic.ts";

const OVERPASS = "https://api.overpass.ch/v1/interpreter";
const PARAMETERS = new Set(["lat", "lon", "radius", "sport"]);
const DEFAULT_RETRY_SECONDS = 60;
const MAX_RETRY_SECONDS = 2147483647;
const MAX_RESPONSE_BYTES = 1_500_000;

interface SearchConfig {
  supabaseUrl?: string;
  serviceKey?: string;
  overpassKey?: string;
  overpassPerMinute?: string;
  overpassPerDay?: string;
}

interface CacheLookup {
  cache: "hit" | "miss";
  retry_after: number;
  payload: Record<string, unknown> | null;
}

interface SearchMetrics {
  cache: "hit" | "miss" | "unchecked";
  upstream_status?: number;
  upstream_limit?: number;
  upstream_remaining?: number;
  cache_write?: "failed";
}

function configuredLimit(raw: string | undefined, maximum: number): number {
  if (raw === undefined) return maximum;
  const value = /^\d+$/.test(raw) ? Number(raw) : 0;
  return value >= 1 && value <= maximum ? value : 0;
}

function quotaHeader(value: string | null): number | undefined {
  if (value === null || !/^\d+$/.test(value)) return;
  const result = Number(value);
  if (Number.isSafeInteger(result)) return result;
}

export function retryAfterSeconds(
  header: string | null,
  now = Date.now(),
): number {
  const value = header?.trim() ?? "";
  let seconds = quotaHeader(value);
  if (seconds === undefined && /^[A-Z][a-z]{2}, /.test(value)) {
    seconds = Math.ceil((Date.parse(value) - now) / 1000);
  }
  return seconds !== undefined && Number.isFinite(seconds) &&
      seconds <= MAX_RETRY_SECONDS
    ? Math.max(1, seconds)
    : DEFAULT_RETRY_SECONDS;
}

function isRetryDelay(value: number, maximum: number): boolean {
  return Number.isInteger(value) && value >= 0 && value <= maximum;
}

async function readOverpassElements(response: Response): Promise<unknown[]> {
  if (
    !response.body ||
    Number(response.headers.get("content-length")) > MAX_RESPONSE_BYTES
  ) {
    await response.body?.cancel();
    throw new Error("Invalid Overpass body size");
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder("utf-8", { fatal: true });
  let bytes = 0;
  let body = "";
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      bytes += value.byteLength;
      if (bytes > MAX_RESPONSE_BYTES) {
        throw new Error("Overpass body too large");
      }
      body += decoder.decode(value, { stream: true });
    }
  } finally {
    await reader.cancel().catch(() => {});
    reader.releaseLock();
  }

  const parsed = JSON.parse(body + decoder.decode());
  if (!Array.isArray(parsed?.elements) || parsed.remark) {
    throw new Error("Invalid Overpass result");
  }
  return parsed.elements;
}

export function createSearchHandler(
  config: SearchConfig,
  request: typeof fetch = fetch,
  log: (line: string) => void = console.log,
): (req: Request) => Promise<Response> {
  const base = config.supabaseUrl?.replace(/\/$/, "");
  const minuteLimit = configuredLimit(config.overpassPerMinute, 5);
  const dayLimit = configuredLimit(config.overpassPerDay, 500);

  async function rpc(
    name: string,
    body: Record<string, unknown>,
  ): Promise<Response> {
    const response = await request(base + "/rest/v1/rpc/" + name, {
      method: "POST",
      redirect: "error",
      headers: {
        apikey: config.serviceKey!,
        authorization: "Bearer " + config.serviceKey,
        "content-type": "application/json",
      },
      body: JSON.stringify(body),
      signal: AbortSignal.timeout(3500),
    });
    if (!response.ok) {
      await response.body?.cancel();
      throw new Error("Search RPC unavailable");
    }
    return response;
  }

  return async (req) => {
    const id = crypto.randomUUID();
    const start = Date.now();
    const metrics: SearchMetrics = { cache: "unchecked" };
    const send = (
      data: unknown,
      status: number,
      reason: string,
      headers?: Record<string, string>,
    ) => {
      log(JSON.stringify({
        request_id: id,
        status,
        reason,
        ...metrics,
        duration_ms: Date.now() - start,
      }));
      return Response.json(data, {
        status,
        headers: {
          "cache-control": "no-store",
          "x-request-id": id,
          ...headers,
        },
      });
    };
    const error = (
      status: number,
      code: string,
      message: string,
      reason: string,
      headers?: Record<string, string>,
    ) =>
      send(
        { error: { code, message, request_id: id } },
        status,
        reason,
        headers,
      );
    const limited = (reason: string, retryAfter: number) =>
      error(429, "RATE_LIMITED", "Too many requests", reason, {
        "retry-after": String(retryAfter),
      });
    const unavailable = (reason: string, retryAfter = DEFAULT_RETRY_SECONDS) =>
      error(
        503,
        "UPSTREAM_UNAVAILABLE",
        "Sports data providers temporarily unavailable",
        reason,
        {
          "retry-after": String(retryAfter),
        },
      );

    if (req.method !== "GET") {
      return error(405, "METHOD_NOT_ALLOWED", "Use GET", "method", {
        allow: "GET",
      });
    }
    const params = new URL(req.url).searchParams;
    for (const name of params.keys()) {
      if (!PARAMETERS.has(name) || params.getAll(name).length !== 1) {
        return error(
          400,
          "INVALID_REQUEST",
          "Unknown or repeated parameter",
          "validation",
        );
      }
    }
    const lat = coord(params.get("lat"), 90);
    const lon = coord(params.get("lon"), 180);
    const radiusText = params.get("radius") || "1000";
    const radius = Number(radiusText);
    const sport = params.get("sport") || "all";
    if (
      lat === null || lon === null || !/^\d+$/.test(radiusText) ||
      radius < 100 || radius > 3000 || !SPORTS.has(sport)
    ) {
      return error(
        400,
        "INVALID_REQUEST",
        "Invalid coordinates, radius or sport",
        "validation",
      );
    }
    if (!base || !config.serviceKey) {
      return error(
        500,
        "INTERNAL_ERROR",
        "Service not configured",
        "database_config",
      );
    }

    const key = makeCacheKey(lat, lon, radius, sport);
    let reason = "cache_lookup";
    try {
      const lookup: CacheLookup =
        await (await rpc("spot_search_lookup", { p_key: key })).json();
      if (
        !lookup || (lookup.cache !== "hit" && lookup.cache !== "miss") ||
        !isRetryDelay(lookup.retry_after, 60)
      ) throw new Error("Invalid search lookup");
      metrics.cache = lookup.cache;
      if (lookup.retry_after > 0) {
        return limited(
          lookup.cache === "hit" ? "cache_hit_limit" : "cache_miss_limit",
          lookup.retry_after,
        );
      }
      if (lookup.cache === "hit") {
        if (!Array.isArray(lookup.payload?.spots)) {
          throw new Error("Invalid cache payload");
        }
        return send({ ...lookup.payload, cache: "hit" }, 200, "cache_hit");
      }
      if (!config.overpassKey || minuteLimit === 0 || dayLimit === 0) {
        return error(
          500,
          "SERVER_CONFIG_ERROR",
          "Overpass is not configured",
          "overpass_config",
        );
      }

      reason = "quota_rpc";
      const delay: number =
        await (await rpc("spot_search_take_overpass_quota", {
          p_minute_limit: minuteLimit,
          p_day_limit: dayLimit,
        })).json();
      if (!isRetryDelay(delay, 86400)) {
        throw new Error("Invalid Overpass quota result");
      }
      if (delay > 0) return limited("overpass_project_limit", delay);

      reason = "upstream_request";
      const upstream = await request(OVERPASS, {
        method: "POST",
        redirect: "error",
        headers: {
          "content-type": "application/x-www-form-urlencoded; charset=UTF-8",
          accept: "application/json",
          "x-api-key": config.overpassKey,
          "user-agent":
            "SportSpot/1.0 (+https://github.com/MiraWUSE/SportSpot; educational sports map app)",
        },
        body: new URLSearchParams({ data: query(lat, lon, radius, sport) }),
        signal: AbortSignal.timeout(18000),
      });
      metrics.upstream_status = upstream.status;
      metrics.upstream_limit = quotaHeader(
        upstream.headers.get("x-ratelimit-limit"),
      );
      metrics.upstream_remaining = quotaHeader(
        upstream.headers.get("x-ratelimit-remaining"),
      );
      if (!upstream.ok) {
        await upstream.body?.cancel().catch(() => {});
        const retryAfter = retryAfterSeconds(
          upstream.headers.get("retry-after"),
        );
        return upstream.status === 429
          ? limited("overpass_provider_limit", retryAfter)
          : unavailable("upstream_http", retryAfter);
      }

      reason = "upstream_body";
      const elements = await readOverpassElements(upstream);
      const spots = await searchSpotsWithPhotos(elements, request);
      const payload = {
        spots,
        count: spots.length,
        source: "Overpass API",
        attribution: "© OpenStreetMap contributors",
      };
      try {
        const saved = await rpc("spot_search_cache_write", {
          p_key: key,
          p_payload: payload,
        });
        await saved.body?.cancel();
      } catch {
        // The live result remains usable when the cache cannot be written.
        metrics.cache_write = "failed";
      }
      return send({ ...payload, cache: "miss" }, 200, "upstream_ok");
    } catch {
      return unavailable(reason);
    }
  };
}
