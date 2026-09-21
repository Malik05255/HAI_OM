# H AGENT secure AI router

H AGENT uses one server-side Cloudflare Worker as the AI entry point. The Android app never receives the six provider API keys.

## Providers

The router can use these providers when their corresponding GitHub secret is configured:

| Provider | GitHub secret | Default model |
| --- | --- | --- |
| NVIDIA NIM | `NVIDIA_API_KEY` | `z-ai/glm-5-3` |
| Google Gemini | `GEMINI_API_KEY` | `gemini-3.8-flash` |
| Mistral | `MISTRAL_API_KEY` | `mistral-medium-latest` |
| Cerebras | `CEREBRAS_API_KEY` | `zai-glm-4.7` |
| Fireworks | `FIREWORKS_API_KEY` | `accounts/fireworks/models/glm-5p3` |
| Groq | `GROQ_API_KEY` | `openai/gpt-oss-120b` |

Cloudflare deployment also uses the existing `CLOUDFLARE_API_TOKEN`,
`CLOUDFLARE_ACCOUNT_ID`, and `H_AGENT_IMAGE_APP_KEY` secrets.

The provider keys are uploaded with `wrangler secret put`. They are not written
to the repository, Android BuildConfig, APK, or application logs.

## Automatic routing

The client sends a route hint rather than a provider or model name:

- `chat`: normal fast conversation.
- `code`: coding questions without a repository-sized context.
- `repo_code`: repository analysis and code modification.
- `prompt_optimize`: short prompt rewriting.

The Worker chooses a provider based on the task and input size. It tries one
provider at a time to preserve free quotas.

Failures automatically trigger failover. Rate limiting, exhausted credit,
authorization/model availability errors, timeouts, network failures, and 5xx
responses put that provider into a temporary cooldown. The next eligible
provider is then tried.

Cooldown state is also written to the Cloudflare Cache API, so a provider that has exhausted its quota is not retried on every fresh Worker isolate. The in-memory map remains the fast local path.

If every authenticated provider is unavailable, the Android chat client falls
back to the existing Kilo/Dahl free path. The remote GitHub coding agent falls
back to its existing OmniRoute free providers.

## Android security boundary

Only the Cloudflare relay URL and the existing relay/app access token are built
into the Android app. A static token shipped in an APK can be extracted and
therefore must never be treated as a provider credential. Its purpose is only
to reduce casual relay abuse.

The actual NVIDIA, Gemini, Mistral, Cerebras, Fireworks, and Groq API keys exist
only as server-side Cloudflare secrets.

For a public production service, add device/app attestation or a short-lived
token exchange in front of the Worker.

## Remote coding agent

The H AGENT GitHub Actions runtime reads:

- repository variable `H_AGENT_AI_PROXY_URL`
- repository secret `H_AGENT_AI_RELAY_KEY`

For the HAI_OM repository, the Android CI automatically stores the deployed
router URL in `H_AGENT_AI_PROXY_URL`. The runtime also accepts the existing
`H_AGENT_IMAGE_APP_KEY` secret as a fallback relay key.

When H AGENT is installed into another repository, that repository can use the
secure six-provider router by setting the URL variable and one relay-key secret.
If they are absent, the remote coding workflow remains functional through its
existing free fallback.

## Partial configuration

The router does not require all six provider keys to be present. Any configured
subset is used. Adding a missing provider later only requires adding its GitHub
secret and rerunning the Android workflow; no Android source-code change is
required.


## Deployment readiness probe

After the Worker is deployed, CI calls the authenticated `POST /probe` endpoint.
The probe tries configured providers in order until one returns a valid response.
It uses a tiny request and stops after the first healthy provider, minimizing free
quota usage. CI treats the AI router as ready only when at least one configured
provider actually responds.
