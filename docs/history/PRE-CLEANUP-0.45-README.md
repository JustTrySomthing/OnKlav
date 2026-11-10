# Historical snapshot — superseded; not current instructions

# OnKlav 0.43.0 source/SDK checkpoint

Social is an intentionally minimal Rust/Wasm Hello World placeholder, alongside Notes, RSS, Read Aloud, Browser, YouTube and Annotation. Walkie-Talkie conversion has started; see docs/WALKIE-PORT.md. See docs/ANNOTATION-PORT.md for scope and remaining acceptance. This checkpoint deliberately makes no APK; the last verified phone build remains 0.14.0. See
[sdk/README.md](sdk/README.md) for authoring and [docs/APPS-PORT.md](docs/APPS-PORT.md)
for all eight apps and remaining work. Only OnKlav is an APK.

Current game-only continuation: docs/RESUME-0.43.0.md. No new APK; the last
verified host APK is 0.42.0. Full migration and phone acceptance remain open.

# OnKlav — actual rewrite, development checkpoint

**The full Inner Circle 2.2.85 feature rewrite is not complete.** This project
contains new Kotlin/Compose, Rust and Godot/GDScript implementation. It excludes
the old Java app and compatibility module. See docs/REWRITE-STATUS.md for gaps.

## Modules

- `android:app`: Kotlin/Compose five-tab shell and messenger UI/Android integration. Debug
  application ID `app.onklav.home.prototype.rewrite` deliberately installs beside
  the working OnKlav v0.7. It is not a complete messaging replacement.
- `android:shared`: new Compose theme/chrome and Kotlin-to-Rust JNI bridge.
- `core`: Rust private document/messenger state, bounded MMS wire codec and package inspection. Atomic
  saves, Unicode limits, search, validation and error propagation are implemented.
- `android:tools`: Kotlin library containing transitional tools/recovery screens.
  It produces no installable app APKs and opens inside the host.
- `android:games`: Godot library included in the host APK. Its non-exported
  Activity runs trusted engine exercises in a host-owned process.
- `games/engine-checks`: original Air Hockey solo port plus unfinished exercises
  for the other game types. See docs/AIR-HOCKEY-PORT.md; friend play remains open.

**Only OnKlav is an APK.** External apps/games/themes will be internal SDK
packages. Read docs/PLATFORM-CONTRACT.md for the required host capability boundary
and the precise work still needed before external packages can run.

There is no Java application source or old Inner Circle code in this tree.
Upstream Android/JVM libraries and Godot contain their own implementation code;
those external dependencies are distinct from the removed legacy app.

## Rebuild (Linux)

Tested tools: JDK 17, Gradle 8.11.1, Android SDK 36 / build tools 35.0.0,
NDK 27.2.12479018, Rust 1.90.0, Godot 4.5.1. Cargo.lock pins Rust dependencies.
Install Rust Android targets aarch64-linux-android and x86_64-linux-android.
Set ANDROID_HOME, JAVA_HOME, GODOT_BIN and make cargo available on PATH.
Restore the existing debug keystore from the separate private signing backup.
Run `python3 scripts/build.py`. It tests/builds Rust, exports the bundled Godot
pack and builds the single OnKlav APK. It never compiles the old app.
`python3 scripts/verify_apks.py` verifies the retained signer, package, native
libraries, embedded pack, one launcher, no nested APKs and no legacy classes.
Current exact-APK results are in docs/apk-verification.json and
docs/verification.json. The 0.9.3 development host retains the existing signer.
See docs/MESSAGING-PORT.md for the new messenger call paths, MMS/default-role
gate and precise unfinished scope. Current runtime outcomes belong to this
APK; historical 0.8.3/0.8.4 passes are not current-build passes.
See docs/HOME-REFERENCE.md for scope, artwork provenance and unfinished services.

For Groups implementation/limits see docs/GROUPS-PORT.md. After Rust changes,
run scripts/build_native.py, then scripts/check_android.py for bounded native,
framework-host UI, build and APK checks without an emulator.

Compiled Rust libraries are retained alongside source for recovery. The build
script regenerates them. Signing material, SDK/JDK/Gradle caches, Cargo target
files and private user data are excluded from source archives.

## Evidence and limits

Rust, Kotlin/JNI, Godot, Android build and APK checks are separate evidence.
See docs/verification.json and docs/CHUNKS.md. Original reference and app
captures are reviewed independently from functional tests. Software emulation
has a System UI ANR/focus history and does not certify physical-phone behavior.
Consult the current verification record for exact scope and hashes.
No Samsung Android 15, real carrier, external account or performance acceptance
is claimed. Keep the working v0.7 app installed while the rewrite continues.

## Android regression test

Build `:android:app:assembleDebugAndroidTest` after the host APK. On a test device,
install the host APK and the generated instrumentation APK, then run:

```
adb shell am instrument -w -r -e class app.onklav.home.NotesDeviceTest app.onklav.home.prototype.rewrite.test/androidx.test.runner.AndroidJUnitRunner
```

The instrumentation APK is a test harness only; do not distribute it as an
OnKlav app/package. The test enters apps through the host, checks Android Back,
left drawer placement and popup options, checks the private engine component,
and exercises the real Rust note save/recreate/clear path. Desktop/JNI and
Android tests must be reported separately.

## Appearance restoration

Version 0.8.2 restores the original staged Appearance editor, presets, named
themes, backgrounds, overlays and data-only .onklav-t import/export/share.
See docs/UI-PARITY.md for scope and remaining visual/functional checks, and
docs/THEME-PACKAGES.md for the new file format. Android regression selection is
`app.onklav.home.AppearanceDeviceTest,app.onklav.home.NotesDeviceTest`.

## Appearance reference restoration — 0.8.3

The full original Appearance flow was audited against source, palette/dialog
resources and rendered Settings / Appearance / Backgrounds / Themes screens.
See docs/APPEARANCE-REFERENCE.md. This corrects 0.8.2's square gray dialogs,
background draft/opacity behavior, missing complete help and non-portable
built-in wallpaper exports. The font picker retains active dialog colors when
a light theme is staged. Backgrounds has separate Save/Cancel, fixed controls,
reset/current/none, original gallery cards and provisional-image cleanup.
No original Java implementation or original APK is bundled.

## 0.9.1 reminder and recovery checkpoint

The original reply reminder chooser now supports hour/tonight/tomorrow,
custom date/time, rescheduling and removal. A queued old alarm cannot consume a
future rescheduled reminder. See docs/RESUME-0.9.1.md and current verification.
The mobile Rust/Godot binaries are unchanged; this checkpoint rebuilds Kotlin
and verifies the retained native bytes. Host Rust tests also pass with 1.90.0.
Build scripts now check for a full JDK, honor HTTPS_PROXY for Gradle and bound
network waits. Emulator boot/build commands must stay sequential and bounded.

## 0.9.2 fast verification and recovery

Use scripts/check_android.py for Kotlin changes while the verified Rust Android
sources/libraries remain unchanged. It runs Robolectric Android framework UI
tests and builds/checks the APK without booting an emulator. See
docs/RESUME-0.9.2.md and docs/build-cache.json for checksummed tool/cache recovery.
All 33 current tests pass; host tests do not replace handset/carrier acceptance.

For one-app source checkpoints: build SDK packages with the existing publisher key,
run `python3 scripts/build_native.py --host-only`, then
`python3 scripts/check_android.py --tests-only`. This compiles/tests Kotlin and
current Linux JNI without assembling an APK or rebuilding mobile ABIs. Source
archives omit stale mobile Rust binaries; the next full build regenerates both.
Use docs/source-verification.json for current checks.
