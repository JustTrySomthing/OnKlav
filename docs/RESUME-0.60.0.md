# 0.60.0 — Files and Annotation backup/restore

Production: SettingsBackup child routes -> generic LibraryBackupDialog and host
LibraryBackup -> serialized AppSdk.staging -> SDK library_backup (host only) ->
Rust library_backup Stored ZIP reader/writer -> content::backup (Files index and
ordinary objects) / drawing backup helpers (editable JPEG + validated document).
No guest package/controller changed, no new capability/icon/layout redesign.

Full records/CRC/hash/graph/size/mark/history/image dimensions validate before
publishing restored records. Whole archive SHA binds to review. Existing IDs win,
including Trash. Current folder conflicts prevent new child insertion. New names
are made unique. Files writes durable blobs before one atomic index; Annotation
publishes per image after its JPEG. Disk failures/Stop may leave a partial merge
or unindexed payloads; exact matching payloads are reusable on retry, conflicting
ones reject. No multi-store atomicity or automatic process-death resumption claimed.

Exports/inspection/copying are bounded and streaming. Live SDK sessions prevent
restore. Kotlin offers Stop via a private marker checked between native blocks;
provider saves require close and failures attempt incomplete-document deletion.
Private staging is cleaned in finally; review copies expire/dismiss. Notes and
metadata formats remain unchanged; packages/grants/sends/external links excluded.
See SETTINGS-PORT.md for limits. Only OnKlav is an APK; signing identities retained.

Initial compile required an explicit Result type for the streamed-write closure;
that log is retained. Three initial focused Rust checks passed, then four final
focused checks included retry/conflict coverage. Full current Rust/native and
focused Android evidence is recorded in source-verification.json. No emulator,
remote-provider readback, phone/carrier/account acceptance or performance claim.

Next: RSS and Read Aloud backup coverage. Browser/YouTube/Walkie stores and full
SDK-data integration remain. Four broader groups still open; games deferred.

Final review found that a duplicate maximum-length name could gain a suffix
longer than the 180-unit limit. The proposed-name guard now rejects this before
writing payloads/index; a boundary regression proves the current index remains
readable. Rebuild native proofs after this Rust correction; earlier logs retained.

The in-progress pre-guard APK assembly was stopped before publishing an APK.
Final assembly follows the corrected 178-test/native proof and both fresh
Android check invocations. No earlier APK is relabelled for this checkpoint.

Final: 178/178 Rust +3/3 provider/JNI +1/1 two-dialog navigation checks pass.
Final APK assembled in 40.42s and verified in 2.83s. Existing signing identity
and ARM64 target retained; handset/provider/carrier acceptance remains open.
