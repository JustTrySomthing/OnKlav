# Live provider qualification

The public Mail reference project currently has example.org JMAP/OAuth endpoints
and an example public-client ID. Geocoding and routing also use example.org;
they are not configured live services. The OSM raster endpoint is configured.
No app account from desktop unit-test fixtures is a real user/provider account.

A real mail qualification needs provider authorization/token/revocation URLs,
a registered public OAuth client ID and redirect scheme for the chosen host
package, exact API/upload/download origins, requested scopes, and a dedicated
test account plus an authorized recipient. Sign-in must use the provider's
consent flow. Never put passwords, refresh tokens or access tokens in source,
logs or chat. The currently configured callback is
`<host applicationId>.oauth://oauth/callback`; debug and diagnostic IDs differ.
Actual browser/account-picker and Android Keystore acceptance also needs the
running Android host. JVM fixtures do not establish those device guarantees.

After configuration, check login/cancel, refresh, revoked/expired credentials,
mailbox/identity discovery, plain text and attachment upload/download/export,
review/cancel/send exactly once to the designated test recipient, acknowledged
and ambiguous outcomes, restart/reconciliation and explicit duplicate-risk retry.
Inspect provider Sent/draft/submission state separately; acceptance is not delivery.
Disconnect must remove local access; test provider revocation where supported.

Search/route qualification needs actual configured endpoints with the documented
Nominatim-jsonv2/OSRM-GeoJSON profiles, allowed usage/rate limits and attribution.
Do not silently substitute public demo services. Test one explicit query/result
and a short route, invalid/empty/error replies, credits and permission revocation.

`python3 scripts/check_live_map.py` is an explicit opt-in single OSM world-tile
check. It builds a disposable signed reference Map package, then uses production
JNI authorization, Kotlin HTTPS, cache storage and native raster decoding. A
second load must use cache without another pending HTTP request, and revocation
must block raster access. There is no bulk/offline download or fake server reply.
Use the existing Android environment; no APK is assembled. Full-source compilation
must already have succeeded before the normal120-second focused check budget.
See integration-provider-verification.json for actual results and blocked gates.
