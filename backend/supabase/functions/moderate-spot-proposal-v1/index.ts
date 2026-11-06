import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import {
  mapModerationRpcError,
  parseBearerToken,
  validateModerationBody,
} from "./moderation-logic.ts";

const URL = Deno.env.get("SUPABASE_URL");
const SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");

function response(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
    },
  });
}

Deno.serve(async (req: Request): Promise<Response> => {
  const requestId = crypto.randomUUID();
  const failure = (code: string, message: string, status: number): Response =>
    response({ error: { code, message, request_id: requestId } }, status);

  if (req.method !== "POST") {
    return failure("METHOD_NOT_ALLOWED", "Use POST", 405);
  }

  if (!URL || !SERVICE_KEY) {
    return failure("SERVER_CONFIG_ERROR", "Server is not configured", 500);
  }

  const token = parseBearerToken(req.headers.get("authorization"));
  if (token === null) {
    return failure("UNAUTHORIZED", "User access token required", 401);
  }

  let parsed: unknown;
  try {
    parsed = await req.json();
  } catch {
    return failure("INVALID_BODY", "Invalid JSON", 400);
  }

  const validation = validateModerationBody(parsed);
  if (!validation.ok) {
    return failure(validation.code, validation.message, 400);
  }

  const { proposalId, decision, reason } = validation.value;
  const adminHeaders = {
    apikey: SERVICE_KEY,
    authorization: `Bearer ${SERVICE_KEY}`,
  };

  try {
    // Supabase Auth verifies the real user access token.
    const authResponse = await fetch(`${URL}/auth/v1/user`, {
      headers: { apikey: SERVICE_KEY, authorization: `Bearer ${token}` },
    });
    if (!authResponse.ok) {
      return failure("UNAUTHORIZED", "Invalid or expired session", 401);
    }
    const user: { id?: unknown } = await authResponse.json();
    if (typeof user.id !== "string") {
      return failure("UNAUTHORIZED", "Invalid user identity", 401);
    }

    // The moderator role is read from server-owned profiles, not user input.
    const roleResponse = await fetch(
      `${URL}/rest/v1/profiles?id=eq.${encodeURIComponent(user.id)}&select=role&limit=1`,
      { headers: adminHeaders },
    );
    if (!roleResponse.ok) {
      return failure("SERVER_ERROR", "Could not check moderator role", 500);
    }
    const profiles: Array<{ role?: string }> = await roleResponse.json();
    if (profiles[0]?.role !== "moderator") {
      return failure("FORBIDDEN", "Moderator role required", 403);
    }

    // Atomic database operation: approve/reject and mark the proposal reviewed.
    const rpcResponse = await fetch(`${URL}/rest/v1/rpc/moderate_spot_proposal`, {
      method: "POST",
      headers: { ...adminHeaders, "content-type": "application/json" },
      body: JSON.stringify({
        p_proposal_id: proposalId,
        p_moderator_id: user.id,
        p_decision: decision,
        p_reason: reason,
      }),
    });

    if (!rpcResponse.ok) {
      const error: unknown = await rpcResponse.json().catch(() => ({}));
      const mapped = mapModerationRpcError(error);
      return failure(mapped.code, mapped.message, mapped.status);
    }

    const result: unknown = await rpcResponse.json();
    return response(result, 200);
  } catch {
    return failure("SERVER_ERROR", "Unexpected server error", 500);
  }
});
