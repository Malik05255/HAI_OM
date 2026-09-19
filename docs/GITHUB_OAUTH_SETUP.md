# GitHub Device Flow setup for OM

OM uses GitHub OAuth Device Flow for account linking.

## End-user flow

1. Tap **ربط GitHub بالكود** in OM.
2. OM requests a short user code from GitHub.
3. OM shows the code, for example `ABCD-EFGH`.
4. Tap **نسخ الكود وفتح GitHub**.
5. Enter/paste the code at GitHub and approve the requested permissions.
6. OM polls GitHub in the background until approval is complete.
7. OM loads all accessible repositories and opens the project picker.
8. Select a repository and continue development in chat.

There is no manual Personal Access Token flow in the UI.

## One-time developer setup

GitHub Device Flow always requires a Client ID. A client secret is not required.

Create one OAuth App or GitHub App for OM and enable **Device Flow** in its GitHub settings. Then copy its **Client ID**.

For normal CI builds, configure the repository Actions variable:

- `GITHUB_OAUTH_CLIENT_ID`

Path in GitHub:

**Repository > Settings > Secrets and variables > Actions > Variables > New repository variable**

Use:

- Name: `GITHUB_OAUTH_CLIENT_ID`
- Value: the Client ID from the GitHub app registration

The workflow now refuses to publish an APK when this value is empty, so a broken build with a disabled GitHub button cannot be released again.

## Manual build fallback

The Android workflow also supports **Run workflow** and accepts `github_oauth_client_id` as an input. This can be used to build and publish from `main` without first storing the repository variable.

## Local builds

You can provide the Client ID either as an environment variable:

`GITHUB_OAUTH_CLIENT_ID=...`

or as a Gradle property:

`-PGITHUB_OAUTH_CLIENT_ID=...`

## Requested OAuth scopes

For the OAuth App Device Flow implementation OM requests:

- `repo`
- `workflow`
- `read:user`

The access token returned by GitHub after approval is stored through the existing encrypted SecretStore. Users never create, paste, or manage the token manually.
