# Cloudflare image generation for H AGENT

H AGENT 0.12.0 uses a Cloudflare Worker as a server-side relay to Workers AI.

## Models

- Primary: `@cf/black-forest-labs/flux-2-dev`
- Fallback: `@cf/black-forest-labs/flux-2-klein-9b`

The Android APK never contains the Cloudflare Account ID or Cloudflare API token.

## Required GitHub Actions secrets

Add these repository secrets:

- `CLOUDFLARE_ACCOUNT_ID`
- `CLOUDFLARE_API_TOKEN`
- `H_AGENT_IMAGE_APP_KEY`

The Cloudflare token is used only by GitHub Actions to deploy the Worker. The app receives only the Worker URL and the limited app relay key.

The Cloudflare API token used for deployment should have the permissions required to deploy Workers and use Workers AI for the account.

`H_AGENT_IMAGE_APP_KEY` should be a long random value. It protects the relay from casual public abuse, but because it is present in the APK it must not be treated as an account-level secret.

## Automatic deployment

On pushes to `main`, `.github/workflows/android.yml` deploys `cloudflare/image-worker` first, captures the `workers.dev` URL, and injects that URL into the Android build.

If the Cloudflare secrets are missing, the Android project still compiles but image generation reports that the new image service is not configured.

You may also set repository variable `H_AGENT_IMAGE_PROXY_URL` to an already deployed compatible Worker URL.
