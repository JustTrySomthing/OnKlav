# 0.9.3 — Groups port

Continue from this source and host APK, not from the old placeholder. See
GROUPS-PORT.md for exact production paths, reference files and remaining gaps.
Groups management and the group conversation integration use Kotlin/Compose and
Rust; there is no old Java module or embedded old app. Only the host is an APK.

## Checks and the recovered test stall

Run scripts/check_android.py with the existing JDK/SDK/Gradle environment. No
software emulator is started. The full Gradle check has a 900-second ceiling,
30-second heartbeat and process-group cleanup; APK checks have a 60-second limit.

GroupsFlowHostTest operates the full five-tab host, group creation/name validation,
contact selection, member changes, member sheet, notifications/unread, pinning,
folders, search, bulk selection, restore and local group deletion. It uses actual
Compose and Rust JNI, with only the Contacts provider supplied as fixture data.
GroupProtocolHostTest passes real post/reaction data through the production JNI
bridge. Rust tests validate durable membership, thread rebinding, rename/delete
semantics, validation atomicity and safe carrier-envelope projection.

The first dialog checks stalled on Robolectric's pending measure/layout, not on
Rust state or an infinite application recomposition. The test now advances the
Compose clock, drains the Android main looper, and traverses the real offscreen
window roots explicitly. It never clears pending flags or substitutes UI state.
All Robolectric classes that load NativeCore use the same API29/native-graphics/
paused-looper/432x936 configuration so the runner reuses one compatible native
classloader. Different sandbox configurations must not load the same JNI path in
one JVM. Both app/shared Gradle tests track the JNI file as an input so native
changes cannot leave stale passing unit-test outputs.

These are Android framework-host tests, not an installed-APK/device/carrier run.
MMS_READY remains false; default SMS role was not changed and no real messages
were sent by the checks. Original games and SDK are not completed by this port.

## Restore after workspace loss

Use the current source plus the existing nine build-cache parts in
build-cache.json and the separate PRIVATE signing backup. Run
scripts/restore_build_cache.py PART_DIRECTORY EMPTY_ENV_DIRECTORY and source the
resulting env.sh. The reusable 0.9.2 cache still supplies the same JDK17, SDK36,
build tools35, Gradle8.11.1 and dependency/Android host-test caches. It contains an
older JNI library. The updated restore script selects the source-matched Linux
x86_64 library in core/host-libs instead, verifies its hash against
native-provenance.json, and places it in core/target/debug. Current Android JNI
libraries remain in android/shared/src/main/jniLibs. Never use the older cached
JNI with current Rust source or regenerate the signing key.

For Kotlin-only continuation run scripts/check_android.py. Every Rust source,
Cargo manifest/lock and the host/mobile libraries must match native-provenance.
For Rust changes install Rust 1.90.0 and targets aarch64-linux-android and
x86_64-linux-android, plus NDK 27.2.12479018; run scripts/build_native.py first.
That script runs Rust tests and rebuilds all three JNI libraries, then records
exact hashes. The NDK used here was Google's android-ndk-r27c-linux.zip, SHA-1
090e8083a715fdb1a3e402d0763c388abb03fb4e; preserve archive symlinks when extracting.
The NDK/compiler are not part of the old reusable environment cache.

The Godot pack was not edited or reexported this turn. Its hash matches the
previous verified APK and the current packaged APK. Godot source changes require
Godot 4.5.1 and the full scripts/build.py export/check workflow.
