# 0.66.0 — reviewed source fixes

Source code74; no APK requested or produced. Last delivered APK remains
0.61.0/code69. Read CODE-REVIEW-0.66.0.md for concrete findings, changes, checks
and limits. The active TODO remains separate from the historical migration ledger.

Core content::unique now preserves the first complete scalar and avoids trailing
whitespace in a truncated extension. A single HashSet of occupied names replaces
repeated scans. Existing maximum-name tests include supplementary-scalar stems;
a new 9,000-collision case covers correct next-name selection. One full Rust run
passes 187 tests and rebuilds Linux JNI. Mobile JNI remains deferred until needed
for a requested APK. Do not use stale mobile native outputs as current proof.

DriveDocuments owns guarded partial-output cleanup. Same-document aliases reject
before stream opens; precondition/source failures never delete the destination.
UI errors and shortcut-write failures no longer remove a completed copy. Busy and
cancellation handling cover metadata selection; provider queries/commits use IO.

DriveSharing serializes upload/removal around its one durable pending journal,
checks request cancellation, moves journal work off Main and throttles progress.
Its bounded decoder retains non-JSON HTTP error status but rejects malformed
success and oversized data. Users can choose another account without first
reauthorizing an unavailable saved one. No tokens are persisted and no upload,
permission change or message is performed by tests.

Single focused Android invocation: 38 app + 12 shared tests pass in 30.0 seconds;
current runtime classes match compiled Kotlin. Total fresh checks: 237. No repeat
full UI suite/emulator, games, publisher/signing changes or APK. Existing account,
handset/carrier and independent SDK release qualifications remain unclosed.

Environment restored after workspace reset:
source /workspace/scratch/ed752e8a1957/build-env/env.sh
source /root/.cargo/env
Use scripts/check_android.py --tests-only with specific test selectors. The SDK
and game artifacts are unchanged; retain proofs. No need to restore signing keys
for tests-only work. Canonical source/TODO/SDK artifacts were replaced together.
