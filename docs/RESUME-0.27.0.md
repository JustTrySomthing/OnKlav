# Resume 0.27.0 — history ZIP checking and review

Source-only 0.27.0-dev/code34. No APK or Android native ABI build. Last verified
APK remains 0.14.0; eight internal packages plus Counter and their publisher are
unchanged. Social stays permission-free Hello World.

User said Next after .26 history export. Initial estimate was 25–40 minutes;
we clarified immediately that Android message writes/default-role changes remain
locked pending real MMS verification. Bounded completed operation: strict ZIP
import inspection and read-only review. This is not operational history restore.
Do not describe it as merging messages or completing all Settings services.

Production: Settings Storage > Backups > Messages and Media Backup > Choose backup
to restore > Kotlin HistoryImport SAF/private copy/progress/Stop > NativeCore
history_import inspect > Rust bounded ZIP/schema/hash/CRC validation > summary >
private cleanup > Compose Review message backup, Restore messages disabled.
No READ_SMS needed to inspect. No user history/provider data is read or written
by checking a ZIP. Export and the existing metadata-only JSON merge still work.
See HISTORY-BACKUP.md for exact format limits, trust/cleanup and remaining gaps.

Rust shares export record validation rather than repacking an imported archive.
Import checks Stored-only v1 ZIPs, canonical names/attributes/extents, hashes and
counts, stricter provider field/date/MIME/address/part/envelope semantics. Only
known files are created in a fresh private job. No executable package or guest
service is exposed; restored permissions/sends/reminders are never accepted.
Native inspector returns restore_available=false/provider_writes=false regardless
of the archive's informational restore_supported flag.

Use build_native.py --host-only then check_android.py --tests-only. Keep the same
native-graphics Robolectric sandbox for JNI tests. No PixelCopy or emulator loop.
Current results/evidence: source-verification.json, docs/evidence/v0.27.0. Preserve
signing keys; no SDK program changed, so no package rebuild is required.

Next restore operation: implement message duplicate/ID and folder/flag remapping,
provider insert/parts/address transactions, cleanup/recovery and cancellation,
with live role/permission checks. Keep real writes gate-closed until safe MMS
acceptance; do not ask user to enable default messenger prematurely. Remaining
Settings: Drive, bundled speech voices, profile sharing, external game runtime
and phone/reference acceptance. Other TODOs remain open; do not inflate parity.

Save TODO/source/SDK starter preserving existing identities; no APK per item or
unchanged Walkie package upload. Give concise progress and ETA updates; never
leave a long retry/emulator loop running without a bounded result.

Final current verification: 97 Rust +72 Kotlin host tests passed (169), zero failures.
Final complete app/shared invocation was forced with current JNI; runtime-class
freshness verified. No APK/mobile ABI build, actual user-history/provider/account/
phone/carrier or rendered parity acceptance. History provider restore remains unavailable.
