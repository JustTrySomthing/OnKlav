# Vault three-question recovery

User-requested recovery replaces password-only access with two authorized access routes:
password or all three configured security answers. It does not provide a master key,
plaintext recovery copy, remotely stored secret or Keystore bypass.

## User flow

1. New Vault: enter/confirm password, then select one question in each of three groups
   and enter all three answers. Complete is disabled until every answer is nonblank.
   No Vault file is created until Complete atomically commits the encrypted document
   and both key wraps. Interrupted pre-commit setup restarts safely without an orphaned
   password or existing data to strand.
2. Existing password-only Vault: the owner explicitly requires this to count as failed
   setup. Start again with a new password and all three recovery answers, without asking
   for the forgotten password. Keep the old encrypted file untouched until Complete;
   then atomically replace the incomplete Vault with a new empty encrypted document.
   This restarts setup rather than recovering the old document. A structurally damaged
   header fails closed; a completed recovery-enabled Vault cannot be overwritten this way.
3. Complete returns to locked login with Unlock or Reset password. A crash after successful
   commit has the same result at next launch. Completing setup never opens the editor.
4. Reset displays the three saved questions, requires all answers and a matching new
   password twice, and atomically rewraps the existing data key after authenticating and
   parsing the whole encrypted document. Success returns to login and requires the new
   password; old password no longer unlocks the current file. Wrong answers/corruption
   cannot replace it. No individual answer-correctness feedback is exposed.

The nine question choices match the requested food, entertainment and childhood groups.
Answers use NFKC, Locale.ROOT lowercasing and collapsed whitespace for matching; passwords
remain exact. Passwords and answers have visibility buttons and are transient Compose/model
state, never rememberSaveable or saved-instance state. Both setup and reset remain scrollable
with the keyboard and large fonts. Existing secure-window, private editor, save/autosave,
background lock, back/Cancel and image/link behavior are retained.

## Encryption and compatibility

The existing OKVAULT1 outer identity and encrypted Tink document are unchanged. Only Vault's
wrapped-key field gains a bounded OKVREC01 envelope containing the original password wrap,
three question indexes and an independent password-equivalent recovery wrap. All three
question indexes and normalized answers are length-delimited and domain-separated before
SHA-256; the resulting secret feeds the existing salted 600,000-iteration PBKDF2-HMAC-SHA256
and AES-GCM key wrapping. No raw answers, answer hashes, password, plaintext document or
unwrapped key are written. Decrypted documents remain in memory only. Ordinary password
change retains the recovery wrap. The wrapped key and data stay in one AtomicFile transaction.

App Documents Backup continues carrying exactly vault/document.bin as opaque ciphertext;
recovery therefore follows the document without adding a new native backup payload or
changing Rust. Old password-only files restart setup without requiring their password. Older backups
retain the password/recovery configuration from when they were made; a reset cannot rewrite
copies already exported. Recovery-enabled files require the updated app to unlock.

## Limits

Anyone able to guess all three answers can reset the password. Combined answer strength may
be much weaker than a long password. In-app failures share a 30-second cooldown after five
failures in the current model session; process restarts/copied backups can bypass this local
counter. This is not offline brute-force prevention or independent security certification.
JVM strings/IME buffers cannot guarantee immediate physical memory erasure. No phone/IME,
recents or independent security qualification is claimed by host tests.

## Current follow-up

See VAULT-STAMP-CORRECTIONS.md and vault-stamp-verification.json for the owner-requested incomplete-setup restart and its regression checks. The original validation below is historical.

## Validation

Focused Vault tests cover password/recovery access, unchanged data on wrong answers or
corruption, authenticated legacy enrollment, preserved recovery after password change,
interrupted setup, relocking, native App Documents Backup round trip, answer choices,
matching input requirements, autosave, background lock and small-screen scrolling. Actual
results and build logs are retained under vault-recovery-checks. No APK was requested.

Result: all15 selected tests passed (5 recovery,5 prior crypto/data checks,5 UI), with production and unit-test Kotlin compilation successful. The first restored-cache snapshot failure and final successful log are retained. Layout previews were inspected. No Rust change/rebuild, APK, full lint or physical-device/security certification is claimed.
