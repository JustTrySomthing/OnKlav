# Files review after storage tabs

Addressed the following concrete defects:

- Android Trash search used its opaque storage filenames instead of the original display names. Search and name ordering now use original names. Trashed folders keep their directory MIME type.
- Internal files exposed the backing OnKlav Trash folder as ordinary editable folders. It is hidden from ordinary browsing and remains available through Trash.
- Failed Android copies recursively removed the destination, including a file another operation could create after the initial existence check. Cleanup now removes only entries successfully created by this copy; unowned items are kept.
- Recursive permanent deletion could follow symbolic links. Deletion now performs a bounded preflight of the entire tree, rejects links before deleting any item, and checks authorization for every validation/deletion. Move-source cleanup uses the same routine.
- Home and storage tabs could silently discard a pending move. They are disabled while choosing a destination, with a model guard against programmatic reset. Android Back navigates parents or cancels the chooser at its root. Trash is excluded from destination choices, and Paste is hidden for Trash/root/active move states.

Focused host regressions and compilation results are recorded in files-review-verification.json. No APK built. These are source/host checks, not physical-phone acceptance.
