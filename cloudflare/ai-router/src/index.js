const PROVIDERS = {
  nvidia: {
    envKey: "NVIDIA_API_KEY",
    url: "https://integrate.api.nvidia.com/v1/chat/completions",
    model: "z-ai/glm-5-3"
  },
  gemini: {
    envKey: "GEMINI_API_KEY",
    url: "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
    model: "gemini-3.8-flash"
  },
  mistral: {
    envKey: "MISTRAL_API_KEY",
    url: "https://api.mistral.ai/v1/chat/completions",
    model: "mistral-medium-latest"
  },
  cerebras: {
    envKey: "CEREBRAS_API_KEY",
    url: "https://api.cerebras.ai/v1/chat/completions",
    model: "zai-glm-4.7"
  },
  fireworks: {
    envKey: "FIREWORKS_API_KEY",
    url: "https://api.fireworks.ai/inference/v1/chat/completions",
    model: "accounts/fireworks/models/glm-5p3"
  },
  groq: {
    envKey: "GROQ_API_KEY",
    url: "https://api.groq.com/openai/v1/chat/completions",
    model: "openai/gpt-oss-120b"
  }
};

const HEALTH = new Map();
const COOLDOWN_CACHE_PREFIX = "https://h-agent-router.invalid/cooldown/";
const MAX_INPUT_CHARS = 180_000;
const ALLOWED_HINTS = new Set(["chat", "code", "repo_code", "prompt_optimize"]);

function json(body, status = 200, extraHeaders = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
      ...extraHeaders
    }
  });
}

function clampNumber(value, min, max, fallback) {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) return fallback;
  return Math.min(max, Math.max(min, parsed));
}

function normalizeContent(content) {
  if (typeof content === "string") return content.trim();
  if (!Array.isArray(content)) return "";
  return content
    .map((part) => {
      if (typeof part === "string") return part;
      if (part && typeof part.text === "string") return part.text;
      if (part && typeof part.content === "string") return part.content;
      return "";
    })
    .filter(Boolean)
    .join("\n")
    .trim();
}

function sanitizeMessages(input) {
  if (!Array.isArray(input) || input.length === 0) {
    throw new Error("messages_required");
  }

  const systems = [];
  const conversational = [];
  let total = 0;

  for (const item of input.slice(-40)) {
    if (!item || typeof item !== "object") continue;
    const role = String(item.role || "").toLowerCase();
    if (!["system", "user", "assistant"].includes(role)) continue;

    const content = normalizeContent(item.content);
    if (!content) continue;

    total += content.length;
    if (total > MAX_INPUT_CHARS) throw new Error("input_too_large");

    if (role === "system") {
      systems.push(content);
      continue;
    }

    const previous = conversational[conversational.length - 1];
    if (previous && previous.role === role) {
      previous.content += "\n\n" + content;
    } else {
      conversational.push({ role, content });
    }
  }

  if (conversational.length === 0) throw new Error("user_message_required");

  while (conversational.length && conversational[0].role === "assistant") {
    conversational.shift();
  }
  if (conversational.length === 0) throw new Error("user_message_required");

  const messages = [];
  if (systems.length) {
    messages.push({
      role: "system",
      content: systems.join("\n\n")
    });
  }
  messages.push(...conversational);
  return messages;
}

function totalChars(messages) {
  return messages.reduce((sum, message) => sum + String(message.content || "").length, 0);
}

function routeOrder(hint, chars) {
  if (hint === "repo_code") {
    if (chars >= 60_000) {
      return ["gemini", "nvidia", "fireworks", "mistral", "cerebras", "groq"];
    }
    return ["nvidia", "gemini", "mistral", "cerebras", "groq", "fireworks"];
  }

  if (hint === "code") {
    if (chars <= 18_000) {
      return ["cerebras", "groq", "nvidia", "gemini", "mistral", "fireworks"];
    }
    return ["nvidia", "gemini", "mistral", "cerebras", "groq", "fireworks"];
  }

  if (hint === "prompt_optimize") {
    return ["groq", "gemini", "cerebras", "mistral", "nvidia", "fireworks"];
  }

  return ["gemini", "groq", "mistral", "cerebras", "nvidia", "fireworks"];
}

