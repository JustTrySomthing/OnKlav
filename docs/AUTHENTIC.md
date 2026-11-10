Current recovery/setup behavior: see MESSENGER-AUTHENTIC-UI-REFINEMENTS.md. Setup now requires three recovery answers and supports authenticated password reset; earlier password-only guidance below describes the initial implementation. Existing accounts are preserved during enrollment.

# Authentic

Included OnKlav authenticator app with a theme-aware key icon, familiar issuer/account/code list, countdown rings, search, pinning and manual ordering. It is an original implementation; it does not embed Google Authenticator or use its private databases/cloud service.

## Accounts and transfer

- Offline RFC 4226 HOTP and RFC 6238 TOTP, SHA-1/SHA-256/SHA-512, six/eight digits, custom TOTP periods (1–3,600 seconds) and non-negative signed-64-bit HOTP counters. Counter advancement is persisted before the new code appears.
- Camera QR scanning, QR image import through OnKlav Files, standard otpauth setup links and manual Base32 keys. Advanced choices stay behind Advanced options. Account/service labels can be edited; secret parameters are not silently changed by label edits.
- Google Authenticator migration QR version 1 is parsed with bounded protobuf/base64 readers. All batch fragments must be present before the explicit import confirmation; mismatched batches and unsupported enums reject. Duplicate keys are kept once, and an older imported HOTP counter cannot move a current counter backward. The merge is indexed rather than quadratic. Up to 500 accounts, bounded payloads and lazy lists.
- Individual transfer QRs require password verification, are displayed in the protected window and are never automatically written as plaintext images. Screenshots/recents capture use FLAG_SECURE.
- Codes copy with Android’s sensitive-clipboard marker. Explicitly copied codes survive leaving/locking for pasting into another app; an owner-token-checked best-effort timer clears them after 30 seconds without clearing another app’s newer clipboard content. OS behavior/process death can affect clipboard timing. Copy computes from the current stored account and current clock rather than a potentially stale display frame.

Google migration is a compatibility implementation against the observed version-1 format, not a promise for future proprietary formats. Camera/real-provider acceptance remains pending. Google cloud synchronization, push approvals and proprietary token formats are not implemented. If the old authenticator blocks QR screenshots, use a second trusted display/device or re-enroll through each service; Authentic does not bypass another app’s screenshot protection.

## Encryption, locking and backups

Authentic requires a confirmed password of at least eight characters at setup. Ciphertext is stored only in host-private `authentic.bin`, hidden from Files. It reuses OnKlav’s existing password-wrapped Tink streaming-AEAD envelope, PBKDF2-HMAC-SHA256 (600,000 iterations), AES-256-GCM key wrapping and authenticated streaming encryption, under a distinct Authentic identity. No password/plaintext keyset/seed file, software-key bypass or secret saved-instance state is introduced. Secret/error string representations are redacted; input errors do not echo setup links. Working byte/password/keyset buffers are cleared where possible; immutable JVM strings cannot be guaranteed zeroized. Atomic-file transactions preserve earlier data on failed writes/unlocks.

Leaving the mini-app or true OnKlav backgrounding locks it, drops account/import/QR state and closes the working keyset. An application lifecycle tracker preserves only foreground internal file-picker handoffs. Failed unlocks throttle on monotonic time. A forgotten password has no bypass; typed DELETE plus explicit confirmation can erase only Authentic’s local accounts, preserving other OnKlav data. This destructive path is not account recovery. Keep service recovery codes or a working second authenticator.

Encrypted backup export and restore use OnKlav Files. Backups are portable with their original password; authenticated restore merges rather than overwrites existing unlocked accounts. Fresh installation restore verifies the entire stream and identity before installing it. Wrong passwords, truncated/modified backups and another app’s envelope reject without replacing the local store. Password changes retain accounts; older backups retain their original password. A file-picker return after a locked export asks the user to unlock and retry rather than claiming success.

Private Authentic data is not newly added to the Rust App Documents backup schema. Export an encrypted backup to Files; it can then be included by the existing Files backup system. There is no automatic cloud backup or remote password recovery.

## UI and platform

Large grouped codes adapt to narrow/large-text screens; source labels truncate while the code remains visible. Search and focused add/edit dialogs keep the everyday list clean. Password fields have visibility controls and explicit minimum-length guidance; setup scrolls above the IME. Clipboard copy is user initiated; seeds are never copied automatically. Add/remove/rename/pin/move/counter operations save before publication. Deletion explains that local removal does not disable the service’s two-step verification. The app uses phone time and offers the automatic date/time settings page when needed.

CameraX is reused. ZXing core 3.5.4 is the only new library, for offline QR read/write; there is no ML model, external scanner app, remote QR decoder or new permission. The scanner owns/unbinds only its own camera use cases, closes every analysis frame and handles permission denial with manual/image alternatives. Apache license and upstream notices are retained under `third_party/zxing` and packaged licenses. Single APK and original signing identities are preserved. No native/Rust/SDK/game source or protocol changes.

## Sources and qualification

- RFC 4226 and vectors: https://www.rfc-editor.org/rfc/rfc4226
- RFC 6238 and test-vector correction: https://www.rfc-editor.org/rfc/rfc6238 and https://www.rfc-editor.org/errata/rfc6238
- Google key URI format: https://github.com/google/google-authenticator/wiki/Key-Uri-Format
- Google transfer instructions: https://support.google.com/accounts/answer/1066447
- Observed migration wire schema (unofficial, original reverse-analysis source): https://github.com/qistoph/otp_export/blob/master/OtpMigration.proto
- Offline QR library: https://github.com/zxing/zxing

See `authentic-verification.json` and `authentic-checks` for results and source hashes. Native host checks use public/generated test secrets; preview screenshots contain no real accounts. Physical Galaxy/Android-15 camera, IME, lifecycle, accessibility, large-account performance, release-shrinker and real-provider migration acceptance, plus independent security review, remain open. Verify codes with each service before removing the previous authenticator. No APK built for this task.

Final validation: all 18 focused checks passed. The final two password-guidance string changes were hash-normalized against the tested source and followed by a successful production Kotlin compilation. Logs/XML and both source-hash snapshots are retained. These are host checks, not physical-phone or independent security certification.
