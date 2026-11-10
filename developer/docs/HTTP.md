# Working-source HTTPS API — task4 networking stage

Current target: [OnKlav 0.100.42/code151, guest protocol96](CURRENT-HOST.md). Phase-specific “working/unreleased” wording below denotes preview qualification, not absence from this APK.
Bounded HTTPS and the account broker are implemented in the current host. Account/provider configuration and real service acceptance are separate.
Use the current reference host described in CURRENT-HOST.md. Existing
feed/web services retain their separate capabilities and restrictions.

## Declare destinations and obtain consent

```json
{
  "capabilities": ["network.http"],
  "network": {"origins": ["https://api.example.com"]}
}
```

These fields belong in the usual signed app manifest, alongside its identity,
version and runtime fields. Declare1–8 unique lowercase HTTPS DNS origins without
paths, queries, credentials, wildcard subdomains or port suffixes. Requests use
port443. Domain names may resolve to public IPv4/IPv6; literal IP origins, local
DNS names and nonpublic DNS results are rejected. Existing public-address DNS
checks and platform TLS certificate validation remain enabled.

The new-project tool accepts `--origin https://api.example.com` and adds the
capability/policy before creating the project. Invalid policy fails before it
leaves a partial project. Install/update and permission screens display the
declared origins. Declaring them is not a grant; the user must allow network.http.
Origins are bound to signed app/publisher/session authorization, not guest-supplied
native packets. Updates/revocation invalidate old sessions and cancel active calls.

## Shared API

`http::get(url, then)`, `http::request(url, method, headers, body, then)` and
`http::post_json(url, data, then)` create the `http.request` effect. The reply value
is `{status,text,headers}`. `http::parse_json(value)` parses its bounded text as
strict JSON without duplicating an automatic native parsed/raw-body pair.
Inspect status first:4xx/5xx are HTTP results, not successful application actions.
Transport, policy, size and cancellation failures use normal failed service replies.

- Methods: GET, HEAD, POST, PUT, PATCH, DELETE. GET/HEAD cannot carry a body.
- Request body: up to64KiB UTF-8; encoded arguments up to140000 bytes.
- Response: up to128KiB supported UTF-8 text; binary/large downloads are later
  download/media services. Unbounded bodies and malformed UTF-8 are rejected.
- Up to8 request headers from Accept, Accept-Language, Content-Type,
  If-None-Match, If-Modified-Since and X-Request-ID; values up to512 ASCII bytes.
- No arbitrary Authorization, Cookie, Proxy, Host, connection or API-key headers.
  Use the [account broker](ACCOUNTS.md) for authenticated requests through
  host-managed credential references. Keep secrets out of URLs, ordinary state
  and diagnostic messages.
- Response headers are limited to content-type, etag, last-modified, retry-after,
  cache-control and a bounded Location whose resolved origin is also declared.
  Set-Cookie and authentication headers are not returned or stored.

The host adds a non-user-specific app ID User-Agent. No cookie jar or shared disk
cache is configured. Conditional headers and app-private caching are available;
OnKlav does not invent a provider cache policy. The native networking client and
the web component do not share browser sessions.

## Bounded execution and request lifecycle

At most2 requests per app/publisher are admitted, and4 globally perform network work;
the per-owner budget is60 requests per minute. Waiting for a global slot is
cancellable. The client uses10-second connect/read timeouts and a30-second call
timeout; queue time is additional. One host request may be pending per guest
session under the existing broker rules.

Redirects are returned for explicit handling and checked again on any new request;
the client does not follow them automatically. Connection retry is disabled and
the platform adds no retry loop. Mutating request bodies are marked one-shot to
prevent their automatic retransmission. Applications must handle uncertain
server outcomes, and use provider idempotency keys/protocols where applicable.
Do not automatically retry writes merely because the response was interrupted.

Calls are registered before the final authorization check. Close, update and
permission changes can therefore cancel them even during setup. Coroutine
cancellation also cancels the underlying call. Authorization is checked during
body reading and before releasing the response. Provider exception details,
full request URLs and response bodies are not copied into platform failure logs.

Tests cover signed-policy validation, JNI origin binding, revocation, request and
response bounds, credential/cookie filtering, per-app concurrency and sanitized
failures with an injected transport. They do not claim live provider/TLS/OEM
qualification. Existing feed parsing/address checks and old-package bootstrap
cases are checked separately for compatibility.

## Account broker and provider qualification

System-browser OAuth with PKCE/state validation, registered callback routing,
host-only encrypted account/token bindings, refresh/sign-out/revocation and
origin-bound authenticated requests are implemented; see [account connection](ACCOUNTS.md). Provider client
registration and approval requirements cannot be bypassed by this SDK. This
networking stage alone does not make a complete email/account platform.

Working account-provider declarations may be included under `network.accounts`.
Each entry contains `id`, `label`, `authorization_endpoint`, `token_endpoint`,
`client_id`, `scopes`, and `api_origins`. Up to four providers are supported in
metadata. Public client IDs are configuration, never client secrets. Endpoint
origins and API origins must already be in signed `network.origins`; endpoints
use fixed HTTPS paths without query strings. Duplicate IDs/scopes and unknown
fields fail validation. The installer displays provider scopes and API services.

This metadata does not grant account access or enable sign-in. OAuth browser
handling, a separate account consent/grant, protected token handles and authenticated
requests are provided by the account broker. Authorization/Cookie headers remain forbidden in the
ordinary HTTP API. No credential belongs in guest state or the signed package.
