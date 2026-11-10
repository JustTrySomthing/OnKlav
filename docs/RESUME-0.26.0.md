# Resume 0.26.0 — SMS/MMS history export

Source-only 0.26.0-dev/code33. No APK or Android native ABI rebuild; last verified
APK remains 0.14.0. All internal SDK packages/publisher identities are unchanged.

User said Next after Automatic YouTube Previews, within continuing Settings work.
Bounded task: SMS/MMS export, not restore. Estimated 20–30 minutes with checks;
provide concise progress and updated ETA, no long silent loops or per-item APK.

Read HISTORY-BACKUP.md for the exact production path, schema, bounds, staging,
Stop/cleanup behavior and limitations. Real Kotlin provider reads + Rust ZIP
validation/streaming/checksums are used in production, not an old app wrapper.
Settings > Storage > Backups > Messages and Media Backup is enabled for export.
Restore remains visibly disabled; app-metadata JSON export/merge is unchanged.
Provider observation is best-effort rather than a portable cross-table transaction.
No actual user history was read or written. Host provider fixtures are not OEM proof.

Run build_native.py --host-only before check_android.py --tests-only for Rust edits.
Same native-graphics Robolectric sandbox as existing JNI tests, no PixelCopy or
emulator loop. One Gradle/cache mutation at a time. Current final evidence is in
source-verification.json and docs/evidence/v0.26.0. No SDK program changed, so no
package rebuild or additional signing key is needed.

Next bounded Settings work is full history restore: strict versioned archive
inspection, staging/checksum/size/path validation, explicit review, duplicate
mapping and provider-write/default-role guards. Do not enable MMS_READY or change
default messenger before carrier MMS is verified; there are no test phones.
Drive account/uploads, bundled speech voices, profile sharing, external game
runtime and handset/reference acceptance remain separate unfinished work.

Save the updated TODO, source and SDK starter, preserving existing file identities.
No need to save the unchanged Walkie internal package again. Root AGENTS.md and
TODO retain full-platform gaps. Do not claim full UI/product completion.

Final current verification: 91 Rust +69 Kotlin host tests passed (160), zero failures.
Full app/shared invocation and runtime-class freshness checks passed. No APK/mobile
ABI build, real history/provider/phone/carrier/account or rendered parity acceptance.
