import "jsr:@supabase/functions-js/edge-runtime.d.ts";

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

  const authorization = req.headers.get("authorization") ?? "";
  if (!/^Bearer\s+\S+$/i.test(authorization)) {
    return failure("UNAUTHORIZED", "User access token required", 401);
  }

  const token = authorization.replace(/^Bearer\s+/i, "").trim();

  let body: Record<string, unknown>;
  try {
    const parsed: unknown = await req.json();
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
      return failure("INVALID_BODY", "Expected a JSON object", 400);
    }
    body = parsed as Record<string, unknown>;
  } catch {
    return failure("INVALID_BODY", "Invalid JSON", 400);
  }

  const allowedKeys = new Set(["proposal_id", "decision", "reason"]);
  if (Object.keys(body).some((key) => !allowedKeys.has(key))) {
    return failure("INVALID_BODY", "Unknown request fields", 400);
  }

  const proposalId = body.proposal_id;
  const decision = body.decision;
  const reason = body.reason;

  if (
    typeof proposalId !== "string" ||
    !/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(proposalId) ||
    (decision !== "approved" && decision !== "rejected") ||
    (reason !== undefined && typeof reason !== "string") ||
    (decision === "rejected" && (typeof reason !== "string" || reason.trim().length === 0 || reason.length > 1000))
  ) {
    return failure("INVALID_REQUEST", "Invalid proposal, decision, or rejection reason", 400);
  }

  const adminHeaders = {
    apikey: SERVICE_KEY,
    authorization: `Bearer ${SERVICE_KEY}`,
  };

  try {
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


    const rpcResponse = await fetch(`${URL}/rest/v1/rpc/moderate_spot_proposal`, {
      method: "POST",
      headers: { ...adminHeaders, "content-type": "application/json" },
      body: JSON.stringify({
        p_proposal_id: proposalId,
        p_moderator_id: user.id,
        p_decision: decision,
        p_reason: reason ?? null,
      }),
    });

    if (!rpcResponse.ok) {
      const error: { code?: string; message?: string } = await rpcResponse.json().catch(() => ({}));
      switch (error.message) {
        case "NOT_MODERATOR":
          return failure("FORBIDDEN", "Moderator role required", 403);
        case "PROPOSAL_NOT_FOUND":
          return failure("PROPOSAL_NOT_FOUND", "Proposal does not exist", 404);
        case "PROPOSAL_ALREADY_REVIEWED":
          return failure("PROPOSAL_ALREADY_REVIEWED", "Proposal is already reviewed", 409);
        case "REASON_REQUIRED":
        case "INVALID_DECISION":
          return failure("INVALID_REQUEST", "Invalid moderation request", 400);
      }
      if (error.code === "23505") {
        return failure("CONFLICT", "Conflicting published spot", 409);
      }
      return failure("SERVER_ERROR", "Moderation failed", 500);
    }

    const result: unknown = await rpcResponse.json();
    return response(result, 200);
  } catch {
    return failure("SERVER_ERROR", "Unexpected server error", 500);
  }
});
