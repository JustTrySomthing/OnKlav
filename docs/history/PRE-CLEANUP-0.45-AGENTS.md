# Historical snapshot — superseded; not current instructions

# OnKlav rewrite instructions


Latest checkpoint: 0.45.0-dev/code52. Read docs/RESUME-0.45.0.md,
docs/VERIFICATION-0.45.0.md and docs/SDK-BUILD-WORKFLOW.md first.
All 139 app host checks pass, with 12 unchanged shared checks verified and 16
Python build checks passing. The old Groups timeout is resolved; do not treat
0.44's historical failure as current. No handset performance benchmark was made.
The optimized ARM64 APK is 38,250,173 bytes. One unchanged assembly and
verification took 23.40 seconds; edited/fresh builds take longer.
Use scripts/build.py (optimized ARM64), --variant debug for development;
SDK builds now select stale packages, with --plan and --app for focused work.
Reuse hash-verified native/SDK/game artifacts; do not restore Rust/NDK/Godot or
publisher keys for Kotlin-only changes. Source archives retain mobile JNI.
Keep tests bounded and separate; no repeated emulator/full-suite retry loops.
The shorter conversation is intentional. Do not restart the framework rewrite.
Preserve app/signing/publisher identities, user data and all transport gates.
Handset speed, authoring simplicity and wider migration gaps remain open.

Charles authorized a real rewrite: Kotlin/Compose interfaces and Android integration,
Rust shared core, Godot/GDScript games. No copied Inner Circle implementation,
legacy Java module, old app binary, or wrapper around the old app is allowed.
Do not count an unused Rust library as a port. Rust implements document storage, bounded archive inspection, durable messenger
state and a bounded MMS codec. Kotlin Notes and messenger call it through JNI.

Inner Circle 2.2.85 is the read-only visual/behavioral reference. Preserve its
Messages / Groups / Apps / Games / Settings ordering, custom apps, compact
controls, themes, accessibility and one-current-match behavior. Do not invent a
new dashboard or quietly reduce features and call that parity.

This development tree has no Java application source. Kotlin/Android and Godot
still depend on upstream JVM/Java/native framework binaries. Do not describe a
Kotlin APK as a binary containing no JVM bytecode.

Read docs/REWRITE-STATUS.md first. The complete rewrite is NOT finished. Godot
scripts in games/engine-checks are engine exercises, not completed ports of the
original four games or Panda. MMS, group/social/walkie transport, multiplayer,
several settings, the remaining SDK app/service ports and full UI/behavior parity remain open.
Do not ship this as a finished replacement or remove those gaps from the ledger.
Read docs/CHUNKS.md and docs/SDK-OPTIONS.md next. Charles explicitly asked to break long stuck attempts
into small steps. Complete one app or transport operation per verifiable chunk.
Build before starting software emulation; keep device commands and the whole
runtime check bounded. Do not restart an endless emulator retry loop or let an
optional screen-unlock command abort all functional checks. Charles now requests one app at a time: compile/test and save the source/SDK checkpoint, then ask before the next app. Defer Android APK assembly, mobile native rebuild and APK upload until a requested batch/phone build. Do not relabel the last verified APK as a new source-only version.

The platform base ID remains app.onklav.home.prototype and signing continuity
must be retained. The unfinished debug host adds .rewrite so it cannot overwrite
the working v0.7 app. OnKlav is the ONLY installable Android application. Built-in apps are host modules,
never APK flavors. Games use the Godot runtime included in the host. SDK apps,
games and themes are internal packages, installed and managed inside OnKlav.
First-party and third-party apps/games use the same SDK package model. Current
host-compiled tools are transitional, not the completed SDK port.
Settings > Apps and Settings > Games manage packages; Appearance manages themes.
Use .onklav-a / .onklav-g; .onklav-t is the provisional theme spelling (Charles
said .onflav-t in his latest message). Do not create independent app/game APKs.

Android grants permissions to OnKlav. External packages must receive only explicit,
revocable capabilities through a host broker. A host-owned process is crash
containment, NOT a security boundary from host permissions. Godot/GDScript and PCK
loading alone do not sandbox untrusted scripts. No external executable packages
may run until a restricted runtime and broker are implemented and tested.
Keep archive inspection fail-closed: inspection is not installation or trust.
The initial restricted Rust/Wasm app runtime now has author signing, identity,
private documents, install/update/remove, capability review/revocation and limits.
Read docs/APPS-PORT.md, docs/RSS-PORT.md, docs/READER-PORT.md, docs/BROWSER-PORT.md, docs/YOUTUBE-PORT.md, docs/ANNOTATION-PORT.md and sdk/README.md. Notes, RSS, Read Aloud, Browser, YouTube and Annotation are real SDK packages;
Social is an intentional permission-free Hello World SDK placeholder; do not invent a feed. Walkie is the remaining incomplete app conversion. Its SDK offline shell and rules are not a completed voice app. External Godot code stays disabled.

