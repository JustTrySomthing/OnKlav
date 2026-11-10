# OnKlav 0.44.0 — build and performance checkpoint

The optimized ARM64 APK is **38,246,689 bytes (38.2 MB)**, down from
128,249,221 bytes: **70.2% smaller**. Package/code: app.onklav.home.prototype.rewrite / 51.
The existing Android signer is preserved. APK SHA-256: `cd92cd000447f170f3a9b9989f21f825d600ca11f0a73d71cfc8df2853082daa`.

Changes: hash-checked reuse of unchanged native/SDK/game artifacts; separate test
phases; phone-only packaging; R8/resource optimization; exact icon artwork with
the unused extended library removed; no artificial first-draw hold; cached setup
completion with live authorization and invalidation for registry/package changes.
Verified native artifacts are retained in the source checkpoint.

One unchanged rebuild and APK verification took **25.39 seconds**
(20.67s assembly, 4.72s verification).
This is not a claim that an edited-code build or fresh tool restoration takes
that time. The first complete final changed-input optimized build took 286.92s
plus 3.34s verification, excluding initial environment/dependency diagnostics.

APK checks passed: signature continuity, version/identity, ARM64-only packaging,
non-debuggable/profileable manifest, JNI and internal-package/game bytes, defined
classes, ZIP integrity, ELF and ZIP 16 KB alignment. Release lint ran in assembly.

**Functional evidence: 17 of 18 unique Kotlin/framework-host checks passed,
plus five Python artifact-reuse checks.** The two new bootstrap checks pass
after correcting their Robolectric native-mode setup. The Groups contact-sheet
timeout reproduced in both the initial selected run and one corrective subset.
It remains unresolved; the same class of failure is documented in earlier
checkpoints, but its cause and relationship to these startup changes are unproven.
No tests were skipped or their timeouts increased, and no additional retries
were made. This is not a fully green host-suite claim. The source is explicitly
saved as an incomplete checkpoint with its known failure retained.

No physical phone, carrier or before/after responsiveness benchmark was run.
The previous transport/game/voice and SDK-authoring limitations remain.
No permission, signing, publisher or transport gate was relaxed.

See RESUME-0.44.0.md and BUILD-AND-PERFORMANCE.md inside the source archive
for exact continuation steps.
