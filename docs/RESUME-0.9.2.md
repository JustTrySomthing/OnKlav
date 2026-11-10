# 0.9.2 — verification recovery and message reminders

The saved 0.9.1 source/APK were restored. The prior stalls were dominated by
rebuilding a lost temporary toolchain and attempting full Android boots without
KVM. Those attempts never installed the APK. No emulator is started by the new
scripts/check_android.py workflow. It checks retained mobile-native provenance,
runs real Android framework/Compose host tests, builds the APK and checks its
signature/structure. A timeout kills the entire process group; progress prints
every 30 seconds. Do not resume the old software-emulator retry sequence.

The reminder flow now passes a framework-host UI check in seven seconds. It
operates the actual native date/time widgets, real Compose choices, production
ChatStore/Rust JNI storage, alarm guard and manager actions. Android callbacks
are drained explicitly; the first failure used an Espresso root selector that
could not find the native dialog in Robolectric. Tests are not carrier/device
or installed-APK acceptance. See verification.json for all 33 passing tests.

Open Message now carries the message key and original date through manager and
notification intents. Separate reminder PendingIntents retain their own target.
Messenger resolves the requested thread directly, rather than searching only
the newest 1000 threads. It scrolls to the matching loaded message and rejects a
reused ID with a different date. Older history uses the existing Load Earlier
Messages control; the pending target survives loading earlier messages. Deleted
or unavailable messages display an error instead of jumping to a different ID.

## Fast recovery after another workspace reset

Restore this source, all cache parts listed in docs/build-cache.json, and the
separate PRIVATE OnKlav signing backup. Never generate a new signing key.
Run scripts/restore_build_cache.py PART_DIRECTORY EMPTY_ENV_DIRECTORY. Source
the resulting env.sh, then run python3 scripts/check_android.py. The cache has
JDK17, SDK36/build tools35, Gradle8.11.1, dependencies, the Android host-test
runtime and the source-matched host JNI library. It contains no signing keys,
emulator or phone data. Cache checksums and safe tar extraction are mandatory.
Rust source changes require the complete native build in scripts/build.py.

Only OnKlav produces a product APK. Full Messenger, carrier acceptance, MMS
(default-role gate stays off), Groups/social/walkie, SDK, original games and
remaining visual/accessibility acceptance are still incomplete.
