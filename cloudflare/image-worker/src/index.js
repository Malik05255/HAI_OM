const PRIMARY_MODEL = "@cf/black-forest-labs/flux-2-dev";
const FALLBACK_MODEL = "@cf/black-forest-labs/flux-2-klein-9b";

function json(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store"
    }
  });
}

function clamp(value, min, max, fallback) {
  const number = Number(value);
  if (!Number.isFinite(number)) return fallback;
  return Math.min(max, Math.max(min, Math.round(number)));
}

function extractImage(result) {
  if (typeof result === "string" && result.trim()) return result.trim();
  if (result && typeof result.image === "string" && result.image.trim()) {
    return result.image.trim();
  }
  if (
    result &&
    result.result &&
    typeof result.result.image === "string" &&
    result.result.image.trim()
  ) {
    return result.result.image.trim();
  }
  return "";
}

async function runModel(env, model, prompt, width, height, seed) {
  const form = new FormData();
  form.append("prompt", prompt);
  form.append("width", String(width));
  form.append("height", String(height));
  form.append("seed", String(seed));

  if (model === PRIMARY_MODEL) {
    form.append("steps", "28");
    form.append("guidance", "4");
  }

  const formRequest = new Request("https://h-agent.invalid/generate", {
    method: "POST",
    body: form
  });

  return env.AI.run(model, {
    multipart: {
      body: formRequest.body,
      contentType:
        formRequest.headers.get("content-type") || "multipart/form-data"
    }
  });
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method === "GET" && url.pathname === "/health") {
      return json({ ok: true });
    }

    if (request.method !== "POST" || url.pathname !== "/generate") {
      return json({ error: "Not found" }, 404);
    }

    if (!env.H_AGENT_APP_KEY) {
      return json({ error: "Image relay is not configured" }, 503);
    }

    if (request.headers.get("X-H-Agent-Key") !== env.H_AGENT_APP_KEY) {
      return json({ error: "Unauthorized" }, 401);
    }

    let input;
    try {
      input = await request.json();
    } catch {
      return json({ error: "Invalid request" }, 400);
    }

    const prompt =
      typeof input.prompt === "string" ? input.prompt.trim() : "";

    if (!prompt || prompt.length > 4000) {
      return json({ error: "Invalid prompt" }, 400);
    }

    const width = clamp(input.width, 512, 1536, 1024);
    const height = clamp(input.height, 512, 1536, 1024);
    const seed = clamp(input.seed, 0, 2147483647, Date.now() % 2147483647);

    let result;
    let model = PRIMARY_MODEL;

    try {
      result = await runModel(env, PRIMARY_MODEL, prompt, width, height, seed);
      if (!extractImage(result)) throw new Error("Primary model returned no image");
    } catch {
      model = FALLBACK_MODEL;
      try {
        result = await runModel(env, FALLBACK_MODEL, prompt, width, height, seed);
      } catch {
        return json({ error: "Image generation temporarily unavailable" }, 503);
      }
    }

    const image = extractImage(result);
    if (!image) {
      return json({ error: "Image model returned no image" }, 502);
    }

    return json({
      image,
      mime: "image/png",
      model
    });
  }
};
