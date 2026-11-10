# OnKlav 0.100.23-rc1/code132 — APK ready

Original-signer ARM64 APK includes approved Kitten Nano0.8 FP32 male2 shared neural speech and female5, background built-in reading controls, Write two-row toolbar/selection refinement, calculator spacing, video soundtrack and YouTube audio format/MP3 improvements. Preserves all prior completed work. Reused source-current native/SDK/game/speech outputs and existing focused test evidence; no broad test reruns.

Final normal build and APK verification passed: package/version/original signer, ZIP integrity, 16KiB alignment, exact core/speech JNI, voice pack/notices/source archives, 23 bundled signed apps and required classes. Packaging retains the exact verified speech-native bytes. Fixed an APK verifier variable-shadowing bug found after its content checks passed. Initial missing-cache, stripped-speech and verifier diagnostics retained under docs/evidence/v0.100.23/diagnostics; final build-debug.json and verify-debug.log passed. Physical Samsung/carrier/Bluetooth/latency/endurance acceptance remains separate. Never generate a new signer; preserve all source/licenses.

# OnKlav 0.100.4/code113 — APK ready

Messages and backup update built and verified with the original signer. Includes current-SIM resolution, single-part dispatch, persisted visible carrier failure details, late positive receipt/legacy status reconciliation and confirmed Retry Text with latest-state recheck. Metadata restore now accepts the same bounded picker reactions as message editing. Both original supplied backups restore in host JNI checks; two notes retain exact fields. No private backup bytes are included in source.

293 core and18 focused Android checks passed. All compiled runtime classes match the test bundle; APK version/package/signature, source-matched Linux/main/diagnostic ARM64 JNI, all25signed app packages, game pack and16KiB alignment verified. See docs/messages-backup-release-verification.json. Full lint reached its240-second limit and is NOT passed.

Toolchain blockers are resolved: official NDK r27c and Rust1.90 ARM64 target restored. A cold native dependency build timed out; only Android compilation was resumed from cached outputs. UI compilation completed with a4GiB Gradle heap after a2GiB/300-second timeout. Do not rerun passed broad suites. Phone carrier delay, actual retry behavior and on-phone backup acceptance remain unverified. Preserve all earlier Clock/permission/media/Camera/YouTube work and original identities.

# Permission setup, Clock and streaming recovery —0.100.3/code112 (APK ready)

See docs/PERMISSIONS-PLAYBACK-REPAIR.md and permissions-playback-verification.json. Included-app permissions are a standing rule; original-publisher defaults repaired once, later revocations preserved. Initial/upgrade host setup offers required Android approvals. Clock preserves pending saves across precise-alarm approval and timer rows visibly count down. Radio opens without a search. Shared playback cancels network reads before deferred decoder release outside registry locks, probes podcast/audiobook sizes on IO workers and guards startup/notification failures; the initial validated route no longer rebuilds the player.

24unique focused Android cases and10Radio controller cases passed;10media/probe cases rechecked after the last shared change. Full lint0errors/269warnings/1information. ARM64debug APK built/verified with original signer, all25signed apps, matching JNI/source and16KiB alignment. Native and game sources unchanged. Preserve camera/YouTube/offline fixes and corresponding source/licenses. Physical phone/codec/carrier/lock-screen acceptance remains open. No further repeat/broad checks without new changes or failures.

# OnKlav — Android platform and SDK release candidate

Current checkpoint: **0.70.0-rc1/code78**, Android target36.
See [current corrections and limitations](docs/RESUME-0.70.0.md).
Games are deferred; device/account/carrier and store qualification remain open.

## App developers

The external app toolkit0.4 is in [developer/README.md](developer/README.md).
It includes project generation, signing/build/inspection, scenario and production-
broker testing, shared UI/controllers, file selection/media reuse and project templates and current API/compatibility guides.
The app-runtime readiness results are separate from phone/carrier acceptance.

## Architecture

Only OnKlav is an installable Android APK. `android/app` contains the Kotlin/Compose
shell and Android integration; `android/shared` contains shared UI and Rust JNI;
`android/tools` retains the Notes recovery fallback; `android/games` embeds Godot.
`core` implements Rust storage, messaging state, package validation and codecs.
`sdk` contains signed Rust/Wasm apps using the generic host renderer and brokers.
Files, Notes, RSS, Read Aloud, Browser, YouTube and Annotation use the SDK. Social is an
intentional Hello World placeholder; Walkie still needs two-phone acceptance. Counter is a
developer example. Air Hockey solo uses Godot; friend play and other game ports
remain open. See [rewrite status](docs/REWRITE-STATUS.md) for precise gaps.

There is no old Java application source or compatibility module. Android/JVM and
Godot dependencies contain upstream code that the host still needs. Private-data
adoption and cleanup code is active functionality and must not be deleted as
obsolete. External Godot execution stays disabled. MMS/default-SMS selection is available only through explicit carrier handset testing.

## Build and check

For Kotlin-only continuation: JDK17, Android SDK36/build-tools35.0.0, Gradle8.11.1
(wrapper), existing Android signing backup, and source-matched retained JNI,
SDK packages and Godot pack. Set JAVA_HOME and ANDROID_HOME. Restore downloaded
build dependencies if necessary; local SDK/JDK/cache files are not source archives.

```bash
python3 scripts/build.py --plan
python3 scripts/build_sdk_apps.py --plan
python3 scripts/build.py                       # optimized ARM64; reuse current artifacts
python3 scripts/build.py --variant debug       # development APK
python3 scripts/check_android.py --tests-only  # separate bounded framework-host checks
python3 -m unittest discover -s scripts -p 'test_*.py'
```

Use `--test fully.qualified.TestClass` with check_android.py for focused tests.
Use `build.py --online` only when Gradle dependencies need fetching, and `--abis`
only when another architecture is needed. Build.py runs broad tests only with
`--tests`. It verifies source freshness, retained signer, package identity, native
alignment and APK contents; it refuses inputs changed during assembly.

Rust/SDK/Godot tools are needed only for their changed components. Native source
changes require Rust Android targets/NDK27.2.12479018 for mobile, or a host-only
build for host tests. Godot source changes require Godot4.5.1 and bounded checks/
export. See [build workflow](docs/BUILD-AND-PERFORMANCE.md) and
[selective SDK builds](docs/SDK-BUILD-WORKFLOW.md). Publisher keys stay outside
the source tree; existing apps must retain their publisher and bump versions.
New authors can start with [sdk/README.md](sdk/README.md).

## Evidence

Use docs/source-verification.json for this checkpoint's exact fresh selected
host checks and docs/apk-verification.json for the optimized ARM64 APK. The older
full 0.45 host suite passed139 app checks plus12 shared checks; that outcome is
historical. Build planning has20 passing Python checks from the unchanged
0.45.1 tooling. None of these prove installed-phone responsiveness, real carrier
transport, two-phone audio or complete reference parity.

Numbered RESUME files and docs/evidence retain historical outcomes. Superseded
root notes are labeled under docs/history; they do not override current guidance.
