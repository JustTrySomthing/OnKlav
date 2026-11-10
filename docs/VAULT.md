# Vault and retirement of new folder protection

Vault is one included host utility with a lock icon. It is not a Files document,
SDK guest, separately signed APK, password autofill service, or password manager.
It reuses the existing password-wrapped Tink streaming encryption primitive.

## User experience

- First open requires an 8–256 character password twice with visibility controls, then
  one question from each of three groups and all three answers. Complete atomically
  creates the encrypted Vault and returns to password login; no editor is opened by
  setup or reset. Incomplete new setup has no persisted password/data and starts again
  safely after interruption. Existing vaults require their current password once,
  then recovery setup before editor access. A forgotten legacy password cannot be
  recovered until recovery has been configured.
- Later visits show Unlock and Reset password; Cancel returns to the previous page.
  Reset requires all three answers and a matching new password twice. Answer matching
  applies Unicode NFKC, case folding with Locale.ROOT and whitespace trimming/collapsing;
  password matching remains exact. Five failed in-app attempts impose a 30-second
  cooldown for the current model session. This does not prevent offline guessing.
  Choose private answers: anyone who knows/guesses all three can recover the data.
- Compact Bold, Italic, Underline, Strikethrough, Link and Image controls reuse
  Write's native rich text engine, selection handling and validated document format.
  Clipboard copy/paste is available as an intentional host action. Paste imports
  plain text so foreign formatting/unsafe links cannot corrupt the stored document. No online
  proofreading or external text-processing actions are invoked.
- Autosave after 700 ms of inactivity encrypts on IO. Explicit Save gives visible
  Saved status and a short confirmation. Lock & close/back flushes the latest
  document before dropping the key and editor. Background stop also flushes and
  locks. No plaintext document cache, thumbnail, migration or Write recent-list entry.
- Save errors retain the current draft in memory behind the password screen;
  leaving through the normal back action is blocked until it can be saved.
  Abrupt power loss/process death before a successful save can lose unsaved edits;
  a previous successful atomic encrypted save remains intact.
- Change password requires the current password and matching new entries and retains
  recovery. Reset rewraps the existing key without discarding data. Older backups
  retain their original password and the recovery configuration present when made.
- Up to 500,000 text characters, 12 embedded images, and 8 MB serialized content.
  Image copies are embedded inside the encryption; the source selected in Files
  is not deleted or encrypted. An internal Files picker trip also locks Vault;
  reenter the password to finish inserting the selected image.

## Security boundary

`filesDir/vault.bin` is an atomic, host-private ciphertext, never indexed by
Files or exposed through its broker/provider. The password derives a 256-bit
wrapping key using PBKDF2-HMAC-SHA256 with 600,000 iterations and a random salt;
AES-GCM wraps a randomly generated Tink AES256_GCM_HKDF_1MB streaming keyset.
A stable Vault identity, fresh per-save generation and authenticated stream bind
contents to the intended document. A wrong password or tampered/truncated stream
cannot publish parsed text. Decryption is bounded to 8 MB and occurs only in
memory; key byte arrays and transient serialized arrays are cleared where the
runtime permits. Android/JVM immutable strings, OS/IME buffers and GC cannot
provide a guarantee of immediate physical memory erasure.

FLAG_SECURE covers the Vault page and inherited dialogs, including the unlocked
editor and login, to suppress screenshots/recents capture. Autofill, content capture,
Assist text structure and third-party selection processing are suppressed. The
editor requests no personalized IME learning. This does not make a compromised
OS, malicious keyboard, explicitly enabled accessibility service, camera, or
user-copied clipboard data safe. There is no network call, master password,
biometric/Keystore bypass, plaintext recovery copy or stored password/answer verifier.
A weak password or guessable recovery-answer combination remains susceptible to
offline guessing of a stolen backup. Security is limited by the easier route.

## Existing folders

Files no longer offers protection for an unencrypted folder. A stale protect
request explains that the feature moved to Vault. Existing encrypted folders and
legacy backup guards remain readable/unlockable/manageable with their original
password; Remove protection is retained to avoid stranding user files. They are
not automatically decrypted, deleted or imported into Vault. The legacy engine
and core format remain solely for compatibility/recovery and historical checks.

## Backup / restore

Settings > App Documents Backup includes Vault ciphertext as an opaque payload.
The archive as a whole remains unencrypted; the Vault payload is still encrypted
with its password or configured recovery answers. File names, passwords, answers, plaintext and unwrapped recovery keys are not
added to the manifest. Vault is not included in the separate Files-only backup.

A fresh installation restores Vault without needing an old device's Keystore.
Existing Vault data is kept, even when different, following the app's non-destructive
merge policy. Restoring a previous Vault therefore requires an installation with
no Vault yet; this is explicit in backup UI. Backups made before Vault are still
accepted. Unexpected payloads/header identities, incomplete atomic saves and
unsafe destination paths are rejected. Save/backup races are bounded by atomic
publication, size/hash checks, and rejection of an in-progress save.

## Validation

See vault-verification.json and vault-checks. Android production compilation,
focused host crypto/UI checks, native backup regression checks and matching
host/ARM64 JNI rebuilding are required. No new APK is requested or built.
Physical phone/IME/recents/file-picker acceptance and independent security
qualification remain open; host tests are not a security certification.

## Three-question recovery extension

See VAULT-RECOVERY.md. The outer OKVAULT1 identity and payload encryption are retained.
The wrapped-key field gains a versioned envelope containing the original password wrap,
three bounded question indexes and a second salted PBKDF2/AES-GCM wrap of the same
content key. A domain-separated length-delimited digest of all three question indexes
and normalized answers supplies the recovery secret; no answers or independent answer
hashes are stored. Both access paths authenticate the whole document before publishing
text or committing a password change. One AtomicFile transaction contains recovery and
ciphertext together; no new backup payload or native format validator is needed.
