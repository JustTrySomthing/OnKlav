# Network handoff and YouTube download recovery — 0.100.0

User report: radio and podcasts failed after leaving Wi-Fi, and YouTube
downloads reported absent audio/480p/1080p or an expired link.

## Changes

* The public-address filter now recognizes the well-known NAT64 prefix used by
  IPv6 mobile networks. It validates the embedded IPv4 destination, continuing
  to reject loopback, private and other prohibited addresses. This is a
  plausible cause of the report; the handset's DNS replies were not captured.
* The shared media service observes Android's validated default Internet route.
  On a route change it rebuilds a radio/podcast connection, retaining podcast
  position. It checks current ownership/grants again, respects Pause/Stop,
  local downloads, sleep timers and service teardown, and does not force Wi-Fi
  or mobile data. There is one reconnect per newly validated route, no polling
  or unbounded retry loop.
* A failed embedded browser page reloads after a newly validated route is
  available, subject to its existing page and permission checks.
* The YouTube download dialog resolves the current video ID through the pinned
  NewPipe Extractor v0.26.4 dependency. It lists real progressive-file heights
  and extracts audio without asking the user to start playback/change quality.
  Links are freshly resolved again immediately before transfer. Separate video
  and audio tracks use the existing Media3 composition/export pipeline.
* Audio output remains AAC/M4A; video output remains MP4/H.264. Existing shared
  Audio/YouTube and Video/YouTube destinations, progress, cancellation, storage
  limits, app grants and current-video checks remain in effect.

## Dependencies and distribution

The extractor and its runtime dependencies are pinned unmodified JARs under
android/app/libs. Their hashes, licensing notes and the extractor's corresponding
source JAR are under third_party/newpipe. APK input provenance now includes
module-local libraries. Preserve the GPL-3.0-or-later combined-distribution
requirements and corresponding source when distributing builds containing the
extractor. The About licenses text identifies the new dependencies.

## Acceptance limits

Host regression checks cannot establish Samsung/carrier handoff, native codec
conversion, or live YouTube/provider acceptance. This change does not guarantee
downloads for every video: private, restricted, live, DRM or non-progressive
sources may remain unavailable. It uses no WebView account credentials, external
app, server conversion service or embedded Python runtime.

No APK is requested or built in this turn. Preserve the last delivered 0.99.1
APK and all prior source, native libraries and signed included-app identities.

## Observed verification

11 focused local regression cases passed. The final production source compiled;
all 2,815 runtime classes match the compiled classes. The official Blender
video resolved 20 tracks and heights from 144p through 2160p, including 480p
and 1080p. The live transfer assertion failed because the environment returned
HTTP206 text/html with a Site Unavailable / Unable to access this site page
instead of media. A separate request confirmed the same replacement page.
This is not counted as a successful full download or handset acceptance.
The retired BaW_jenozKc fixture returned unavailable and oEmbed404; it was
replaced with the official video, whose oEmbed metadata succeeded.

Full lint was stopped at the120-second limit during lintAnalyzeDebug and was
not retried. It remains incomplete. Twelve existing checkpoint/build-input
regressions passed. No APK was built.
