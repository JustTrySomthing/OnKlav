# Resume 0.12.0

Read RESUME-0.11.0.md for recovered tool/cache/key locations and bounded build commands. Current version 0.12/code19 has Notes, RSS and Read Aloud as actual signed Rust/Wasm SDK apps. Five apps remain to convert: Browser, YouTube, Annotation, Social, Walkie-Talkie. Read READER-PORT.md/RSS-PORT.md/REWRITE-STATUS.md before claims. Full rewrite is false.

67 tests pass: 39 Rust plus 28 Kotlin. Native provenance is source matched for host/arm64/x86_64. Exact three bundled SDK package hashes and Wasm bytes are checked in final APK. Android signing/publisher keys remain outside source and unchanged. APK resources were rebuilt after adding the TTS visibility query; compiled manifest assertion and APK checks pass. Tests use Compose accessibility actions, not physical handset input or audible playback.

The last UI test repairs were waiting for settings closure before Back, checking EditableText rather than the hint after Clear, and clearing a closed SDK session frame before reopening. No emulator boot is needed for these checks. Continue one bounded app/service chunk; preserve checkpoint APK/source before further work. Original bundled voices, private data adoption and reference layout/handset acceptance remain in the gap ledger.