function localProviderHealth(name) {
  return HEALTH.get(name) || { failures: 0, cooldownUntil: 0 };
}

function cooldownRequest(name) {
  return new Request(COOLDOWN_CACHE_PREFIX + encodeURIComponent(name), {
    method: "GET"
  });
}

async function providerHealth(name) {
  const local = localProviderHealth(name);
  if (local.cooldownUntil > Date.now()) return local;

  if (typeof caches === "undefined" || !caches.default) return local;

  try {
    const cached = await caches.default.match(cooldownRequest(name));
    if (!cached) return local;

    const state = await cached.json();
    const normalized = {
      failures: Number(state?.failures) || 0,
      cooldownUntil: Number(state?.cooldownUntil) || 0
    };

    if (normalized.cooldownUntil > Date.now()) {
      HEALTH.set(name, normalized);
      return normalized;
    }

    await caches.default.delete(cooldownRequest(name));
  } catch {}

  return local;
}

function parseRetryAfter(response) {
  const raw = response.headers.get("retry-after");
  if (!raw) return 0;

  const seconds = Number(raw);
  if (Number.isFinite(seconds) && seconds >= 0) {
    return Math.min(6 * 60 * 60 * 1000, seconds * 1000);
  }

  const when = Date.parse(raw);
  if (Number.isFinite(when)) {
    return Math.max(0, Math.min(6 * 60 * 60 * 1000, when - Date.now()));
  }

  return 0;
}

async function markSuccess(name) {
  HEALTH.set(name, { failures: 0, cooldownUntil: 0 });

  if (typeof caches === "undefined" || !caches.default) return;
  try {
    await caches.default.delete(cooldownRequest(name));
  } catch {}
}

async function markFailure(name, status, retryAfterMs = 0) {
  const current = await providerHealth(name);
  const failures = Math.min(8, current.failures + 1);

  let cooldownMs;
  if (status === 429) {
    cooldownMs = retryAfterMs || 15 * 60 * 1000;
  } else if ([401, 402, 403, 404].includes(status)) {
    cooldownMs = 6 * 60 * 60 * 1000;
  } else if (status === 408 || status === 0) {
    cooldownMs = Math.min(2 * 60 * 1000, 20_000 * failures);
  } else if (status >= 500) {
    cooldownMs = Math.min(5 * 60 * 1000, 15_000 * Math.pow(2, failures - 1));
  } else {
    cooldownMs = 60 * 60 * 1000;
  }

  const state = {
    failures,
    cooldownUntil: Date.now() + cooldownMs
  };
  HEALTH.set(name, state);

  if (typeof caches === "undefined" || !caches.default) return;

  try {
    const ttlSeconds = Math.max(1, Math.ceil(cooldownMs / 1000));
    await caches.default.put(
      cooldownRequest(name),
      new Response(JSON.stringify(state), {
        headers: {
          "content-type": "application/json",
          "cache-control": `public, max-age=${ttlSeconds}`
        }
      })
    );
  } catch {}
}

function providerBody(name, provider, input, messages, hint) {
  const maxTokens = Math.round(
    clampNumber(input.max_tokens ?? input.max_completion_tokens, 128, 16_384, hint === "chat" ? 2_048 : 8_192)
  );
  const temperature = clampNumber(input.temperature, 0, 1, hint === "chat" ? 0.3 : 0.15);

  const body = {
    model: provider.model,
    messages,
    stream: false,
    temperature,
    max_tokens: maxTokens
  };

  if (name === "nvidia") {
    body.reasoning_effort = hint === "chat" || hint === "prompt_optimize" ? "low" : "high";
    body.clear_thinking = true;
  } else if (name === "groq") {
    body.reasoning_effort = hint === "chat" || hint === "prompt_optimize" ? "low" : "high";
  } else if (name === "cerebras" && provider.model === "zai-glm-4.7") {
    body.clear_thinking = true;
  }

  return body;
}

