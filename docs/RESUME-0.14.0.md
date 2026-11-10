# Resume 0.14.0

Charles narrowed the task to one app at a time. This chunk converts YouTube;
Annotation is next, then Social and Walkie-Talkie. Do not start multiple apps.
Full rewrite/SDK/carrier/game completion flags remain false.

Current host: 0.14.0-dev/code21, app.onklav.home.prototype.rewrite, retained signer.
APK SHA-256: bae905c8285fcd700c7327bb0a355f1b2b6e8691c0492d26a989fe3c33eb68f5.
81 tests pass: Rust45 plus Kotlin36. Both mobile Rust ABIs and Linux host JNI were
rebuilt from current source. APK/package signatures/bytes and 16KiB alignment
pass. No physical installation, live website, audio, carrier or performance
acceptance occurred. No emulator was booted.

YouTube is a real signed Wasm package. Read YOUTUBE-PORT.md for its actual
controller/data/host paths and explicit reference/device/data-cleanup gaps. The
obsolete BrowserScreen and its YouTube switch are removed, not used as a port.
Notes/RSS/ReadAloud/Browser/YouTube all use the same SDK installer/broker. Curated
missing entries bootstrap once; previously removed entries stay removed. Use
Settings > Apps to reinstall/manage/export a package. Never produce per-app APKs.

Use RESUME-0.11.0.md for restored tool locations and private keys. Source workspace
restored-env/env.sh and /root/.cargo/env, export ONKLAV_APPS_SIGNING_KEY to the
existing migration-private/apps.publisher.key. Do not regenerate either signer.
Run scripts/build_sdk_apps.py after guest changes, build_native.py after core
changes, then check_android.py. Debug R8 remains disabled. Each Android phase
is bounded separately; do not revive the old emulator retry loop. Compile fixed
a missing CompactAction import and local helper declaration order in this chunk;
final compilation/tests/APK/verification passed afterward.

Read AGENTS.md and the status/SDK/next-app reference before coding. Source package
excludes generated UI screenshots; retain logs, original assets, source, exact
native binaries and package provenance. APK/source/SDK plus the internal YouTube
package are the deliverables; private signing keys remain outside them.
