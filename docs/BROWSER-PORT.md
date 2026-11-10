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

Settings Backups now covers Browser tabs/bookmarks/settings.
See SETTINGS-PORT.md and RESUME-0.62.0.md for validation/merge evidence.
No APK was requested for this checkpoint; latest delivered is 0.61.0.

# Browser SDK migration — 0.13.0 development checkpoint

Browser now runs from signed `Browser.onklav-a`, compiled independently from `sdk/apps/browser` Rust source. It is not a selector for the previous Kotlin BrowserScreen and is not a separate APK. Rust controls address routing, settings, tabs, bookmarks, dialogs, confirmation and component commands. The scoped Rust browser service stores metadata atomically. Kotlin/Compose renders generic widgets and owns the web engine/resource lifecycle.

## Implemented production path

- Original Browser header/bookmark action, address/Go row, six bottom controls, eight tabs, tab chooser, bookmark drawer/add/remove confirmation, Home/New Tab/search engine settings, Share Page, Open in Browser, Find controls and clear-data confirmation. Drawer opens from the left as requested for OnKlav. Exact original artwork/spacing remains pending; no exact-parity claim is made.
- `browser.*` data services are generic and scoped to the authenticated SDK app, requiring `storage.private`. Eight tabs, 200 bookmarks, 160 UTF-16 title units, 4096 address units, 20-item bookmark pages, 3 MiB bounded browser record file and the shared 16 MiB Rust private-data quota. Website storage is outside that Rust record quota.
- Address/search conversion supports DuckDuckGo/Google/Bing and upgrades explicit HTTP addresses to HTTPS. Script/file/content/intent schemes and embedded credentials are rejected. Browser networking includes HTTPS websites on custom ports/local networks; it does not claim the public-address DNS restriction of the separate RSS feed fetcher.
- Validated `web` nodes have a bounded tab ID, command sequence and callback. Rust derives native profile identity from the installed app ID; packages cannot select another app’s profile. Host callbacks are non-gesture events, bound to the current tab/session. Revocation, updates, removal and stale-tab callbacks fail closed.
- A web component requires `network.web`, `clipboard.read` and `clipboard.write`, even with JavaScript disabled. This conservative combination covers native website editing and web clipboard APIs. The newly shared SdkGatedWebView also gates native clipboard menu actions, keyboard shortcuts and accessibility actions with live grants. RSS’s JavaScript-disabled publisher viewer uses this gated class and an app-specific profile.
- AndroidX MULTI_PROFILE must be supported by the installed WebView provider. Code selects the profile before any website/settings/state use. Unsupported providers show an update message and do not load websites. There is no shared-default-profile fallback for website loading.
- No guest native/JavaScript bridge, file/content URL access, automatic downloads, camera, microphone, geolocation or file-picker grants. Mixed content is disabled, SSL errors cancel, media needs a user gesture, third-party cookies are disabled and service-worker network/file access is blocked. DOM JavaScript is website code inside the normal web engine, not a native API available to Wasm.
- Backgrounding/disposal destroys the active native surface; bounded history bundles retain at most eight times 256 KiB in host memory. Clear data acts on the current app’s cookies/local website storage and in-memory page histories, preserving metadata preferences/bookmarks. It deliberately does not call a global cache-deletion API across profiles.

## Verification and exact limits

74 tests pass: 42 Rust, 21 app Kotlin and 11 shared Kotlin. Rust tests run the actual signed Browser package, metadata/tab/bookmark/settings/reopen operations, stale callbacks, capability denial and a callback that tries to request a gesture-only external launch. Native policy tests cover URL/profile derivation, bounded history, and clipboard key/accessibility gates.

The real Compose/JNI Browser test covers bookmarks, adding a tab, saving the Home Page, navigating Home and close/reopen. The host test’s WebView provider lacks MULTI_PROFILE, so it exercises the safe fallback and metadata UI; it does not verify actual website rendering, real profile cookie isolation, sign-in or media. Fixture shell/drawer captures were reviewed internally and are not delivered as browser screenshots. APK verification checks signature, CRC, 16 KiB alignment, both exact Rust libraries, all four signed package/program assets, no nested APK and no legacy defined classes.

The final bounded phases passed: compilation 142.8s; tests 100.0s; APK assembly 43.1s; APK checks 7.1s. Each compile/test/build phase has a 180s maximum, verification 60s. The only UI test failure was an ambiguous Back selector matching both header and page controls; it now chooses the header. No emulator retry loop was started.

## Remaining work — do not call this full Browser parity

Actual website/profile/clipboard/menu/media acceptance requires a handset with a supporting provider. Original fullscreen video, find match counts, original action artwork/precise spacing, tab selection/last-tab-close details, settings error presentation, configuration/process-death recovery and external sign-in/download-target routing need further reference work. Ad blocking was not a Browser feature in the reviewed WebActivity; YouTube has its separate reference and port.

The previous rewritten Kotlin Browser preferences remain untouched and are not automatically imported. SDK removal now uses durable cleanup intent, per-app profile rotation/deletion and scoped speech-preference cleanup (SdkNativeData, 0.41/0.42); further provider acceptance remains open. Clear Browsing Data currently clears scoped cookies/local storage/history, not per-profile cached files or all service-worker databases. Neither this checkpoint nor its tests establish a complete data purge. No Inner Circle private data was copied or deleted.

YouTube, Annotation, Social and Walkie-Talkie remain to convert to the SDK. Original bundled Read Aloud voices, RSS layout/data-adoption gaps, carrier/default-role, external Godot sandboxing and measured performance remain open in their ledgers. This is an SDK development checkpoint, not the finished replacement.

Primary platform contract used: https://developer.android.com/reference/androidx/webkit/WebViewCompat and https://developer.android.com/reference/androidx/webkit/ProfileStore . The dependency is the already-cached AndroidX WebKit 1.12.1; newer unverified APIs are not assumed.

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

## 0.56.0 — Browser Back and shared web recovery

Android Back now consumes native Browser history after drawer/menu handling; the
header Back remains an exit. YouTube retains its original Back-to-exit behavior
outside fullscreen. The active surface registers a scoped history callback and
removes it on disposal. History navigation repeats the live capability check.

The shared host web surface clears a prior load error on a new page start.
Renderer loss retires the active view before destruction, dismisses fullscreen,
clears that tab's stale in-memory native history and resets progress. The next
command creates a fresh isolated surface; Reload reopens the current stored URL.
Stale renderer progress/errors/navigation/download/fullscreen callbacks are ignored.

Clear/reset cookie completion is bound to the active view, command sequence,
native navigation generation and live grant. A late completion cannot reload over
newer navigation. The new page's completion clears history after loading, avoiding
the old immediate-clear/reintroduced-history race. Bookmarks/preferences remain.
No global cache purge, new permissions or changed artwork/layout was introduced.

Focused framework-host evidence is in source-verification.json. The test provider
lacks MULTI_PROFILE: native page-state tests use controlled WebViews, while the
real signed Browser screen test covers metadata/navigation and safe fallback.
Real provider/profile/media acceptance remains separate.