Never change default SMS role until incoming/outgoing MMS is implemented and
verified. Never silently copy/delete private Inner Circle data. Keep signing keys
out of source and public artifacts. Restore the separate signing backup for
future updates; do not generate a replacement key.

For every migration claim record the actual production call path, component
language, functional tests and remaining behavior gaps. Separate build/signature,
host/JNI tests, Godot desktop tests, emulator/device checks, carrier/account checks
and measured performance. Language choice alone does not establish better speed
or stability. Keep this checkpoint resumable; continue authorized rewrite work
without asking Charles to reapprove routine implementation steps.

For Kotlin-only continuation, use scripts/check_android.py and the checksummed
build cache described in docs/RESUME-0.9.2.md. Do not repeat the failed full
software-emulator boots by default. Android framework host tests and APK
verification are separate from installed-APK/handset/carrier acceptance.

The current Groups checkpoint is 0.9.3. Read docs/GROUPS-PORT.md and
docs/RESUME-0.9.3.md before continuing it. Saved groups and the carrier group
post/reaction parser are now real Rust consumers with Kotlin/Compose UI. Group
MMS, older parent-context resolution and friend activities remain incomplete.
After Rust changes run scripts/build_native.py before scripts/check_android.py;
current native-provenance.json must match all Rust source and the host/mobile JNI.
The older tool cache is reusable only with the current source-matched Linux JNI
fallback; the updated restore script enforces that distinction.

Current code checkpoint: 0.39.0 UI chrome/motion/vector branding correction; see docs/RESUME-0.39.0.md. Previous checkpoint: 0.38.0 explicit partial-message continuation with original-record checkpoints and committed-receipt reconciliation. Unknown/present changed outcomes and phone/provider acceptance remain unfinished; phone writes remain disabled. Read docs/RESUME-0.38.0.md, docs/HISTORY-BACKUP.md, docs/SETTINGS-PORT.md and TODO.md. Walkie remains not operationally accepted; its limits are in RESUME-0.23.0.md. Charles explicitly authorized continuing within the rewrite without repeated permission questions.
Use build_native.py --host-only and check_android.py --tests-only for this workflow.
Current requested APK batch is 0.39.0-dev/code46; see docs/RESUME-0.39.0.md,
verification.json and apk-verification.json. Current Android ABIs/SDK packages
are source-matched and retained from the verified 0.38 build; no Rust/SDK changes
in this UI task. 127 Kotlin tests plus 141 unchanged Rust checks =268. Current
mobile native-provenance.json now also matches Rust. Source packaging omits mobile
Rust binaries, so a fresh checkout still requires build_native.py before an APK.
No actual phone/carrier acceptance. MMS/default role remain disabled. Charles
explicitly requests delivery of this APK, then an approval question before the
next SMS/MMS, Groups and Walkie transport work. Await that answer before starting.

Read docs/SOCIAL-PLACEHOLDER.md and docs/WALKIE-PORT.md. Social has zero capabilities and only Hello World. Walkie now has a gate-closed host carrier/audio bridge and correlated session/floor/receipt integration. Phone acceptance, background scheduling/recovery, late arrivals, cleanup review and full original UI remain open. No testing phones are available. Do not count fixture tests as live acceptance or enable MMS/default role. User requests full Walkie completion; continue within it without asking to start another chunk.

Latest UI checkpoint: 0.40.0-dev/code47; RESUME-0.40.0.md and verification.json.
The user's latest explicit direction supersedes earlier left-side instructions:
all side pages/drawers enter from and return to the RIGHT. Retain the outgoing
composition through exit. 135 Kotlin tests passed, 141 Rust checks unchanged.
Only one host APK; no Java source added, no transport gate enabled.

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

Charles authorized all four areas together; do not ask to start each app. Keep
commands bounded. A full native build now updates both mobile and host proofs.

Current checkpoint: 0.42.0-dev/code49. Read RESUME-0.42.0.md first. 148 Kotlin and
145 Rust checks passed. User authorized design parity, group activities and both
cleanup edge cases; no per-item APK. Preserve right-side motion and single host
APK. Durable removal blocks new install/launch until cleanup reconciles; don't
remove this guard or turn a failed delete into success. Native linker restore
must materialize symlink targets correctly. Use fresh Gradle generated caches
after restore; reuse downloaded artifacts only. Friend game ports/transport,
reference handset acceptance, original speech engine and wider migration remain
open. Do not call activity routing a completed friend game.

Current source checkpoint: 0.43.0-dev/code50, no new APK. Read RESUME-0.43.0.md
and AIR-HOCKEY-PORT.md. Air Hockey `ah2` SOLO now uses new GDScript rules/UI,
Godot rigid-body physics and one current save; 72 engine +12 exercise checks
passed. Task 15 remains partial for friend transport and handset acceptance.
Do not restore the deleted first-to-seven hockey exercise or count catalog
routing as friend play. Core/JNI and app SDK are unchanged and proof-checked.
The next bounded task is Air Hockey friend protocol/host invitation/status;
actual carrier acceptance and untrusted external game execution stay closed.
No per-item APK. Last verified host APK remains 0.42/code49.
