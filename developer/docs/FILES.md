# Files and selected content

Guests use the existing files.manage/files.external/files.export grants and library/blob effects. Effects that request host import/export/selection open the OnKlav picker; do not launch Android file dialogs directly or construct native paths. The optional guest-side onklav-ui Files manager/viewer is a controller over these same brokers; its record/drawer layout is not a promise of the first-party Files app's exact host-only layout.

Current native selection offers OnKlav and Android tabs, media/document/download categories, parent-folder navigation and Internal files. The X dismisses the picker; internal navigation and system Back stay distinct. New host-side media saves are flat under Images/Audio/Video. Legacy Photos/APK selections and document-tree locations remain compatible; no guest should persist guessed native folder paths.

library.list/info/folder/rename/move/copy/trash/restore/purge/open/import/export/share operate on opaque IDs. User mutations require a direct action and live authorization. System/app-managed containers and protected metadata reject edits; personal contents remain usable. Save As preserves existing files, internal Trash is recoverable, and the current native Android Files app provides its own recoverable Trash and confirmed bulk actions. Guest library operations retain their documented broker semantics; do not invent an Android bulk-delete effect.

A selected-file import is a separate app-owned copy, not a permanent right to the source. Revocation, locking and removal can invalidate later access. Preserve drafts and offer selection again on failure. Interrupted internal writes remain recoverable through the host. Folder selection may be readable even when it is not a writable save destination. Filename/MIME filters are hints; validate content before parsing it.

Guests never get Android Context, credentials or host paths/URIs. Files grants do not unlock Android private app directories or another guest's private storage. For bounds and exact requests use API.md and the public helpers in developer/components/src/files.rs; native host implementation source is separate from the external app API.
