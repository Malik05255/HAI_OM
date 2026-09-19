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

Create one OAuth App in the GitHub account that owns OM and enable **Device Flow** in that app's settings.

Configure the repository Actions variable:

- `GITHUB_OAUTH_CLIENT_ID`

No client secret is required for GitHub Device Flow.

## Requested OAuth scopes

- `repo`
- `workflow`
- `read:user`

The access token returned by GitHub after approval is stored through the existing encrypted SecretStore. Users never create, paste, or manage the token manually.
