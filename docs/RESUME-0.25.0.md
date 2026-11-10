# Resume 0.25.0 — message YouTube previews and internal playback handoff

Source-only 0.25.0-dev/code32. Signed YouTube SDK package v2, same retained
publisher. No APK/mobile ABI build. Last verified host APK stays 0.14.0.

User requested continued Settings work with clear ETAs and progress. Current
bounded item is Automatic YouTube Previews, previously a disabled Privacy switch.
Initial estimate was 15–25 minutes including checks. Give concrete item-level
estimates and updates; do not imply an entire remaining platform ETA is known.
Do not ask to reapprove routine work or build per-item APKs.

Reference: original Inner Circle 2.2.85 PrivacyUi, YouTubeLinks and YouTubeCard,
read-only. New Kotlin implementation, Rust SDK host validation, signed Rust/Wasm
controller. No old Java or app binary is copied into this port.

Messages and Groups use the shared message-card component. Recognize at most four
unique YouTube IDs in 10,000 characters, preserve bounded start times, normalize
links to an internal canonical HTTPS target. Remote image fetches use only a
fixed CDN plus the validated ID, never message-provided hosts/paths. Default off:
no thumbnail fetch/cache use. Play stays visible and is an explicit user action.

Privacy switch commits the preference and invalidates queued/in-flight results,
cancels active calls and evicts the 2 MiB memory-only cache. Work is bounded to two
concurrent loads, 8-second call timeout, 96 KiB input and constrained image decode.
No redirects, cookies, WebView preview, arbitrary URL fetch or disk image cache.
Background lifecycle disables new fetches/cancels active calls; stale generations
cannot cache or repaint after off/on. Permission belongs to the host messaging UI,
separate from the YouTube SDK package's revocable capabilities.

Play routes through private Compose callbacks to the installed YouTube SDK app,
not exported intent extras, an Android YouTube APK or a browser fallback. SDK
host video_open validates fixed app/session, current grants, package version,
strict ID and 0–86400s before delivering a host-only event to signed Wasm. The
controller increments its epoch and loads the canonical address; initial home
WebView creation waits until this handoff finishes. History is not fabricated
before a real viewer navigation callback. WebView still requires user interaction
for media playback and its existing isolated, signed-out policy remains.

Previously installed YouTube v1 needs a normal signed update. Settings > Apps >
YouTube now offers Update Included App when the bundle has a newer version. The
existing review/installer verifies author/version and presents declared grants,
preserving only current grants by default. No silent update or permission grant.
Other apps and the retained publisher/debug signing keys remain unchanged.

Verification uses injected image bytes and callbacks, real signed Wasm/JNI and
Compose interaction tests. No public thumbnail request, live webpage/playback,
account or carrier acceptance was performed. Physical phone visual/playback and
provider availability remain unverified. Current counts/logs: source-verification.

Next Settings gaps: Drive account/uploads, full SMS/MMS history backups, original
bundled speech voices, profile sharing and external game packages/runtime. Full
message backup export is the next item that can be developed without account
credentials; provider-write/default-role acceptance remains gated. Walkie and
remaining platform gaps are unchanged. See TODO and SETTINGS-PORT.

Build: restore env.sh and /root/.cargo/env; use retained publisher outside this
tree. Signed SDK build must precede build_native.py --host-only, then
check_android.py --tests-only. Preserve source/SDK/TODO identities. No APK unless
requested; a later phone build must rebuild both Android ABI libraries first.

Final current verification: 87 Rust +65 Kotlin host tests passed (152), zero failures.
Full app/shared host invocation green; current runtime-class freshness verified.
YouTube v2 retains publisher; all other SDK package hashes unchanged.
No APK/mobile ABI rebuild, real provider/page/playback, account or carrier acceptance.
