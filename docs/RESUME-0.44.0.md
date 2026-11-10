# Resume OnKlav 0.44.0 — build, package size and startup

Charles authorized these fixes in the shorter performance conversation because
continuing the previous long chat repeatedly crashed. This work does not start
another feature migration or replace the current framework architecture.

The phone target is 0.44.0-dev/code51, package
`app.onklav.home.prototype.rewrite`, signed by the retained Android certificate
`6ed975c5fc27d19f6291f751a8bb00fa8a2c2e7d268bffc7b6ddf7e1b1a7a69d`.
Optimized is non-debuggable, shell-profileable, R8/resource-shrunk and ARM64-only.
The first complete final build is 38,246,689 bytes versus 128,249,221 bytes for
0.42's dual-architecture debug APK, a 70.2% reduction. This is a packaging/size
comparison, not measured phone responsiveness or equivalent build-time evidence.

## Changes

- `scripts/build.py` hashes inputs and reuses unchanged SDK packages, native
  libraries and the Godot pack. Broad Android tests are separately requested.
  Every execution phase is timed and bounded. There is no automatic retry loop.
- `--variant debug` is the development path; optimized is the default phone
  comparison build. x86_64 is explicitly opt-in with `--abis`.
- Native builds accept requested architectures. The source archive now retains
  source-matched mobile JNI so a new Kotlin-only continuation does not require
  a Rust/NDK rebuild. Native/core and SDK programs are unchanged in this task.
- The extended icon library is replaced by icons-core plus the six exact used
  Apache-licensed extended vectors. Artwork is unchanged. The old development
  keep-everything consumer rule is removed, while JNI/Godot keep rules remain.
- MainActivity's artificial 350ms-to-two-second pre-draw hold is removed.
  Successful bundled-app setup is cached by Application/epoch; setup failure,
  missing registry and changed package permissions/removal are not hidden.
  Launch/event authorization remains live in Rust.
- `check_android.py --test` supports bounded app checks. `build_inputs.py` has
  regression checks for stale/new inputs and changed/missing packaged artifacts.
- `measure_phone.py` is a bounded, explicit-device evidence collector; no phone
  measurements were made here. Refer to BUILD-AND-PERFORMANCE.md before use.

## Build environment and diagnostics

The old 0.9.2 cache lacked newer OkHttp and optimized-build lint dependencies.
Current dependencies were recovered/fetched; `--online` permits an explicit
initial dependency fetch rather than repeated offline failures. Subsequent
builds default to the local cache. Android's required AAR annotation generation
and release lint tasks are enabled and passed in the final optimized assembly.
The rejected attempt to omit annotation export was reverted; no fake metadata
was supplied. A damaged generated Gradle transform was quarantined. See retained
phase logs for these initial diagnostics rather than retrying them blindly.

Do not skip required tests or force a failed build through. Reuse verified inputs,
make one evidence-based correction when needed, and retain exact remaining gaps.

## Remaining scope

No connected handset/carrier, frame/scrolling/latency or before/after phone
benchmark is claimed. The Wasm per-event instance/JSON path remains a profiling
candidate. Easier app authoring, full feature/UI parity and the outstanding
transport/game work in TODO.md remain unfinished. MMS/default role and untrusted
external Godot execution stay disabled. Existing data and signing identities
must be preserved. Do not begin an unrelated runtime rewrite to chase language
or size expectations without phone evidence.

Final fresh check counts and the incremental build timing are recorded in
source-verification.json and the accompanying 0.44 verification note.

Fresh outcome: 17/18 Kotlin app checks pass, five Python checks pass. One Groups
contact-sheet timeout remains failing after one corrective follow-up. Both new
bootstrap checks pass. No full suite, shared/Rust/Godot rerun or handset claim.
Unchanged rebuild plus APK verification: 25.39s; changed-input builds take longer.
Use package_source.py --allow-known-failures DESTINATION to preserve this
explicitly incomplete checkpoint; never relabel its test result as green.
