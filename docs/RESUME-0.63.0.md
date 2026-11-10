# 0.63.0 — finish built-in app-document backup

User explicitly wants backup finished quickly and no APKs. Keep that preference
until explicitly changed. Last delivered APK remains 0.61.0/code69. Source now
0.63.0-dev/code71. Host-only Rust rebuild/tests are sufficient for this checkpoint;
Android native rebuild is deferred until an APK is actually requested. Stale
mobile JNI is excluded from source packaging. Do not relabel an older APK.

One Settings App Documents Backup combines the eight built-in document stores.
Existing host ZIP service streams binary Files/JPEGs; combined records include
Notes and typed app metadata. Full member/graph/hash/dimension/identity/quota
validation precedes per-store commits. Files/Notes combined row limits are checked
together. Per-store atomic writes and payload reuse allow retry after partial I/O.
Combined manifest cap is 96 MiB inside the existing bounded 512 MiB archive.

Walkie backup strips SIM/session/floor/outgoing-attempt/transport state. Restored
clips have a durable passive flag, synthetic local session binding and no queued
state. Host attempts/receipts reject them, autoplay and outgoing heard receipts
are suppressed, and normal expiry does not silently erase restored recordings.
Existing clips/deletion proofs and preferences/recipients win; imported recipients
have no selected recipient or sending SIM. No connection or send is started.

Messages metadata/history and Appearance retain existing separate backup paths;
permissions, website credentials, packages and arbitrary third-party runtime state
are excluded. This completes the built-in app-document backup implementation.
No more backup batches are queued; return to other unresolved non-game port work.
Only core tests, one combined provider/JNI round trip and one button-navigation
check are needed here. Final actual evidence is in source-verification.json.

Final: 185/185 full core checks and the two selected Android checks pass.
Combined provider/JNI check passed in a 129.4s compile/test invocation; the sole
button check passed in 29.2s. No APK or mobile native rebuild was performed.
Backup implementation scope is closed; save the current source and move on.
