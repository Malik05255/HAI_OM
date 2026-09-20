# GitHub Device Flow setup for H AGENT

H AGENT uses GitHub OAuth Device Flow for account linking.

## End-user flow

1. Tap **ربط GitHub بالكود** in H AGENT.
2. H AGENT requests a short user code from GitHub.
3. The app displays the code.
4. Tap **نسخ الكود وفتح GitHub**.
5. Enter the code on GitHub and approve the requested permissions.
6. H AGENT polls GitHub until authorization completes.
7. H AGENT loads the accessible repositories.
8. Select a repository and continue from chat.

There is no manual Personal Access Token flow in the UI.

## OAuth configuration

The Android build contains a public OAuth Client ID suitable for Device Flow. A client secret is not embedded and is not required for Device Flow.

The Client ID can be overridden with:
- environment variable: `GITHUB_OAUTH_CLIENT_ID`
- Gradle property: `-PGITHUB_OAUTH_CLIENT_ID=...`
- optional workflow input: `github_oauth_client_id`

## Requested scopes

H AGENT requests:
- `repo`
- `workflow`
- `read:user`

The returned access token is stored locally using the encrypted SecretStore.
