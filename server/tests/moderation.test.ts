import { deepStrictEqual, strictEqual } from "node:assert/strict";
import {
  mapModerationRpcError,
  parseBearerToken,
  validateModerationBody,
} from "../../backend/supabase/functions/moderate-spot-proposal-v1/moderation-logic.ts";

const PROPOSAL_ID = "00000000-0000-4000-8000-000000000000";

function invalid(body: unknown, code: "INVALID_BODY" | "INVALID_REQUEST") {
  const result = validateModerationBody(body);
  strictEqual(result.ok, false);
  if (!result.ok) strictEqual(result.code, code);
}

Deno.test("JWT: извлекает токен из заголовка Bearer", () => {
  strictEqual(parseBearerToken("Bearer abc.def.ghi"), "abc.def.ghi");
});

Deno.test("JWT: не зависит от регистра слова Bearer", () => {
  strictEqual(parseBearerToken("bearer token-123"), "token-123");
});

Deno.test("JWT: отклоняет отсутствующий или некорректный заголовок авторизации", () => {
  for (const value of [null, "", "Basic token", "Bearer", "Bearer ", "Bearer a b"]) {
    strictEqual(parseBearerToken(value), null);
  }
});

Deno.test("Заявка: принимает решение об одобрении", () => {
  deepStrictEqual(validateModerationBody({
    proposal_id: PROPOSAL_ID,
    decision: "approved",
  }), {
    ok: true,
    value: { proposalId: PROPOSAL_ID, decision: "approved", reason: null },
  });
});

Deno.test("Заявка: принимает отклонение с указанием причины", () => {
  deepStrictEqual(validateModerationBody({
    proposal_id: PROPOSAL_ID,
    decision: "rejected",
    reason: "Недостаточно данных о местоположении",
  }), {
    ok: true,
    value: {
      proposalId: PROPOSAL_ID,
      decision: "rejected",
      reason: "Недостаточно данных о местоположении",
    },
  });
});

Deno.test("Заявка: допускает необязательную причину при одобрении", () => {
  const result = validateModerationBody({
    proposal_id: PROPOSAL_ID,
    decision: "approved",
    reason: "",
  });
  strictEqual(result.ok, true);
  if (result.ok) strictEqual(result.value.reason, "");
});

Deno.test("Заявка: отклоняет тело, которое не является JSON-объектом", () => {
  for (const body of [null, [], "hello", 10, true]) invalid(body, "INVALID_BODY");
});

Deno.test("Заявка: отклоняет неизвестные поля", () => {
  invalid({ proposal_id: PROPOSAL_ID, decision: "approved", role: "moderator" }, "INVALID_BODY");
});

Deno.test("Заявка: отклоняет некорректный UUID", () => {
  for (const id of ["not-a-uuid", "", 123, undefined]) {
    invalid({ proposal_id: id, decision: "approved" }, "INVALID_REQUEST");
  }
});

Deno.test("Заявка: отклоняет недопустимую версию и вариант UUID", () => {
  invalid({ proposal_id: "00000000-0000-0000-8000-000000000000", decision: "approved" }, "INVALID_REQUEST");
  invalid({ proposal_id: "00000000-0000-4000-0000-000000000000", decision: "approved" }, "INVALID_REQUEST");
});

Deno.test("Заявка: отклоняет неизвестное или отсутствующее решение", () => {
  invalid({ proposal_id: PROPOSAL_ID, decision: "pending" }, "INVALID_REQUEST");
  invalid({ proposal_id: PROPOSAL_ID }, "INVALID_REQUEST");
});

Deno.test("Заявка: отклоняет причину, которая не является строкой", () => {
  invalid({ proposal_id: PROPOSAL_ID, decision: "approved", reason: null }, "INVALID_REQUEST");
  invalid({ proposal_id: PROPOSAL_ID, decision: "rejected", reason: 123 }, "INVALID_REQUEST");
});

Deno.test("Заявка: запрещает отклонение без причины", () => {
  invalid({ proposal_id: PROPOSAL_ID, decision: "rejected" }, "INVALID_REQUEST");
});

Deno.test("Заявка: отклоняет пустую и слишком длинную причину", () => {
  invalid({ proposal_id: PROPOSAL_ID, decision: "rejected", reason: " \n " }, "INVALID_REQUEST");
  invalid({ proposal_id: PROPOSAL_ID, decision: "rejected", reason: "a".repeat(1001) }, "INVALID_REQUEST");
});

Deno.test("Заявка: принимает причину длиной ровно 1000 символов", () => {
  const result = validateModerationBody({
    proposal_id: PROPOSAL_ID,
    decision: "rejected",
    reason: "a".repeat(1000),
  });
  strictEqual(result.ok, true);
});

Deno.test("RPC: возвращает 403 при отсутствии прав модератора", () => {
  deepStrictEqual(mapModerationRpcError({ message: "NOT_MODERATOR" }), {
    code: "FORBIDDEN", message: "Moderator role required", status: 403,
  });
});

Deno.test("RPC: возвращает 404, если заявка не найдена", () => {
  deepStrictEqual(mapModerationRpcError({ message: "PROPOSAL_NOT_FOUND" }), {
    code: "PROPOSAL_NOT_FOUND", message: "Proposal does not exist", status: 404,
  });
});

Deno.test("RPC: возвращает 409 для уже обработанной заявки", () => {
  deepStrictEqual(mapModerationRpcError({ message: "PROPOSAL_ALREADY_REVIEWED" }), {
    code: "PROPOSAL_ALREADY_REVIEWED", message: "Proposal is already reviewed", status: 409,
  });
});

Deno.test("RPC: возвращает 400 при неверном решении или отсутствии причины", () => {
  for (const message of ["REASON_REQUIRED", "INVALID_DECISION"]) {
    deepStrictEqual(mapModerationRpcError({ message }), {
      code: "INVALID_REQUEST", message: "Invalid moderation request", status: 400,
    });
  }
});

Deno.test("RPC: возвращает 409 при нарушении уникальности", () => {
  deepStrictEqual(mapModerationRpcError({ code: "23505" }), {
    code: "CONFLICT", message: "Conflicting published spot", status: 409,
  });
});

Deno.test("RPC: возвращает 500 при неизвестной ошибке сервера", () => {
  for (const error of [{ message: "SOMETHING_ELSE" }, {}, null, "bad response"]) {
    deepStrictEqual(mapModerationRpcError(error), {
      code: "SERVER_ERROR", message: "Moderation failed", status: 500,
    });
  }
});
