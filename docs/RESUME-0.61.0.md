# 0.61.0 — RSS and Read Aloud backups

Host path: Settings child routes -> LibraryBackupDialog / LibraryBackup ->
AppSdk.staging -> SDK library_backup with app-scoped rss/reader data root ->
Rust library_backup checked archive -> feeds portable helpers / reader_backup.
Same v1 ZIP kind routing, exact entries/CRC/digest/limits/session guard/Stop as
Files/Annotation; no guest app/controller or package permissions changed.

RSS exports sources, offline articles/bookmarks/read marks/settings; strips HTTP
validators/errors/check times. Missing source/bookmark/read identities merge;
current entire source cache/bookmark and settings win. Pristine state takes saved
settings, including explicit empty source lists. Validate all incoming and merged
bounds and quotas before atomic feeds.json; do not call normal save() trimming.
No network occurs during backup/restore. Canonical SDK data only.

Reader exports only current saved draft. Existing saved draft, including cleared
text, wins. New text must fit 50k UTF-16 and validated Notes schema. Atomic document
then pointer commit supports retry after interruption; a matching orphan can
repair its pointer, conflicting existing records reject. No hidden draft pile,
voice identities/settings or playback/caret/runtime state import. Normal own
rewrite preference adoption remains in app-open; old app data is not accessed.

Evidence: focused Rust archive checks, final full Rust/native rebuild, Android
provider/JNI round trips reopening both signed apps, and one four-dialog Settings
navigation check. Use source-verification.json for final actual results. No broad
UI/emulator loop, phone/carrier/provider/account acceptance or speed claim.

Next: Browser and YouTube backups, then Walkie durable archive portability.
Four broader groups and full SDK integration/final review remain; games deferred.
Only OnKlav is an APK. Preserve signing identities and canonical file identities.

Final evidence: 181/181 Rust, 5/5 provider/JNI checks and 1/1 four-dialog
navigation check pass. RSS reopen simulates the normal pending refresh failing
offline, then proves cached content/bookmarks are visible; no real publisher
network request is used. APK assembled in 42.68s and verified in 3.30s.
Existing signer, ARM64 target and SDK package identities are retained.
