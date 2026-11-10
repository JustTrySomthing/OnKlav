# Current reconciliation — 0.65.0 source only

Use TODO.md and RESUME-0.65.0.md as the current status. Historical entries below
are checkpoint records and are not a fresh list of missing implementations.
Eight-app document backup is complete; retain the installed speech engine.
Profile sharing was not in the original local-only Profile. Current source adds
Drive authorization/upload/public links and recovery, shared speech Settings,
bounded Files duplicates, provider-first read updates, Browser Find counts and
complete per-profile clear, and YouTube Back/rotation behavior. Existing capture,
viewer, group history/reactions, Browser handoff and Walkie transport/recovery are
implemented. Phone/account/release qualification is still unclosed; no APK built.

## Historical records follow

# Backup continuation — 0.62.0 source only

Settings Backups now covers YouTube bookmarks/history.
See SETTINGS-PORT.md and RESUME-0.62.0.md for validation/merge evidence.
No APK was requested for this checkpoint; latest delivered is 0.61.0.

# YouTube — 0.14.0 SDK conversion

This checkpoint converts the next app only. The complete OnKlav rewrite and full
Inner Circle parity remain unfinished. Only OnKlav is an APK; YouTube is the
signed internal `YouTube.onklav-a` package, not a standalone Android app.

## Production implementation

`AppsDesktop youtube -> AppSdk.bundledApps -> SdkAppScreen/SdkAppModel -> JNI ->
Rust sdk::dispatch -> wasmi -> sdk/apps/youtube/main.wasm -> validated frames and
effects -> app-scoped Rust videos service or authorized Kotlin host component`.

The package contains its own Rust controller. It owns navigation commands,
Home/history/bookmark drawers, confirmations, item actions, page callback state,
current-video history suppression and host-effect continuations. The host does
not select a YouTube business-logic screen from the manifest. The obsolete trusted
BrowserScreen and its YouTube switch were removed. No original Java source,
classes, old app binary or embedded old application is used.

`core/src/videos.rs` owns durable, atomic per-package library storage. Kotlin/
Jetpack Compose supplies shared web, media-record, dialog and toolbar widgets.
`SdkVideoPolicy`, `SdkMediaScripts` and `SdkThumbnails` are host-owned provider
adapters that any authorized SDK package can use; selection is by validated
widget/provider contract, never an application-ID switch. Guests cannot provide
native JavaScript or Android objects. The fixed metadata/ad scripts are newly
written host code, not extracted legacy Java implementation.

## Reference behavior implemented

The Inner Circle 2.2.85 Apps entry opens its dedicated signed-out viewer, rather
than a Browser-style address bar. The rewritten viewer follows that layout:
Back/title/loading indicator and 48dp Home, History and Bookmarks controls; no
native address/search/bottom browser toolbar; landscape hides the header. Search
and ordinary navigation belong to the served YouTube page. Holding Home opens
the reset confirmation. Drawers enter from the left per Charles's direction.

History uses canonical 11-character video identities, newest-first deduplication,
1,000 entries, sanitized 160-UTF-16-unit titles and 60-second repeated-visit
suppression. Home/non-video pages are not history entries. Bookmarks support
videos and allowed YouTube pages, with a 200-entry limit. Both survive reopening.
Lists page in groups of 20, show local-calendar history headings, 96x54 rounded
thumbnails, 14sp/two-line titles, 48dp removal controls and long-hold link copying.
Removal, clear-history and reset have confirmation/cancel paths. Clearing history
keeps bookmarks and suppresses the current video until navigation to another
video; epoch-checked callbacks reject stale native metadata.

The host restricts top-level provider navigation to exact signed-out YouTube
hosts and blocks account/sign-in paths. Account links show Keep Watching / Open
YouTube App; the latter is an explicit brokered user action and reports a missing
YouTube app. Offsite links require an explicit Open in Browser action.

Websites use the host-derived per-package AndroidX WebView profile. Native
fullscreen custom views are supported and disposed with the web surface. Leaving
the foreground pauses/destroys that surface and requests media pause. File and
content access, mixed content, service workers, camera/microphone/location and
native file chooser are disabled. Clipboard access checks live grants. Fixed
best-effort ad handling blocks known ad hosts/pagead endpoints, hides selected ad
markup, presses visible skip controls, and changes playback only while the player
reports an ad; original mute/rate are restored afterward and on cleanup.

Thumbnails use only the derived i.ytimg.com video endpoint, no redirects, public
DNS addresses, 4-second connect/read and 8-second total timeouts, two concurrent
workers, 98,304-byte input, <=640x480 decode bounds, 3MiB memory and per-app
2MiB/40-file disk caches. The queue is bounded by the validated frame size and
20-row library pages, not the original explicit 12-job queue. Downloading needs
`network.thumbnails`; cached display still needs a live scoped storage session.
Removal with private-data deletion and explicit history/reset clear scoped image
caches. Revocation/removal cancels active image calls. No arbitrary image URL or
native network handle is supplied to the guest.

