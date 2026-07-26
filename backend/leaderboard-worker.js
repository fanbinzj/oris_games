/**
 * Dino Nugget Run — global leaderboard (Cloudflare Worker + KV).
 *
 * Endpoints (plain text, one "name|score" per line):
 *   GET  /top     -> top 10
 *   POST /submit  -> body "name|score"; responds with the updated top 10
 *
 * Requires a KV namespace bound as SCORES (see backend/README.md).
 */

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type",
};

const TOP_N = 10; // entries returned to the game
const MAX_STORED = 100; // entries kept in KV
const MAX_NAME = 12;
const MAX_SCORE = 50000; // generous sanity cap; legit runs are far below

function textResponse(body, status = 200) {
  return new Response(body, {
    status,
    headers: { ...CORS, "Content-Type": "text/plain; charset=utf-8" },
  });
}

function renderTop(list) {
  return list
    .slice(0, TOP_N)
    .map((e) => `${e.name}|${e.score}`)
    .join("\n");
}

async function readList(env) {
  return JSON.parse((await env.SCORES.get("top")) || "[]");
}

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: CORS });
    }
    const url = new URL(request.url);

    if (request.method === "GET" && url.pathname === "/top") {
      return textResponse(renderTop(await readList(env)));
    }

    if (request.method === "POST" && url.pathname === "/submit") {
      const body = (await request.text()).slice(0, 64);
      const sep = body.lastIndexOf("|");
      if (sep <= 0) return textResponse("bad request", 400);

      const name = body
        .slice(0, sep)
        .replace(/[|\r\n<>]/g, " ")
        .trim()
        .slice(0, MAX_NAME);
      const score = parseInt(body.slice(sep + 1), 10);
      if (!name || !Number.isInteger(score) || score <= 0 || score > MAX_SCORE) {
        return textResponse("bad request", 400);
      }

      const list = await readList(env);
      list.push({ name, score, t: Date.now() });
      // Best first; ties keep the earlier submission ahead.
      list.sort((a, b) => b.score - a.score || a.t - b.t);
      const trimmed = list.slice(0, MAX_STORED);
      await env.SCORES.put("top", JSON.stringify(trimmed));
      return textResponse(renderTop(trimmed));
    }

    return textResponse("not found", 404);
  },
};
