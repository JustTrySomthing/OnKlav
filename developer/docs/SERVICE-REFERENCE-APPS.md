# Public service reference applications (working source)

These source-only projects use public `reference_apps` controllers, the same
onklav-ui crate as included apps. Existing projects, identities and shipping
packages are preserved. No APK or SDK release is produced.

| Project | Practical flow | Scope |
| --- | --- | --- |
| reference-weather | Manual saved place, cached current conditions, units, credits, explicit refresh/age check | Configured current profile; no GPS/forecast/polling |
| reference-listening | Signed-origin feed/stream/status URLs, RSS2 episodes, selected playback, Icecast metadata, shared controls | No discovery/HLS/ICY; URLs/episodes are session state |
| reference-map | Measured native viewport, dataset pan/data zoom, explicit visible-tile load or offline cache, stale/incomplete coverage, provider credits, search/result centering and route preview | Up to1024 logical pixels per axis/25 visible tiles; configured geocoder/router; no prefetch/bulk offline/turn-by-turn |
| reference-mail | Host connection, JMAP Session/account/mailbox/identity selection, all100 cached headers paged20 at a time, body, MIME import/private-file manager, attachment download/upload/export, multiple drafts/full review, uncertain-send recovery and Sent move | Bounded plain-text/MIME/JMAP profiles; example provider must be configured; no delivery guarantee |

Map raster tiles use OSM attribution/policy; geocoder/router and Mail use
example.org placeholders. Configure source URLs and signed manifest together.
No public geocoder/router is silently enabled. Discovered Session URLs do not
widen origins. Credentials stay in host accounts, never URL fields. Account
changes hide private mailbox content; explicit clear/delete removes retained data.

The map viewer reports its real measured logical viewport through a non-gestural
callback. Native arrow/data-zoom/drag operations recenter the dataset; explicit
Load fetches the new visible coverage. One local cache call returns all hits,
then misses are fetched/committed sequentially. Offline mode honors provider
policy. Controls/search/route live in a full-height drawer. Native viewport
callbacks do not grant tap authority to external effects.

Existing reader/map/media/mail/weather fixture projects remain available.
`workflow-wrap` creates a clearly labelled synthetic MIME file through real
private services, checks decoded content, and stops at upload/export pending.
Its scenarios never synthesize provider or Android success. Functional unit
tests use clearly synthetic callback fixtures; signed tests use the actual
verifier/interpreter/broker. Run selected scenarios, not all-project suites.

Source workflow gaps are closed for these bounded profiles. Actual provider
OAuth/HTTP/attachment/submission behavior, full Android integration, phone layout/
TalkBack/IME/process recovery and sustained performance/heap/security qualification
remain open. See docs/workflow-wrap-verification.json and EMAIL-CONTENT.md.
