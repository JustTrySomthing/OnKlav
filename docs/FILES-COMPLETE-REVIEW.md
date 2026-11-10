# Files workflow review

Review covers the actual signed Files controller and Rust store, Compose Files UI and shared picker, Android shared storage and legacy document trees, import/export recovery, editor replacement saves and backup-file copying. No APK is produced by this source increment.

## Repairs and completed features

- Attachment pickers now share the Files app's OnKlav/Android tabs and Internal files link. Trash and protected app-system roots are not offered as attachments or save destinations. The shared selection session also refuses trashed files when invoked programmatically.
- Generic/unknown MIME types fall back to filename detection for typed attachment filters. Folder picking distinguishes readable categories from physical writable save destinations, so Android Audio can be selected for reading without offering it as an invalid Save here target.
- Android Trash has Restore all and confirmed Empty trash, with live SDK/session/storage authorization and partial-completion errors that keep remaining items. Copies and permanent deletion retain the safeguards from the previous review.
- Restore falls back to Downloads when the original parent was removed. It retains existing destinations, and fallback names are made unique. Backing names retain a readable name for recovery if app metadata is unavailable, with bounded UTF-8 length for long filenames. A .nomedia marker keeps the backing trash directory out of media scanning.
- Previously saved Photos/APK locations remain navigable through hidden compatibility entries. The visible Android root keeps the requested six categories and Internal files link; Images includes camera photos.
- Android broker operations and connection changes are serialized per owner to prevent independent windows from overwriting each other's cached entry tables.
- Shared session Copy now accepts the controller's successful Copied message instead of treating it as an error.
- Header Back navigates Android parent folders and returns to OnKlav only from the Android root. Current Android folder names are shown below the storage tabs.

## Verification

See files-complete-verification.json and evidence/files-complete. The report combines unique passing classes across bounded phases; only changed/new paths or diagnosed test fixtures were rerun. Older tests that launched the retired Notes app were updated to current shared-document workflows; Write migration retains dedicated regression coverage.

The native workflow exercises folder creation, copy, rename, move, ZIP byte verification, cycle rejection, search, Trash/restore, empty-trash and source preservation through the real SDK/engine. Android coverage includes nested copy/move/rename, ZIP/share, missing-parent and metadata-loss recovery, bulk actions, overwritten-destination protection, linked-folder rejection and permission removal. UI checks include the storage tabs, gallery-style pencils, typed pickers, read-only folder selection, bulk-action confirmation and parent navigation. Existing recovery, backup, file-provider and editor-save checks remain included.

Phone verification is still required for Android 15 permission dialogs, vendor document providers, very large storage libraries and other installed apps' intent handling. Android Trash manages deletions performed through OnKlav, not other apps' private trash stores. Android private app directories remain restricted by the OS. Search remains scoped to the opened folder/category; recursive global search is not claimed. No software audit proves the absence of every possible bug.
