import jpeg from "npm:jpeg-js@0.4.4";

const PENDING = "sportspot-photo-pending";
const APPROVED = "sportspot-photo-approved";
const MAX_BYTES = 5 * 1024 * 1024;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const SPOT_ID =
  /^(?:osm:(?:node|way|relation):(?:0|-?[1-9][0-9]{0,18})|community:[0-9a-f-]{36})$/;
const OBJECT_KEY = /^[0-9a-f-]{36}\/[0-9a-f-]{36}\.jpg$/;
const NO_CACHE = {
  "cache-control": "no-store",
  "x-content-type-options": "nosniff",
};

interface Config {
  origin?: string;
  serverKey?: string;
  cleanupToken?: string;
}

interface Submission {
  id: string;
  owner_id: string;
  target_type: string;
  target_id: string;
  object_key: string;
  content_hash: string;
  status: string;
  rejection_reason: string | null;
}

interface CleanupItem {
  bucket: string;
  object_key: string;
}

class PhotoError extends Error {
  constructor(readonly status: number, readonly code: string) {
    super(code);
  }
}

function json(data: unknown, status = 200): Response {
  return Response.json(data, { status, headers: NO_CACHE });
}

function validateId(value: string | null): string {
  if (value === null || !UUID.test(value)) {
    throw new PhotoError(400, "INVALID_ID");
  }
  return value;
}

function target(query: URLSearchParams, gallery = false) {
  const type = query.get("target_type");
  const id = query.get("target_id") ?? "";
  if (type === "spot") {
    if (
      !SPOT_ID.test(id) ||
      (id.startsWith("community:") && !UUID.test(id.slice(10)))
    ) {
      throw new PhotoError(400, "INVALID_TARGET");
    }
  } else if (type === "review" || (!gallery && type === "proposal")) {
    validateId(id);
  } else {
    throw new PhotoError(400, "INVALID_TARGET");
  }
  return { p_target_type: type, p_target_id: id };
}

async function boundedBody(
  req: Request | Response,
  maximum: number,
): Promise<Uint8Array<ArrayBuffer>> {
  const length = Number(req.headers.get("content-length") ?? 0);
  if (length > maximum) throw new PhotoError(413, "PHOTO_TOO_LARGE");
  const reader = req.body?.getReader();
  if (!reader) throw new PhotoError(400, "INVALID_BODY");
  const data = new Uint8Array(maximum);
  let used = 0;
  try {
    while (true) {
      const { value, done } = await reader.read();
      if (done) return data.subarray(0, used);
      if (value.byteLength > maximum - used) {
        await reader.cancel().catch(() => {});
        throw new PhotoError(413, "PHOTO_TOO_LARGE");
      }
      data.set(value, used);
      used += value.byteLength;
    }
  } finally {
    reader.releaseLock();
  }
}

function decodeJpeg(bytes: Uint8Array) {
  if (
    bytes.byteLength < 4 || bytes[0] !== 0xff || bytes[1] !== 0xd8 ||
    bytes.at(-2) !== 0xff || bytes.at(-1) !== 0xd9
  ) {
    throw new PhotoError(415, "INVALID_IMAGE");
  }
  try {
    const decoded = jpeg.decode(bytes, {
      useTArray: true,
      tolerantDecoding: false,
      maxResolutionInMP: 3,
      maxMemoryUsageInMB: 48,
    });
    if (
      decoded.width < 1 || decoded.height < 1 || decoded.width > 1600 ||
      decoded.height > 1600
    ) {
      throw new Error("Unsupported dimensions");
    }
    return decoded;
  } catch {
    throw new PhotoError(415, "INVALID_IMAGE");
  }
}

export function normalizePhoto(bytes: Uint8Array): Uint8Array<ArrayBuffer> {
  if (bytes.byteLength > MAX_BYTES) {
    throw new PhotoError(413, "PHOTO_TOO_LARGE");
  }
  const { width, height, data } = decodeJpeg(bytes);
  // Re-encode pixels only. APP markers, EXIF, comments and other payloads are discarded.
  const clean = new Uint8Array(jpeg.encode({ width, height, data }, 82).data);
  if (clean.byteLength > MAX_BYTES) {
    throw new PhotoError(413, "PHOTO_TOO_LARGE");
  }
  return clean;
}

async function digest(bytes: BufferSource): Promise<string> {
  return Array.from(
    new Uint8Array(await crypto.subtle.digest("SHA-256", bytes)),
    (byte) => byte.toString(16).padStart(2, "0"),
  ).join("");
}

