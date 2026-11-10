# Shared text reader

A real SDK app using the shared read-only search/pagination screen and app-private
revision-checked position storage. Requires the working collection-enabled host,
not merely the released build78. Sample text is synthetic. No network or file
permissions are needed. This is not the editable Read Aloud app or an EPUB/PDF parser.

From the source root, run `python3 developer/onklav.py verify developer/examples/text-reader`.
Verification signs only a disposable local test package; it does not install on a
phone or create an APK/release. The scenario turns pages, finds emoji, jumps to a
match and reopens through the actual Rust broker to verify durable position.
