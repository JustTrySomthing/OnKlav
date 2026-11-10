# Encrypted user folders

Source implementation complete. Forty native checks and ten unique focused Android host/UI checks passed. Not an APK, physical-device acceptance or a security-audit claim.

Files offers a padlock for user-created OnKlav folders. Built-in, system and connected Android folders are excluded. The shared built-in picker uses the same password dialog when an app enters a protected folder. Passwords have show/hide controls and confirmation for a new password. An unlocked folder offers Lock now, Change password and Remove protection.

The encrypted container uses Tink AES-256-GCM-HKDF streaming AEAD. A random data keyset is wrapped by AES-256-GCM with a password-derived PBKDF2-HMAC-SHA256 key, a random 128-bit salt and 600,000 iterations. Authentication binds the folder identity and payload generation. The container holds file names, directory records, file contents, and the private Write / retained Notes recovery copies associated with those entries. The main index keeps the outer folder name and opaque reserved member IDs while locked.

The native broker centrally denies locked reads, mutations, ancestor copies/archives, share resolution, provider reopening, linked Write access and Trash access. SDK guests cannot invoke the host key-management operations or receive passwords/keys. Existing files explicitly imported into another app or exported elsewhere are independent copies and cannot be recalled. Already-open external file descriptors cannot be revoked by deleting a cache file; applications that received them while the folder was unlocked may retain that data.

Unlock creates a private working tree. Lock closes reads, finishes tracked Write saves, freezes mutations, writes an atomic authenticated archive, verifies it completely, then removes working and known recovery copies and publishes the locked index. Application backgrounding triggers a lock; internal activity/file-picker handoffs keep the session. A process death can interrupt cleanup and leave private working/staging files until next startup recovery. The native gate defaults to locked after process restart, and recovery completes before app setup. This is app-sandbox protection with authenticated encrypted containers, not a guarantee against root, a compromised device, memory capture or forensic recovery of previously deleted plaintext blocks.

Open-session recovery keys are wrapped by individual Android Keystore keys, erased after a completed lock. Correct-password recovery remains available if a Keystore session key is lost. Uncommitted working edits are preserved on a failed seal; a correct password reopens the latest working tree so a storage/capacity problem can be resolved. There is no forgotten-password recovery after a successful lock.

Limits: 240 MB total working data including automatic recovery copies; 1,000 entries per folder; no nested encrypted folders. Deferred provider-output saves must finish before a folder is protected. Once protected, save an output elsewhere, then move it into the unlocked folder using Files. This avoids leaving an untracked output file outside the encrypted transaction.

Backups require a completed lock and carry encrypted folder containers, not plaintext children. Restoring reserves member identities and rejects collisions with existing private Write records. Password changes rewrap the current keyset; they do not change passwords on older backups or copies of the container.

Physical phone acceptance remains necessary for Android Keystore behavior, background/process-kill recovery, low disk space and large-folder timing. No handset or external security audit is implied by host tests.

Before permanent deletion, remove folder protection with the password. This
ensures the host destroys the session recovery key before removing the folder's
identity. Trashing an unlocked folder is allowed; locked folders cannot be moved,
renamed or deleted.