## Verification for this APK

45 Rust and 36 Kotlin/framework-host tests pass. The YouTube test executes the
actual signed Wasm package and checks library/bookmark persistence, account
notices without automatic external launch, explicit copy effects, scoped image
authorization, clear/cancel/reset, stale callbacks, current-video suppression and
revocation. Kotlin tests check provider/ad boundaries, local history headings,
thumbnail size/permission checks and the actual desktop-to-viewer UI, bookmark
reopen, history drawer, clear cancel and held-Home reset dialog. The provider in
framework-host tests lacks MULTI_PROFILE: it displays the safe fallback. Tests do
not claim to have rendered YouTube, streamed media or opened another real app.

Both Android Rust ABIs were rebuilt from current source. APK checks cover ZIP,
retained Android signer, one host application, exact native bytes, 16KiB alignment,
no legacy classes/nested APKs and all five bundled SDK package/Wasm hashes. See
verification.json, native-provenance.json and apk-verification.json.

## Remaining acceptance and parity gaps

- Real handset website/profile support, playback/audio/fullscreen/orientation,
  background recovery, sign-in redirects, changing ad markup and accessibility
  need acceptance. No measured speed/stability improvement is claimed.
- Original offsite links opened the in-platform Browser. This version's explicit
  Open in Browser uses Android's external-browser intent; internal app-to-app
  navigation is still a missing SDK broker operation.
- Reset clears scoped cookies, web storage, view history, Rust history and image
  cache, retaining bookmarks. It does not promise a full per-profile HTTP/disk
  cache purge. Native profile cleanup on uninstall, async-reset/background races
  and exact reset behavior require more lifecycle work. Never use a global cache
  deletion that destroys other SDK apps' website data.
- Automatic fullscreen request on rotation, exact original glyph geometry,
  drawer width/spacing/animations, thumbnail scrolling/cache pressure and a
  dedicated private-viewer mode need reference/handset follow-up.
- Earlier rewritten Kotlin YouTube preferences are not automatically adopted;
  original Inner Circle private data is never read or deleted.

Next app is Annotation. Do not begin it until Charles requests the next chunk.
Social and Walkie-Talkie also remain; carrier MMS, game ports and broader platform
work remain listed separately in REWRITE-STATUS.md.

## 0.25.0 — automatic message YouTube previews

Privacy's default-off Automatic YouTube Previews switch is connected to shared
Messages/Groups video cards. Explicit Play stays available when thumbnails are
off. Bounded validated-ID CDN requests, cancellation/cache invalidation and an
internal signed YouTube SDK v2 handoff replace the disabled control. Existing
YouTube v1 installs use a reviewed Update Included App action. No silent grants,
APK, real provider/playback/account/carrier acceptance. See RESUME-0.25.0.md and
source-verification.json; other Settings services and handset checks stay open.

## 0.42.0 — design, activity selection and cleanup

- [x] MMS validation precedes decoded-attachment publication; partial writes roll
      back only owned files, and cleanup failures are surfaced.
- [x] Durable app-removal intent, idempotent core cleanup retry, one-time native
      profile rotation, retryable thumbnail cleanup and guarded startup.
- [x] Individual group member selection, original activity tiles, internal Walkie
      handoff, modal game catalog and return to the same conversation.
- [x] Shared original SDK toolbar/navigation glyphs and rounded fields, selected
      conversation tabs, RSS v2 source-first cards and compact search/refresh row.
- [ ] Friend-game invitation/verification/status and play await original game
      ports/protocol; the picker alone does not close that work.
- [ ] Handset pixel/frame/large-font/accessibility, live carrier/dual-SIM/provider
      and two-phone voice acceptance; original bundled speech-engine/voice parity.
- [ ] Independent security/lifecycle review and the remaining wider migration.

148 Kotlin/framework-host and 145 Rust checks passed (293 total). One verified
host APK, code49. MMS/default-role remain disabled. See RESUME-0.42.0.md.

## 0.56.0 — shared web reliability continuation

YouTube shares Browser's retired-renderer guards, Reload surface recovery and
current-view/sequence/navigation/grant checks around asynchronous cookie reset.
Fullscreen closes on renderer loss. Ordinary Android Back still exits this viewer;
fullscreen consumes Back first. This does not certify live YouTube playback, ad
markup, provider profiles or a complete HTTP-cache purge. Internal Browser handoff
and native profile-removal integration were already implemented in 0.41/0.42;
the historical missing-service notes above are not a fresh implementation backlog.
