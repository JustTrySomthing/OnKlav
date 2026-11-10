# Resume SDK apps / Notes checkpoint

Read AGENTS.md, APPS-PORT.md, sdk/README.md and verification.json. Notes is the
first packaged Rust/Wasm app. Seven catalog apps still need SDK conversion;
never replace the existing desktop with a new design or emit per-app APKs.

Restore the existing source and separate Android signing backup. The new app SDK
publisher key is a second private backup (OnKlav-App-Publisher-Private-Backup.zip).
It is required for compatible future Notes updates. Neither private key is in
the public source or SDK starter. Do not generate replacement identities.

Toolchain: Rust 1.90, wasm32-unknown-unknown, aarch64-linux-android,
x86_64-linux-android; full JDK17, SDK36/build-tools35, NDK27.2.12479018,
Gradle8.11.1. The older build cache may restore build outputs or Gradle transform
metadata that must be discarded, retaining dependency archives. Restore helper
now skips Gradle transforms. If an old cached build has stale native ABI entries,
run Gradle clean once; do not package leftover native intermediates.

Set ONKLAV_APPS_SIGNING_KEY to the restored private SDK publisher key outside the
source tree. `python3 scripts/build_sdk_apps.py` builds Notes and Counter and
records source/program/package hashes. Run it whenever SDK Rust code/manifests
change. Then `python3 scripts/build_native.py` rebuilds/tests the host JNI and
both Android libraries whenever core sources or fixtures change. Its provenance
records all sources and binaries. `python3 scripts/check_android.py` validates
provenance, runs bounded Android framework-host tests and verifies the final APK.
Every default test command is bounded; no software emulator is started.

SdkAppsHostTest uses the same API29/native-graphics/paused-looper configuration as
other JNI UI tests. Its manual offscreen window traversal is test-only. Wait for
real serialized IO actions before clicking enabled controls; virtual clock
advancement alone does not wait for the Rust/IO executor.

See app-sdk-provenance.json, native-provenance.json, apk-verification.json and
verification.json for exact evidence. Physical-handset, real Files/share chooser,
carrier and performance acceptance remain separate. MMS_READY remains false.

Source and APK retain their existing Library identities. SDK starter and Notes
.onklav-a are additional deliverables. Private publisher backup is kept separately
and must never be included in public archives. Only OnKlav is an Android APK.
