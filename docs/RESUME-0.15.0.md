# Resume 0.15.0 — Annotation, source only

Charles changed the workflow: one SDK app at a time, compile/test/save source,
then ask approval before the next app. No per-app APK build/upload. This chunk
converts Annotation. Social and Walkie-Talkie remain. Do not start Social without
the requested next-app approval. Full rewrite and full SDK flags remain false.

Annotation.onklav-a is signed Rust/Wasm, with core/src/drawing.rs storage/editing
and generic Kotlin canvas/image codecs/brokers. The old AnnotationScreen and
route are removed. Read ANNOTATION-PORT.md for production paths, original limits,
verified features and precise handset/lifecycle/style acceptance gaps.

50 Rust + 40 Kotlin tests pass (90 total). Logs are docs/evidence/v0.15.0.
Current source/UI is 0.15.0-dev/code22, but NO corresponding APK exists.
The last verified APK remains 0.14.0-dev/code21, SHA-256
bae905c8285fcd700c7327bb0a355f1b2b6e8691c0492d26a989fe3c33eb68f5.
Keep its APK/signature reports historical. Current annotation-verification.json
and host-native-provenance.json describe source checks and Linux JNI only.
The source-only ZIP omits stale Android Rust binaries; it must not use the old
mobile provenance as evidence for current source.

Use RESUME-0.11.0.md for restored tools/private-key locations. Source workspace
restored-env/env.sh and /root/.cargo/env; export ONKLAV_APPS_SIGNING_KEY to the
existing migration-private/apps.publisher.key, never regenerate signers.
Run build_sdk_apps.py after guest changes, build_native.py --host-only after core
changes, then check_android.py --tests-only. Compile shared libraries and app
in separate bounded phases. For a requested phone/batch build, run full
build_native.py for both Android ABIs, then check_android.py without --tests-only.
Re-verify all six bundled package bytes/Wasm, both native ABIs and retained signer.
Annotation declares capabilities the 0.14.0 host cannot support, so do not offer
it as installable in that old host.

Dialogs regression fixed: the shared ReferenceDialog already scrolls its body;
do not add another unbounded verticalScroll around SDK menu nodes. Bottom Tools
positioning happens once through a keyed effect, not on every recomposition.
Compose test waits for the actual library query field, not title text also
visible on the main screen. No emulator was used. Default SMS role/MMS gate
and Godot code are unchanged. SDK/game/security audit and device work remain open.
