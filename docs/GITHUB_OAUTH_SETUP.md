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

## OAuth configuration

The production Android build includes the public OAuth Client ID for the HAI OM OAuth App, so normal CI/release builds work without an Actions variable.

A client secret is not embedded and is not required for GitHub Device Flow.

For testing or replacing the OAuth App later, the Client ID can still be overridden with either:

- environment variable: `GITHUB_OAUTH_CLIENT_ID`
- Gradle property: `-PGITHUB_OAUTH_CLIENT_ID=...`
- optional `github_oauth_client_id` input when manually running the Android workflow

## Requested OAuth scopes

OM requests:

- `repo`
- `workflow`
- `read:user`

The access token returned by GitHub after approval is stored through the existing encrypted SecretStore. Users never create, paste, or manage a Personal Access Token manually.