async function validCleanupToken(
  expected: string | undefined,
  provided: string | null,
): Promise<boolean> {
  if (!expected || expected.length < 32 || !provided || provided.length > 256) {
    return false;
  }
  const encoder = new TextEncoder();
  const left = await digest(encoder.encode(expected));
  const right = await digest(encoder.encode(provided));
  let difference = 0;
  for (let i = 0; i < left.length; i++) {
    difference |= left.charCodeAt(i) ^ right.charCodeAt(i);
  }
  return difference === 0;
}

function clientView(row: Submission) {
  return {
    id: row.id,
    owner_id: row.owner_id,
    target_type: row.target_type,
    target_id: row.target_id,
    status: row.status,
    rejection_reason: row.rejection_reason,
  };
}

function photoHandler(config: Config, request: typeof fetch = fetch) {
  const origin = config.origin?.replace(/\/$/, "");
  const key = config.serverKey;

  const supabase = (
    path: string,
    init: RequestInit = {},
    bearer = key,
  ): Promise<Response> => {
    const headers = new Headers(init.headers);
    headers.set("apikey", key!);
    headers.set("authorization", `Bearer ${bearer}`);
    return request(origin + path, {
      ...init,
      headers,
      redirect: "error",
      signal: AbortSignal.timeout(15_000),
    });
  };

  async function rpc<T>(
    method: string,
    params: Record<string, unknown> = {},
  ): Promise<T> {
    const response = await supabase("/rest/v1/rpc/" + method, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(params),
    });
    if (response.ok) {
      if (response.status === 204) return undefined as T;
      return await response.json() as T;
    }
    const error = await response.json().catch(() => ({})) as {
      code?: string;
      message?: string;
    };
    const status = response.status === 429 ? 429 : ({
      "42501": 403,
      P0002: 404,
      P0001: 409,
      "22023": 400,
      "22P02": 400,
    }[error.code ?? ""] ?? 502);
    const code = /^[A-Z_]{3,40}$/.test(error.message ?? "") && status < 500
      ? error.message!
      : "PHOTO_REQUEST_FAILED";
    throw new PhotoError(status, code);
  }

  async function actor(req: Request): Promise<string> {
    const authorization = req.headers.get("authorization");
    const token = authorization?.match(/^Bearer ([^\s,]+)$/i)?.[1];
    if (!token) throw new PhotoError(401, "UNAUTHORIZED");
    const response = await supabase("/auth/v1/user", {}, token);
    if (response.status === 429) throw new PhotoError(429, "RATE_LIMITED");
    if (response.status >= 500) throw new PhotoError(502, "AUTH_UNAVAILABLE");
    if (!response.ok) throw new PhotoError(401, "UNAUTHORIZED");
    const user = await response.json() as { id?: string };
    if (!user.id || !UUID.test(user.id)) {
      throw new PhotoError(401, "UNAUTHORIZED");
    }
    return user.id;
  }

  function objectPath(
    bucket: string,
    objectKey: string,
    download = false,
  ): string {
    if (
      !OBJECT_KEY.test(objectKey) || (bucket !== PENDING && bucket !== APPROVED)
    ) {
      throw new PhotoError(502, "INVALID_STORAGE_RECORD");
    }
    return `/storage/v1/object/${
      download ? "authenticated/" : ""
    }${bucket}/${objectKey}`;
  }

  async function upload(
    bucket: string,
    objectKey: string,
    data: Uint8Array<ArrayBuffer>,
  ) {
    const response = await supabase(objectPath(bucket, objectKey), {
      method: "POST",
      headers: { "content-type": "image/jpeg", "x-upsert": "false" },
      body: data,
    });
    if (response.ok) return;
    // Storage may report a duplicate as HTTP 400 with statusCode 409.
    const error = await response.json().catch(() => ({})) as {
      statusCode?: string | number;
    };
    if (
      response.status === 409 ||
      (response.status === 400 && String(error.statusCode) === "409")
    ) return;
    throw new PhotoError(
      response.status === 413 || response.status === 429
        ? response.status
        : 502,
      "STORAGE_FAILED",
    );
  }

  async function readObject(row: Submission): Promise<Response> {
    const response = await supabase(
      objectPath(
        row.status === "approved" ? APPROVED : PENDING,
        row.object_key,
        true,
      ),
    );
    if (!response.ok || !response.body) {
      throw new PhotoError(
        response.status === 429 ? 429 : 502,
        "STORAGE_UNAVAILABLE",
      );
    }
    return response;
  }

  async function cleanup(req: Request): Promise<Response> {
    if (
      !await validCleanupToken(
        config.cleanupToken,
        req.headers.get("x-photo-cleanup-token"),
      )
    ) {
      throw new PhotoError(403, "FORBIDDEN");
    }
    const items = await rpc<CleanupItem[]>("photo_cleanup_batch");
    let removed = 0;
    let failed = false;
    for (const bucket of [PENDING, APPROVED]) {
      const keys = items.filter((item) => item.bucket === bucket).map((item) =>
        item.object_key
      );
      if (!keys.length) continue;
      const response = await supabase(`/storage/v1/object/${bucket}`, {
        method: "DELETE",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ prefixes: keys }),
      });
      if (!response.ok) {
        failed = true;
        continue;
      }
      await rpc("photo_cleanup_ack", { p_bucket: bucket, p_keys: keys });
      removed += keys.length;
    }
    if (failed) throw new PhotoError(502, "CLEANUP_RETRY");
    return json({ removed });
  }

  const getActions = [
    "gallery",
    "image",
    "mine",
    "queue",
    "proposal_queue",
    "preview",
  ];
  const postActions = ["upload", "remove", "approve", "reject", "cleanup"];
  return async (req: Request): Promise<Response> => {
    try {
      if (!origin || !key) throw new PhotoError(503, "PHOTO_API_UNAVAILABLE");
      const query = new URL(req.url).searchParams;
      const action = query.get("action") ?? "";
      if (!getActions.includes(action) && !postActions.includes(action)) {
        throw new PhotoError(400, "INVALID_ACTION");
      }
      if (req.method !== (getActions.includes(action) ? "GET" : "POST")) {
        throw new PhotoError(405, "METHOD_NOT_ALLOWED");
      }
      const command = <T>(values: Record<string, unknown>) =>
        rpc<T>("photo_command", values);
      if (action === "gallery") {
        return json(
          await command<string[]>({ p_action: action, ...target(query, true) }),
        );
      }
      if (action === "cleanup") return await cleanup(req);
      const caller = action === "image" ? null : await actor(req);

      if (
        action === "mine" || action === "queue" || action === "proposal_queue"
      ) {
        const filters = action === "mine"
          ? target(query)
          : action === "proposal_queue"
          ? { p_target_id: validateId(query.get("target_id")) }
          : {};
        const rows = await command<Submission[]>({
          p_actor: caller,
          p_action: action,
          ...filters,
        });
        return json(rows.map(clientView));
      }

      const id = validateId(query.get("id"));
      const args = { p_id: id, p_actor: caller };
      if (action === "upload") {
        const targetArgs = target(query);
        if (req.headers.get("content-type") !== "image/jpeg") {
          throw new PhotoError(415, "INVALID_IMAGE");
        }
        const data = normalizePhoto(await boundedBody(req, MAX_BYTES));
        const row = await command<Submission>({
          ...args,
          ...targetArgs,
          p_action: "reserve",
          p_hash: await digest(data),
        });
        if (row.status !== "uploading") return json(clientView(row));
        await upload(PENDING, row.object_key, data);
        const submitted = await command<Submission>({
          ...args,
          p_action: "submit",
        });
        return json(clientView(submitted), 201);
      }
      if (action === "image" || action === "preview") {
        const row = await command<Submission>({ ...args, p_action: action });
        const file = await readObject(row);
        return new Response(file.body, {
          headers: { ...NO_CACHE, "content-type": "image/jpeg" },
        });
      }
      if (action === "remove") {
        await command({ ...args, p_action: action });
        return json(null);
      }
      if (action === "approve") {
        const row = await command<Submission>({
          ...args,
          p_action: "moderate",
        });
        if (row.status !== "approved") {
          const file = await readObject(row);
          const bytes = await boundedBody(file, MAX_BYTES);
          decodeJpeg(bytes);
          if (await digest(bytes) !== row.content_hash) {
            throw new PhotoError(409, "PHOTO_CHANGED");
          }
          await upload(APPROVED, row.object_key, bytes);
        }
        return json(
          clientView(await command<Submission>({ ...args, p_action: action })),
        );
      }
      const raw = await boundedBody(req, 4096);
      let reason: unknown;
      try {
        reason = JSON.parse(new TextDecoder().decode(raw))?.reason;
      } catch {
        throw new PhotoError(400, "REASON_REQUIRED");
      }
      if (typeof reason !== "string" || !reason.trim() || reason.length > 500) {
        throw new PhotoError(400, "REASON_REQUIRED");
      }
      return json(
        clientView(
          await command<Submission>({
            ...args,
            p_action: "reject",
            p_reason: reason,
          }),
        ),
      );
    } catch (error) {
      if (error instanceof PhotoError) {
        return json(
          { error: { code: error.code, message: error.code } },
          error.status,
        );
      }
      return json({
        error: {
          code: "PHOTO_REQUEST_FAILED",
          message: "Photo request failed",
        },
      }, 502);
    }
  };
}

export { photoHandler };
