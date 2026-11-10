# Resume — 0.47.0-dev/code54, Files

The active request is a built-in SDK Files application, not game remakes. Read FILES-APP.md, source-verification.json and TODO.md. Games remain explicitly deferred and unchanged.

Files uses the same signed Wasm package model, renderer, menus and theme as the other SDK applications. It includes shared virtual folders, canonical Notes records, recoverable Trash, read-only public System packages and explicit Android folder access. Existing notes are copied once, with originals retained. Existing text/image export paths now target shared Documents/Images folders. Browser public HTTPS downloads target Downloads. Protected private service state stays private.

Build only stale inputs: scripts/build_sdk_apps.py --plan; scripts/build.py --plan. Files is package version7 (development signing iterations), nine built-in packages plus Counter. Preserve existing publisher and Android signer. The manifest packer injects publisher identity; the builder validates this against existing identity. No signing keys belong in archives.

Rust and mobile JNI were rebuilt for this feature. Reuse the current source-matched native proof for Kotlin-only continuation. Current framework-host, Python, Rust and APK results are in source-verification.json and docs/evidence/v0.47.0. Past failed/corrective attempts are not acceptance evidence.

A real phone is still required for Android picker/provider behavior, open handlers, account/media downloads, large content, accessibility and performance. Carrier, settings service, Walkie and full migration gaps remain open. Do not report this as a finished replacement of all original behavior.

Verified APK: 38,452,516 bytes; SHA256 `8dcdb5df1ad3c09cf9e0833526a5f6448d8a0ad194e197a8f243c9037e6496b9`. Existing signer retained. Optimized ARM64 assembly took 394.2 seconds; verification took 4.75 seconds in this restored environment. Future Kotlin-only checks can reuse the retained source-matched mobile and host JNI, SDK packages and Godot pack.

Outstanding 0.47 check: the framework-host test opens a note in Notes but stalls on the return to Files. This has not been resolved or accepted on a handset. The user requested packaging the existing verified APK now; do not restart repeated packaging/test loops before delivering it.
