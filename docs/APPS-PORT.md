# Apps migration — 0.16.0 source checkpoint

The Apps desktop has eight original applications. This checkpoint converts Notes, RSS, Read Aloud, Browser, YouTube and Annotation
into actual signed SDK packages and establishes the runtime/installer needed
by the other ports. It does not claim that all eight apps are ported or complete.
No separate Android app APK is produced. No Inner Circle Java code or executable
is included. Inner Circle 2.2.85 is a read-only design/behavior reference.

| Desktop app | Current implementation | Work still required |
| --- | --- | --- |
| Notes | Rust Wasm controller in `Notes.onklav-a`, generic Kotlin/Compose renderer, Rust private storage/broker | Broader handset/lifecycle/export/share acceptance, exact visual parity; search returns first 40 matches |
| Browser | Signed Rust/Wasm controller, scoped Rust browser metadata and generic Kotlin/Compose web/profile components | Handset website/media/profile checks, native data cleanup, reference behavior/style and own prior data adoption; see BROWSER-PORT.md |
| YouTube | Signed Rust/Wasm controller, scoped Rust video library and shared Kotlin/Compose provider/media components | Handset playback/profile/ad/account acceptance, exact style/private-mode/rotation, internal Browser handoff and complete native-data cleanup; see YOUTUBE-PORT.md |
| Read Aloud | Signed Rust/Wasm controller, private Rust drafts, generic Compose/speech SDK components | Original bundled voices/shared messenger engine, own draft adoption, handset and exact UI parity; see READER-PORT.md |
| Annotation | Signed Rust/Wasm controller, Rust image-space edit/history/library store and shared Kotlin canvas/image broker | Physical gesture, accessibility, exact reference spacing and process/rotation/export-provider recovery acceptance; see ANNOTATION-PORT.md |
| RSS Reader | Signed Rust/Wasm program, scoped Rust feed cache/bookmarks/read/settings and brokered Kotlin HTTP/XML | Exact card/search/scroll and prior Kotlin source-list import; publisher and handset acceptance; see RSS-PORT.md |
| Social | Intentional permission-free Rust/Wasm Hello World placeholder, as Charles requested | No social features requested; see SOCIAL-PLACEHOLDER.md |
| Walkie-Talkie | Conversion started: Rust control/floor/audio rules and pure Kotlin/JNI rules API | Actual SDK controller/UI, scoped session/clip store, recording/playback broker and SMS/MMS integration; see WALKIE-PORT.md |

Walkie-Talkie remains marked as SDK port pending in
Settings > Apps. They are not misrepresented as installable SDK apps. First-party
Notes, RSS, Read Aloud, Browser, YouTube, Annotation, the Social placeholder and the separately compiled developer Counter use the same manifest,
Ed25519 verification, capability review, install/update/remove operations,
restricted Wasm interpreter and declarative Compose renderer.

## Actual production path

`AppsDesktop -> SdkAppScreen -> SdkAppModel -> AppSdk -> NativeCore JNI -> Rust
sdk::dispatch -> wasmi -> main.wasm -> validated frame/effect -> Rust capability
broker -> scoped document service or Kotlin host callback -> Compose widgets`.

The Notes controller is source in `sdk/apps/notes`, compiled to Wasm and packaged.
The host does not choose Notes logic from a package descriptor. Counter proves a
separate arbitrary Rust application executes without rebuilding the host.
No Android Context, imports, native filesystem or raw network access is exposed
to guest code. See `sdk/README.md` for authoring and exact initial limits.

## Notes behavior preserved

Title/body editing; 80/100,000 UTF-16 limits; debounced durable save; reopen;
left saved-notes drawer; search, new, load and confirmed delete; clipboard
copy/paste; export menu with Files destination and share chooser; appearance font
and shared popup chrome. Existing rewritten OnKlav Notes are adopted once into
private SDK storage, with original files retained. This does not read Inner
Circle data. If the existing data exceeds the SDK quota, the host exposes the
previous rewritten Kotlin editor as a recovery path and reports the migration
failure rather than losing documents.

Initial curated Notes bootstrap grants its declared storage/clipboard/export/share
capabilities. External installs require user review and start with no grants.
Permission changes, updates and removal invalidate live sessions/pending effects.
Revocation is also checked for native text-selection clipboard controls.

## Verification and limits

Rust tests execute the actual Notes and Counter packages, installation/update,
signatures, publisher pinning, stale reviews, removal/reinstall, isolation,
revocation, clipboard native gate, quota/UTF-16 limits, malicious Wasm imports,
loop fuel, initial memory and invalid output pointers. Android framework-host UI
tests use real Compose and the production JNI runtime. APK signature/structure,
embedded package and native source/binary hashes are separate checks.
No physical-handset installation, external share recipient, carrier exchange or
performance claim is established by these tests. MMS_READY remains false.

The SDK is a development preview, not independently security audited. Current
widgets cover small document apps; network/media/TTS/communications/drawing APIs,
custom package icons, automatic old-blob collection and external Godot games
remain open. Continue one verified app/service chunk at a time, retaining the
original desktop and Settings manager designs.

Read Aloud is now a signed Rust/Wasm SDK app; see READER-PORT.md for its verified production path and exact remaining voice/data/parity gaps. Social is now the intentionally minimal SDK placeholder; Walkie-Talkie conversion has started.

0.15.0 is deliberately a source/SDK checkpoint. The user requested no APK per app.
Current source test results are in source-verification.json (Annotation’s earlier results remain in annotation-verification.json); the preserved
apk-verification.json and verification.json describe the last 0.14.0 phone build.

Social is intentionally a Hello World placeholder per the latest user correction, not an unfinished feed. Walkie’s rules foundation is not counted as a completed SDK app.

## 0.41.0 — four-area continuation

Implemented durable Messenger callback/provider recovery, older group reaction
parent context and bounded history search, durable Walkie result/background
recovery and late-clip fallback, internal Browser handoff, paged Notes search,
shared speech settings/ownership, own Reader draft adoption, Annotation viewport
restoration and per-app website-profile isolation/cleanup.

Verified 145 Kotlin/framework-host and 144 freshly rebuilt Rust tests
(289 total), with one host APK (code48). This does not complete carrier,
two-phone audio or exact reference acceptance. MMS/default SMS role remain
disabled. See RESUME-0.41.0.md for production paths and explicit remaining work.

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
