# GitHub OAuth setup for OM

OM supports a one-button GitHub connection flow using OAuth Authorization Code + PKCE.

## One-time developer setup

Create one OAuth App in the GitHub account that owns OM:

- Application name: `OM Mobile`
- Homepage URL: `https://github.com/Malik05255/HAI_OM`
- Authorization callback URL: `om://github-auth`

Then configure the repository:

- Actions variable: `GITHUB_OAUTH_CLIENT_ID`
- Actions secret: `GITHUB_OAUTH_CLIENT_SECRET`

The Android workflow injects both values only at build time. They are not committed to the repository.

## User flow

1. Tap **ربط GitHub** in OM.
2. GitHub opens using the account already signed in to the browser.
3. Review permissions and tap **Authorize**.
4. GitHub redirects to `om://github-auth`.
5. OM exchanges the temporary code using PKCE, stores the returned token with the existing encrypted SecretStore, loads all accessible repositories, and opens the project picker.
6. Select a repository and continue in chat.

## Requested OAuth scopes

- `repo`
- `workflow`
- `read:user`

The manual fine-grained-token path remains only as a fallback when OAuth credentials are not configured in the build.
