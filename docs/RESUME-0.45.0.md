# OnKlav 0.45.0 — reliability, lifecycle and selective builds

Charles authorized a broad but bounded cleanup/performance pass in the shorter
conversation. Preserve the current interface, app identity, signing certificate,
SDK publishers, user data and all transport/untrusted-code gates.

## Implemented

- Groups, Messenger and SDK host/model coroutine boundaries rethrow normal
  cancellation rather than showing it as an error or replying to an abandoned
  host action. The old Groups trace showed a spurious cancellation error over
  the member sheet. The original complete Groups workflow now passes.
- Contact selection has truthful loading, unavailable/error and empty states,
  retry, and resume/permission refresh. The members sheet offers retry too.
  A new UI test exercises provider failure, retry and actual member selection.
- Bundled-app bootstrap is keyed to the screen lifecycle, separately from
  catalogue refresh. Its own installation epoch changes no longer cancel it.
- SDK operations expose privacy-safe trace sections for profiling, without
  recording text, contacts, IDs or guest state. Disabled tracing takes the
  direct path. The Wasm instance/JSON architecture is not speculatively replaced.
- Game-catalog auto-scroll stops once the scroll boundary consumes no movement.
- The SDK builder fingerprints each package and only compiles/signs stale apps.
  Missing/corrupt bundled copies can be restored without Cargo or signing.
  Partial builds retain individual proofs and cannot bless unbuilt stale apps.
  Updates retain identity/publisher and enforce newer versions for changed code,
  manifests or package bytes. See SDK-BUILD-WORKFLOW.md.
- Host assembly snapshots production inputs and checks them after compilation,
  rejecting a mixed checkpoint if code/resources/artifacts change while building.
  SDK starter packaging includes the new helper modules and workflow notes.

## Verification scope

A complete app host invocation passed all 139 app checks (zero failed/skipped),
including both Groups workflows, plus the unchanged shared suite's 12 checked
cases. Sixteen Python build-planning/freshness/update-guard checks pass. The
initial targeted Groups/bootstrap check also passed before the broader run.
Runtime bundle bytes match the compiled current Kotlin classes. No complete
suite was repeatedly retried until green.

Native Rust source, signed SDK app programs and the Godot pack are unchanged,
source-matched and reused; their prior tests are not counted as fresh runs.
The selective planner is exercised with fixture projects and real no-op package
reuse. It does not claim a changed guest compilation was performed here.

Physical handset/carrier, two-phone audio, navigation/frame/memory performance,
precise reference appearance and the remaining wider migration are still open.
MMS/default-role and untrusted external Godot execution stay disabled. Host tests
are not installed-APK or handset acceptance. The app-authoring language/workflow
and per-event Wasm instance/JSON overhead still need real-device assessment.

Current source is 0.45.0-dev/code52. Final APK bytes, hashes and verified build
results are in apk-verification.json and VERIFICATION-0.45.0.md after assembly.
The prior 0.44 failure is retained as history, not the current test outcome.

Verified APK: 38,250,173 bytes; SHA-256
`db723cef236ae159853cbef224dd7ea61ae2bd23417e1336d2e739ba35ddd427`.
Initial assembly 206.84s plus verification 3.02s; unchanged warm assembly
20.62s plus verification 2.78s (23.40s total). These are environment-specific
build measurements, not handset benchmarks.
