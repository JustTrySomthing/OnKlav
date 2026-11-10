# Design, group activities and cleanup — bounded continuation

Authorized by Charles on 22 October 2026. Preserve 2.2.85 as read-only reference;
no legacy implementation or nested APK. One final host APK for this batch.

1. Recover source, reference and existing tools/keys after workspace maintenance.
2. Fix MMS partial-materialization cleanup and durable app-removal reconciliation.
3. Restore the original individual-member activity flow and concrete UI gaps.
4. Freeze source; compile, run focused failure checks only if necessary, run one
   integrated host check, build current mobile JNI and one verified APK.

No emulator boots. Every command is bounded. Rust initially passed after cleanup
implementation; it was rebuilt/tested after RSS package changes because the core
embeds those guest packages. Android-only mode reuses the already verified host
checks when building mobile libraries; it does not rerun green Rust tests.

A compile error in a new DialogAction argument was corrected. Failed diagnostics
are recorded; no repeated unchanged test runs, skipped tests, or weakened checks.
Physical UI/carrier/audio acceptance cannot be claimed here. Friend game
invitations depend on the original game ports and their protocol and stay
unavailable rather than launching engine exercises as original games.

The damaged transform entry was still referenced after targeted removal. A fresh
Gradle user/project cache is used, reusing only downloaded dependency artifacts;
no generated transform metadata is reused. This changes the failing condition
instead of repeatedly retrying it. NDK symlinks are restored from the official
checksum-verified archive before Android linking.

Current compilation and mobile linking passed after the isolated-cache and NDK
restoration fixes. The final host check uses the updated SDK packages. No Rust
tests were repeated during the Android-only linker repair.
