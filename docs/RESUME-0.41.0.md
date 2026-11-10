# OnKlav 0.41.0 — authorized four-area continuation

This is a development checkpoint, not a completed migration. Charles authorized
Messenger, Groups, Walkie and existing app integration together. See
EXECUTION-PLAN-0.41.0.md for the bounded execution budget.

## Implemented production paths

- Messenger: validated carrier callbacks enter a bounded fsynced receipt journal
  before Rust outcome reconciliation. Recovery replays outcomes without sending
  again. Raw SMS identity includes subscription and format. MMS inserts reject
  partial existing-row mutation and roll back a newly created parent on failed
  child insertion. Fresh decoded attachments are cleaned after provider copying.
- Groups: provider history includes incoming MMS sender identity. Rust discovers
  reaction parent targets; Kotlin resolves older SMS/MMS parents within the same
  thread, then passes context through the existing Rust feed projector. Unknown
  reaction senders remain visible. Debounced provider history search reports its
  bound; unread actions update real provider state.
- Walkie: callback outcome and response are durable before core reconciliation;
  denied/revoked setup retains the journal. A persisted, coalesced recovery job
  reconciles callbacks and expiry; ambiguous sends are not automatically resent.
  Clip dispatch is serialized with package revocation/removal. Valid late audio
  for an ended matching session becomes an ordinary voice message, without a
  false heard receipt; mismatched peer/SIM/session/hash/audio is rejected.
- Existing apps: Notes has 40-row paged search using a new backward-compatible
  Rust records.page operation. Browser links from internal apps can open the
  signed internal Browser and return to their source. Reader/Messenger share
  offline speech preferences and audio ownership. Own prior rewritten Reader
  drafts can be adopted without deleting the original. Annotation preserves its
  viewport across resize/recreation. Delete-data rotates an app's website profile
  immediately and queues old loaded profiles for deletion after restart.

## Explicit acceptance still open

No phone, SIM, carrier, microphone/speaker pair or signed-in account is available
here. MMS_READY and default-role requests remain disabled. Sending/receiving,
notifications under device power management, dual-SIM behavior, real provider
failure modes, two-phone Walkie latency/focus/background operation and exact
reference visual/accessibility behavior still need handset acceptance. A job's
requested delay is not a guaranteed real-time deadline under Android scheduling.

Additional implementation review remains for atomic cleanup when MMS decoding
fails after emitting only some attachment blobs, and native cleanup failures
after a core package removal has already committed. Those paths are not claimed
as fully atomic or independently security-reviewed.

Groups friend activities, full original app/service parity, bundled voice asset
identity, independent cleanup/security review and the original games/creator
expansion remain outside claims of completion. Social is still the authorized
Hello World placeholder. Only OnKlav is an APK; all eight apps remain signed
internal .onklav-a packages. No authored Java source or old Inner Circle binary
was introduced. Upstream Kotlin/Android still requires JVM/framework binaries.

## Reproducibility and testing

Rust/core, mobile native libraries and SDK packages were rebuilt once for this
batch. A stale host-native build record stopped the first Android check before
any tests; the full native proof was verified against current source/library
hashes and reused. build_native.py now updates both proofs on a full build.
Final test counts, APK hash and evidence are recorded in verification.json and
the delivered verification report after the integrated check.

The first integrated Kotlin run passed 131 app tests and failed the new JNI
recovery fixture because its Robolectric sandbox used a different classloader.
Aligning the fixture with the existing native-test sandbox fixed it; the focused
recovery run and final complete host run passed. No assertions were removed.
APK verification then detected obsolete Rust bytes in Gradle copy/merge output;
only identified generated native packaging directories were discarded. The
packaging-only recovery did not repeat Rust or Kotlin tests. The checker now
clears these stale packaging intermediates before assembly. Original failed
logs/results are retained alongside the final evidence.
