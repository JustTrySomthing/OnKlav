# Build and performance workflow

The default phone deliverable is an ARM64 optimized, non-debuggable, shell-profileable
test build. It retains `app.onklav.home.prototype.rewrite` and the existing signing
certificate. Host/package checks remain separate from physical-device and production-release qualification.

## Routine build

Restore the existing Android tools and signing identity. The source archive now
retains source-matched mobile Rust libraries as well as signed SDK packages and
the exported game pack. Kotlin-only work does not require Rust, NDK, Godot or the
app publisher key when those verified artifacts are unchanged.

```sh
python3 scripts/build.py --plan
python3 scripts/build.py
```

The plan hashes current inputs and outputs. Unchanged native libraries, SDK apps
and game packs are reused; stale or newly added input files invalidate reuse.
Gradle incrementally compiles and assembles the host, then the verifier checks
signing continuity, version, requested ABIs, JNI bytes, internal package bytes,
the game pack, manifest, defined classes, ZIP integrity and 16 KB alignment.
It does not treat old test evidence as a new test run.

The default uses the local dependency cache. If a new dependency is missing,
`--online` permits an explicit fetch; normal builds can then return to offline
operation. It does not recover a missing JDK/Android SDK or change signing keys.

```sh
python3 scripts/build.py --online
python3 scripts/build.py --variant debug
python3 scripts/build.py --variant debug --abis arm64-v8a,x86_64
```

Debug prioritizes iteration and host testing; it is unshrunk and unsuitable for
final size/performance comparison. Optimized uses R8 and resource shrinking with
release library inputs. Class names stay readable for this comparison build.
The old tools-wide keep-everything consumer rule is no longer applied. The exact
JNI class/method keep rule remains in the shared library; upstream Godot rules
remain active. Current optimized rules also retain mail providers and the NewPipe/Rhino dynamic execution path. The unused Rhino JSR-223 adapter is removed. The phone build excludes emulator-native libraries.

The extended Material icon dependency is also removed from every build variant.
Six required extended vectors are retained unchanged as Apache-licensed source;
the other five used icons come from icons-core. See third_party/androidx-icons.
Debug builds no longer process and package all unused extended icon families.

## Deliberate checks

APK assembly does not automatically rerun the full Android test suite. Run checks
appropriate to the changed behavior separately; `--tests` requests the broad
framework-host suite. Focused selectors are also available:

```sh
python3 scripts/check_android.py --tests-only --test app.onklav.home.SdkBootstrapTest
python3 -m unittest discover -s scripts -p test_build_inputs.py -v
```

Rust changes still require fresh native tests/builds. SDK changes still require
the existing publisher key and package compilation. Game changes require game
checks/export. Those phases are skipped only when their provenance matches.
Every phase has a timeout, progress output and a failure log; failures stop the
pipeline. No emulator boot, automatic retry loop or repeated full-suite run is
part of this workflow.

## Responsiveness changes and measurement limits

MainActivity no longer blocks first draw for a minimum of 350 ms while waiting
for SDK initialization (the previous fallback was two seconds). The Compose shell
draws immediately while existing asynchronous package setup continues.

Successful bundled-app setup is cached per Application and package epoch. App
install/removal/permission changes invalidate the cache. A missing registry also
invalidates it. Failed setup is not cached. This caches setup completion only:
every launch/event and privileged operation still uses live Rust authorization.

The Wasm event runtime and its permission boundary are preserved. Replacing that
runtime or removing its safeguards without measurements is not a performance fix.
The source still creates Wasm instances per event and serializes state/UI through
JSON; those remain candidates for bounded profiling if handset pauses persist.

`scripts/measure_phone.py` collects at most five explicitly selected-device,
process-cold launches plus launch-frame/memory evidence. It never installs apps,
clears their data, changes permissions or exercises carrier functions. Run it
separately against the reference and optimized app on the same phone; scrolling,
navigation, app-opening and subjective responsiveness need a handset pass too.
No connected handset measurement has been made by these source changes.

App-authoring complexity and complete original feature parity remain separate
unfinished work. Do not present an optimized package as proof that either is solved.

## 0.45 reliability and development improvements

Cancellation no longer becomes a user-visible Groups/Messenger/SDK failure.
Bootstrap no longer cancels itself when bundled installs update the catalogue.
Contact retry/loading/resume states and idle boundary scrolling are corrected.
Privacy-safe SDK tracing supports later phone profiling with a disabled fast path.
See SDK-BUILD-WORKFLOW.md for per-package planning, selective --app compilation,
atomic publishing, update rules and reuse without Cargo/signing keys. The starter
includes its proof/helpers and supports reuse without an Android host tree.
Host assembly snapshots production inputs and rejects concurrent source changes.

All 139 app checks pass, including Groups and provider retry; 12 shared checks
are verified, and 16 Python checks pass. APK size is 38,250,173 bytes (70.2% below
the old 128,249,221-byte debug package). Initial assembly took 206.84s plus3.02s
verification. Warm unchanged assembly took20.62s plus2.78s verification. These
cached-environment measurements do not promise edited-code or fresh setup times.
Phone performance and remaining feature migration have not been accepted.

## Build recovery after a chat switch

Resume from the latest `OnKlav-Continue-Here.zip`. The checksummed Android
toolchain is `OnKlav-Build-Cache-v0.9.2.part01` through `part09` in the
OnKlav project files. Run `scripts/restore_build_cache.py` with the parts
directory and an empty tools directory, source its `env.sh`, then overlay
`OnKlav-Android-Dependencies.zip` into `GRADLE_USER_HOME`. Restore the
existing debug keystore from `OnKlav-PRIVATE-Debug-Signing-Backup.zip` to
the build user's private Android directory and check its certificate against
`scripts/verify_apks.py`. Keep signing material outside the source archive.

For Shroom changes, use the official Linux Godot 4.7.2 editor from the
`godotengine/godot-builds` 4.7.2-stable release, verify its archive against
that release's SHA512-SUMS.txt, and set `GODOT_BIN`. Run
`python3 scripts/package_included_shroom.py` before Android assembly.
The project ships the matching Web template and shared Android game runtime.
Use `python3 scripts/build.py --plan`, then `python3 scripts/build.py` for the
optimized ARM64 APK. The verifier checks the signing certificate, package
version, native binaries, internal apps, shared games and included Shroom asset.

The original cache predates some current Android dependencies. On a fresh
restore use `python3 scripts/build.py --online` once if the offline build
reports missing dependencies. If Gradle's restored launcher exits with
`fatal library error, lookup self`, start it with
`GRADLE_OPTS="-Dorg.gradle.native=false -Dorg.gradle.internal.instrumentation.agent=false"`.
