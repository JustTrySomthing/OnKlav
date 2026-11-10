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

# Backup continuation — 0.61.0

Settings Backups now covers RSS sources, offline articles, bookmarks, read marks and settings.
See SETTINGS-PORT.md and RESUME-0.61.0.md for merge limits and evidence.
Existing data wins; full SDK-data backup remains unfinished.

# RSS SDK port — 0.11.0 development checkpoint

RSS Reader now opens a real signed Rust/Wasm `.onklav-a` program, using the same
installer, permission broker, runtime and Kotlin/Compose renderer as Notes and
external apps. Only OnKlav is an APK. No legacy Java implementation is included.

Production path: AppsDesktop -> SdkAppScreen -> SdkAppModel (explicitly bound to
the current Application) -> Rust sdk runtime -> sdk/apps/rss controller -> scoped
Rust feeds services. Android SdkFeedService supplies bounded HTTP and RSS/Atom
parsing; it never gives a guest a Context or socket. The Rust feeds module has
no hardcoded first-party app identity and is available to any permitted SDK app.

Implemented: the original 18-source catalog and categories; enable/pause, add,
rename, categorize and remove feeds; conditional HTTP refresh, stop refresh,
Wi-Fi-only preference, foreground refresh interval; up to 40 items per source,
600 cached articles, 200 bookmarks and 4,000 read marks; offline detail reading,
query/category/feed filtering, pagination, summaries, unread-only/order settings,
read-mark reset and cache clearing; separate left feed/bookmark drawers; text
sharing, link copying and a host-owned publisher viewer. Removing a feed keeps
bookmarks. Cache and bookmark state is atomically stored under the app's private
SDK directory. Its 8 MB reader limit also respects the shared 16 MB app quota.

`network.feeds` and `storage.private` are checked before, during and after a
refresh. HTTPS redirects are bounded and revalidated; a custom DNS implementation
pins validated public addresses for each connection, including literal-IP
validation. Responses/decompressed XML are capped at 2 MB. DTDs/external entities,
oversized/deep XML, private/reserved addresses and unsafe schemes are rejected.
`network.web` opens a visible host page, with JavaScript, local-file/content
access, device web permissions and downloads disabled. This text-oriented viewer
is not the final Browser SDK surface. Clipboard/share/web actions require a
button action and their respective live grants. Network operations can be stopped;
permission changes/removal invalidate pending sessions and cancel HTTP calls.

Evidence: two feed-storage Rust tests and two actual packaged-app Rust tests
cover cache/bookmarks/read/filter/remove/offline reopen and revoked-result
rejection. Parser/security tests cover RSS/Atom/plain text, unsafe schemes,
private addresses, hostile XML and limits. RssAppsHostTest runs the full OnKlav
Compose UI with the production JNI and fixture feed replies; it opens an offline
article, bookmarks it, uses the left drawer, changes preferences and reopens.
Framework-host tests use accessibility click callbacks after controls become ready;
physical touch/handset/real publisher network acceptance remains separate.

The previous long build waits were traced to debug R8 shrinking after a failed
parallel test task, corrupt generated Gradle transform metadata and test lifecycle
assumptions. Debug shrinking is now disabled. Test and APK tasks run in separate
bounded phases, and cache restoration reuses dependency artifacts only. SDK
ViewModels use the current Application explicitly, avoiding a shared test
factory's earlier Application/data root. Test controls wait for enabled state.

Remaining reference differences: generic SDK article rows/search controls still
need exact spacing/card/chrome refinement; large-font
handset acceptance is open. Keyed scroll retention was added in 0.57.0. Publisher JavaScript-heavy pages need the future
Browser service. Earlier rewritten Kotlin RSS source preferences are retained and adopted once
on first SDK launch into a pristine SDK feed store (0.57.0). No private Inner Circle data is read.
The six-app conversion count belonged to the historical 0.11.0 checkpoint; current
implementation/remaining work is tracked in FINISH-NON-GAME.md.

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

## 0.57.0 — own-source adoption and reading position

Before the first SDK RSS launch, SdkAppModel reads this rewrite's `rss/feeds`
preferences and the included signed RSS publisher. The host-only Rust
`adopt_own_feeds` action verifies that publisher, live storage grant and absence
of an active RSS session. It atomically imports at most 32 unique HTTPS sources
only if feeds.json does not exist; existing SDK data always wins. No HTTP request,
article placeholder or old application private-data access is used for adoption.
Names derive from the host because the previous format stored URLs only.
The retained old list is never changed or deleted. The completion marker commits
after Rust success; a retry after a lost acknowledgement leaves saved data intact.
Explicitly empty lists remain empty. Malformed/over-limit/unsafe lists fail without
partial import or a marker, preserving the old preferences. Normal opening/refresh
can subsequently request feeds under the existing network grant.

RSS v3 separates article and bookmark page counters; closing a drawer no longer
resets the article page. Article Android Back returns to the list. The generic
SDK renderer keeps distinct body/drawer scroll positions by validated optional
frame keys, with a 32-slot bound per app model. Articles, filters, pages and bookmark
queries use different keys; normal refresh keeps the current key. Android's saved
scroll state and retained model support configuration changes; full process-death
navigation restoration is not claimed.

Focused Rust/JNI/Compose evidence and APK verification are in source-verification.json,
apk-verification-debug.json and evidence/v0.57.0. Real publisher/handset/large-font
acceptance and richer publisher viewing remain separate. Layout/icons unchanged.
