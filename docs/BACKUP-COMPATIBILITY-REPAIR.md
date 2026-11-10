# Backup compatibility repair

User-supplied Notes and App Backup version-1 JSON files were inspected without altering the originals. The Notes backup contains two valid notes; the retained baseline Linux JNI inspected and restored both with exact titles, text, IDs and timestamps in a temporary data root. No note content is included in source or evidence. The reported Notes rejection was not reproduced. Notes uses Settings > Backup and Restore > Notes Backup > Choose Notes Backup, separately from the App Backup picker.

The App Backup rejection was reproduced exactly as Invalid saved message flag: two saved emoji reactions were accepted by the message picker but absent from a legacy fixed restore allowlist. The validator now uses the same bounded group_wire::valid_reaction check as message editing. The focused Rust regression passes for the rejected emoji plus complex supported emoji, round-trips stars/reactions, and checks malformed reactions reject without changing stored state.

For use before an updated APK, a separate OnKlav-App-Backup-Compatible.json copy clears exactly the two unsupported reaction fields. All other JSON values, including origin, groups, folders, pins, stars and blocked senders, match the original. The baseline JNI accepts and restores it; comparison accounts for the existing deliberate reset of group/message thread references and reminder schedules. Both original input SHA-256 hashes remain unchanged. The normal same-installation origin rule still applies; different installations skip phone-specific flags.

Initial diagnostic assertions incorrectly required thread references to remain unchanged. They failed, were corrected to match the documented existing normalization, and final checks passed. Earlier logs are retained.

No APK was built. The Rust change must be included in a native rebuild; previously recorded NDK/Android-target blockers remain. Previous SMS, permissions and media changes are preserved.
