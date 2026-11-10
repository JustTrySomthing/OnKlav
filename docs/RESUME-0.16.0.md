# Resume 0.16.0 — Social placeholder, Walkie started

Charles clarified that Social was only a placeholder: implement a minimal Rust
Hello World SDK package and then start the next app conversion. Social is done
at precisely that scope; do not invent a feed, social identity or network service.
Walkie-Talkie continuation is already authorized; no further app-start approval
is needed for its next chunk. Continue it in bounded steps, preserving source.

Current source is 0.16.0-dev/code23. 97 tests pass: Rust55 + Kotlin42. Results are
in source-verification.json and docs/evidence/v0.16.0. No APK, mobile native
rebuild, physical device, real recording/playback or carrier check occurred.
The last verified APK remains 0.14.0-dev/code21 and its existing reports remain
historical. Current Rust matches host-native-provenance.json (Linux JNI only).
Source-only packaging omits stale Android Rust binaries. Keep both private
publisher and Android signing keys; never regenerate or include them publicly.

Social lives in sdk/apps/social, signed as Social.onklav-a, with zero capabilities,
null state, one Hello World label and no effects. The normal SDK host supplies
header/Back. AppSdk maps the original Social desktop entry to the package.
The signed package and desktop-open/Back tests pass. See SOCIAL-PLACEHOLDER.md.

Walkie phase one is core/src/walkie.rs: strict typed original control framing,
microphone-floor transitions, capture/autoplay gates and structural AMR/AMR-WB
validation. NativeCore op=walkie_rules exposes pure rules to the trusted host;
a real Kotlin/JNI test exercises it. It records, sends and plays nothing, creates
no files, grants no guest capabilities and is NOT a completed SDK Walkie app.
Read WALKIE-PORT.md before continuing. The next chunk is the actual scoped
session/clip store and Rust/Wasm controller, followed by brokered audio/UI and
transport. MMS_READY remains false; do not change default SMS role or simulate
connection/delivery to make buttons look operational.

Use workspace restored-env/env.sh and /root/.cargo/env. Export
ONKLAV_APPS_SIGNING_KEY to existing migration-private/apps.publisher.key.
Run build_sdk_apps.py after guest changes, build_native.py --host-only after
core changes, then check_android.py --tests-only. Shared-library compilation,
app/test compilation and Android host tests are separately limited to 180s.
Latest timings: 84.8s /162.7s /91.4s; Rust tests29.7s and host JNI3.8s. Compilation
completed inside its limit; no emulator loop occurred. For the next requested
phone batch, build both Android ABIs and verify the actual newly assembled APK.

package_source.py reads the generic source-verification.json, validates current
host/SDK provenance, saves source and SDK ZIPs and the report's internal_packages.
Do not relabel or reupload the old APK. Generated browser screenshots stay out of
source/SDK artifacts. Earlier Annotation/YouTube/Browser/voice/carrier/game gaps
remain, and full rewrite/SDK flags remain false.
