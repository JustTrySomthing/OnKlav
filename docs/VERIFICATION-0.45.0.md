# OnKlav 0.45.0 cleanup and verification

This checkpoint fixes the previously failing Groups workflow, cancellation
popups, contact-loading/retry states and a self-canceling startup bootstrap.
It stops idle game-catalog scrolling and adds privacy-safe SDK profiling hooks.
SDK builds now compile/sign only stale apps and reuse unchanged packages without
Rust or publisher keys. APK assembly rejects source changes during the build.

139 app host tests passed with zero failures/skips. The 12 shared checks passed
in the targeted 0.45 run and were reused unchanged by the full invocation.
16 Python build-planning/freshness checks passed. Runtime bundle class bytes
match current Kotlin output. Source-matched Rust JNI, SDK Wasm and Godot artifacts
were reused; their older test counts are not claimed as fresh tests.

The verified ARM64 optimized APK is 0.45.0-dev/code52, 38,250,173 bytes (38.3MB),
70.2% below the old 128.2MB debug APK. Existing app identity and signer remain.
SHA-256: db723cef236ae159853cbef224dd7ea61ae2bd23417e1336d2e739ba35ddd427.
Signature, ZIP/ELF16KB alignment, version/identity, non-debuggable/profileable,
required classes, JNI/internal packages and Godot content checks passed.
Initial assembly:206.84s; verification:3.02s. Warm unchanged assembly:20.62s;
verification:2.78s, total23.40s. These are cached-environment observations.

No physical handset or carrier was available. Startup/frames/navigation/memory,
full visual parity and wider feature migration remain unaccepted. MMS/default
role and untrusted Godot execution gates remain closed. Selective build logic
was tested with fixtures and real no-op reuse; no changed guest was compiled.
Detailed logs and JSON evidence are under docs/evidence/v0.45.0.

The extracted SDK starter passed plan and actual no-op reuse for all nine apps
without Android host files, Cargo or a publisher key.
