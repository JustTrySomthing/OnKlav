# Account connection (working source)

Scope: these are optional guest HTTPS/JMAP/account APIs, distinct from the built-in Email app’s Yahoo/Google/private-domain app-password setup. See [current host](CURRENT-HOST.md).
Current target: [OnKlav 0.100.42/code151, guest protocol96](CURRENT-HOST.md). Phase-specific “working/unreleased” wording below denotes preview qualification, not absence from this APK.
Declare network.http, accounts.connect and signed provider metadata. Installation
only reviews the policy; both capabilities must be granted by the user. Call
`accounts::connect("provider-id", "connected")` from a direct button tap. The
system browser performs public-client OAuth authorization-code sign-in with PKCE
S256. Register `${applicationId}.oauth://oauth/callback` with the provider, using
the actual host package (the diagnostic package has its own callback scheme).

A successful reply contains only account (opaque UUID), provider and label.
`accounts::list("listed")` returns up to eight such records for this app/publisher.
The label identifies the declared provider, not a verified user identity. ID tokens
are ignored. No token, verifier or authorization code is returned to guest code.

Each signed provider declares id, label, authorization_endpoint, token_endpoint,
client_id, scopes and api_origins. Use a registered public client, never a client
secret. Endpoints are fixed public HTTPS paths under signed network origins.
Providers requiring another callback scheme, confidential client secrets or an
unimplemented protocol are not supported by this connection flow.

Connection has a two-minute browser timeout, four concurrent flows and ten attempts
per app/publisher per minute. Token exchange is one-shot with no redirects/cookies,
30-second timeout and 32KiB response bound. Returned scopes cannot exceed the signed
request. Tokens are encrypted with host-owned Android Keystore keys in a distinct
no-backup account namespace. Policy changes invalidate existing handles. Removal
clears credentials even when documents are retained; revoking either account or network access clears
them too. No software-key fallback or secret export is available.

`accounts::refresh(handle, "refreshed")` rotates host-protected tokens. Refresh
preserves an omitted refresh token and commits only if the original account record
still exists unchanged. It cannot recreate a disconnected account. Known expiry
also triggers refresh before an authenticated resource request;401 responses and
mutating requests are not replayed automatically.

`accounts::authenticated(http::get(url,"loaded"), handle)` asks the host to attach
Bearer credentials only at that provider's signed api_origins. The guest may not
supply Authorization/Cookie headers. Grants and account binding are rechecked at
network setup and while reading. Sign-out cancels active credential use.

`accounts::disconnect(handle,"signed_out")` deletes local credentials first. An
optional signed revocation_endpoint receives a bounded best-effort POST. The
public reply distinguishes disconnected from revocation_submitted. A failed remote
revocation never restores a local account. A changed provider policy must not
receive an old token; sign out locally and connect the changed provider again.

Physical browser/provider/Keystore acceptance remains required. Host tests use
fake browser/token transports and real scoped encryption. These primitives alone
do not implement email transports or a mailbox client.