async function callProvider(name, provider, apiKey, input, messages, hint) {
  const controller = new AbortController();
  const timeoutMs =
    hint === "prompt_optimize" ? 8_000 :
    hint === "chat" ? 16_000 :
    hint === "repo_code" ? 55_000 :
    35_000;

  const timer = setTimeout(() => controller.abort(), timeoutMs);

  try {
    const response = await fetch(provider.url, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "accept": "application/json",
        "authorization": `Bearer ${apiKey}`,
        "user-agent": "H-AGENT-AI-Router/1.0"
      },
      body: JSON.stringify(providerBody(name, provider, input, messages, hint)),
      signal: controller.signal
    });

    const raw = await response.text();

    if (!response.ok) {
      await markFailure(name, response.status, parseRetryAfter(response));
      return {
        ok: false,
        status: response.status
      };
    }

    let payload;
    try {
      payload = JSON.parse(raw);
    } catch {
      await markFailure(name, 502);
      return { ok: false, status: 502 };
    }

    const content = normalizeContent(payload?.choices?.[0]?.message?.content);
    if (!content) {
      markFailure(name, 502);
      return { ok: false, status: 502 };
    }

    await markSuccess(name);
    return {
      ok: true,
      payload: {
        id: typeof payload.id === "string" ? payload.id : `h-agent-${Date.now()}`,
        object: "chat.completion",
        created: Number(payload.created) || Math.floor(Date.now() / 1000),
        model: "auto",
        choices: [
          {
            index: 0,
            finish_reason: payload?.choices?.[0]?.finish_reason || "stop",
            message: {
              role: "assistant",
              content
            }
          }
        ],
        usage: payload?.usage || undefined
      }
    };
  } catch (error) {
    await markFailure(name, error?.name === "AbortError" ? 408 : 0);
    return {
      ok: false,
      status: error?.name === "AbortError" ? 408 : 0
    };
  } finally {
    clearTimeout(timer);
  }
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method === "GET" && url.pathname === "/health") {
      const configured = Object.values(PROVIDERS)
        .filter((provider) => typeof env[provider.envKey] === "string" && env[provider.envKey].trim())
        .length;

      return json({
        ok: true,
        configuredProviders: configured
      });
    }

    if (request.method !== "POST" || url.pathname !== "/chat") {
      return json({ error: "Not found" }, 404);
    }

    if (!env.H_AGENT_APP_KEY) {
      return json({ error: "AI relay is not configured" }, 503);
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

    const hint = ALLOWED_HINTS.has(input?.route_hint) ? input.route_hint : "chat";

    let messages;
    try {
      messages = sanitizeMessages(input?.messages);
    } catch (error) {
      const code = String(error?.message || "");
      if (code === "input_too_large") {
        return json({ error: "Input too large" }, 413);
      }
      return json({ error: "Invalid messages" }, 400);
    }

    const chars = totalChars(messages);
    const order = routeOrder(hint, chars);

    const configured = order.filter((name) => {
      const provider = PROVIDERS[name];
      return typeof env[provider.envKey] === "string" && env[provider.envKey].trim();
    });

    if (configured.length === 0) {
      return json({ error: "No AI providers configured" }, 503);
    }

    const now = Date.now();
    const states = await Promise.all(
      configured.map(async (name) => [name, await providerHealth(name)])
    );
    const ready = states
      .filter(([, state]) => state.cooldownUntil <= now)
      .map(([name]) => name);

    if (ready.length === 0) {
      return json({ error: "AI providers cooling down" }, 503, {
        "retry-after": "30"
      });
    }

    for (const name of ready) {
      const provider = PROVIDERS[name];
      const result = await callProvider(
        name,
        provider,
        env[provider.envKey].trim(),
        input,
        messages,
        hint
      );

      if (result.ok) {
        return json(result.payload, 200);
      }
    }

    return json({ error: "AI providers temporarily unavailable" }, 503, {
      "retry-after": "30"
    });
  }
};
