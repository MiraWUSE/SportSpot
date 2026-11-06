export type ModerationDecision = "approved" | "rejected";

export interface ModerationRequest {
  proposalId: string;
  decision: ModerationDecision;
  reason: string | null;
}

export type ModerationValidation =
  | { ok: true; value: ModerationRequest }
  | { ok: false; code: "INVALID_BODY" | "INVALID_REQUEST"; message: string };

export interface ModerationFailure {
  code: string;
  message: string;
  status: number;
}

export function parseBearerToken(authorization: string | null): string | null {
  const header = authorization ?? "";
  if (!/^Bearer\s+\S+$/i.test(header)) {
    return null;
  }
  return header.replace(/^Bearer\s+/i, "").trim();
}

export function validateModerationBody(parsed: unknown): ModerationValidation {
  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
    return { ok: false, code: "INVALID_BODY", message: "Expected a JSON object" };
  }

  const body = parsed as Record<string, unknown>;
  const allowedKeys = new Set(["proposal_id", "decision", "reason"]);
  if (Object.keys(body).some((key) => !allowedKeys.has(key))) {
    return { ok: false, code: "INVALID_BODY", message: "Unknown request fields" };
  }

  const proposalId = body.proposal_id;
  const decision = body.decision;
  const reason = body.reason;

  if (
    typeof proposalId !== "string" ||
    !/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(proposalId) ||
    (decision !== "approved" && decision !== "rejected") ||
    (reason !== undefined && typeof reason !== "string") ||
    (decision === "rejected" &&
      (typeof reason !== "string" || reason.trim().length === 0 || reason.length > 1000))
  ) {
    return {
      ok: false,
      code: "INVALID_REQUEST",
      message: "Invalid proposal, decision, or rejection reason",
    };
  }

  return { ok: true, value: { proposalId, decision, reason: reason ?? null } };
}

export function mapModerationRpcError(error: unknown): ModerationFailure {
  const payload = error && typeof error === "object" && !Array.isArray(error)
    ? error as Record<string, unknown>
    : {};

  switch (payload.message) {
    case "NOT_MODERATOR":
      return { code: "FORBIDDEN", message: "Moderator role required", status: 403 };
    case "PROPOSAL_NOT_FOUND":
      return { code: "PROPOSAL_NOT_FOUND", message: "Proposal does not exist", status: 404 };
    case "PROPOSAL_ALREADY_REVIEWED":
      return { code: "PROPOSAL_ALREADY_REVIEWED", message: "Proposal is already reviewed", status: 409 };
    case "REASON_REQUIRED":
    case "INVALID_DECISION":
      return { code: "INVALID_REQUEST", message: "Invalid moderation request", status: 400 };
  }

  if (payload.code === "23505") {
    return { code: "CONFLICT", message: "Conflicting published spot", status: 409 };
  }
  return { code: "SERVER_ERROR", message: "Moderation failed", status: 500 };
}
