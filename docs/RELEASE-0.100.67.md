# OnKlav 0.100.67-rc1

ARM64 debug test APK, version code 176. The existing APK signing identity is retained. Shroom 0.1.1 is bundled as the second game below Crash Test Panda on first inclusion. See SHROOM.md.

This quick APK reuses the byte-identical ARM64 Rust library from 0.100.66. The newer Browser download-routing change in core/src/sdk.rs requires a native rebuild and is pending in source. The Android package and game assets can be verified independently of that pending rebuild.

The quick build also reuses the existing shared game export for the other games. The newer built-in game sources are retained and need a full export. The Panda duel suite reached the 120-second build timeout during the attempted export. Shroom is independently signed and verified against the shared engine.
