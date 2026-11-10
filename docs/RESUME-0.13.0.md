# Resume 0.13.0

Use RESUME-0.11.0.md for tools/cache/private key locations. Source restored-env/env.sh and /root/.cargo/env from workspace. Current signed host is version 0.13.0-dev/code20. Notes, RSS, Read Aloud and Browser are real internal Rust/Wasm packages; four apps remain: YouTube, Annotation, Social, Walkie-Talkie. Read BROWSER-PORT.md for production path and exact gaps. Full rewrite is false.

74 tests pass: Rust42 + Kotlin32. Kotlin compilation, tests, APK build and verification are separate bounded phases; final timings are in verification.json. APK retains the existing signer and both exact Rust ABIs. All four bundled SDK packages/Wasm hashes are checked. No emulator was booted; host WebView lacks MULTI_PROFILE and website acceptance remains pending. Debug R8 stays disabled.

Browser's UI test Back selector matched both header and page buttons; it now selects the header. Final review made clipboard grants mandatory for all generic web components and added live native menu/key/accessibility clipboard gates. RSS publisher uses its own profile and the gated class too. See the explicit saved-data/native-profile cleanup and exact-style gaps, do not claim a full purge or finished Browser parity.

Next SDK app: original YouTubeViewer/private/bookmark/history/player design, not merely another generic Browser label. Browser/profile primitives are now available for that work. Reuse existing publisher/Android keys; do not regenerate them. Continue one bounded app/service chunk and keep a verified APK/source checkpoint before further changes.
