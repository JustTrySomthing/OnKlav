# Current reconciliation — 0.65.0 source only

Use TODO.md and RESUME-0.65.0.md as the current status. Historical entries below
are checkpoint records and are not a fresh list of missing implementations.
Eight-app document backup is complete; retain the installed speech engine.
Profile sharing was not in the original local-only Profile. Current source adds
Drive authorization/upload/public links and recovery, shared speech Settings,
bounded Files duplicates, provider-first read updates, Browser Find counts and
complete per-profile clear, and YouTube Back/rotation behavior. Existing capture,
viewer, group history/reactions, Browser handoff and Walkie transport/recovery are
implemented. Phone/account/release qualification is still unclosed; no APK built.

## Historical records follow

# Files backup continuation — 0.60.0

Settings > Backups > Files Backup now saves active ordinary files/folders to a
checked portable ZIP and restores missing IDs while keeping existing content.
Notes use the separate Notes backup. Trash, System packages and Android folder
links remain local. See SETTINGS-PORT.md and RESUME-0.60.0.md for archive limits,
retry semantics and current evidence. Broader SDK-data backup remains unfinished.

# Files — OnKlav 0.47 development checkpoint

Files is a signed Rust/Wasm SDK application (`app.onklav.files`), displayed on the Apps tab using the shared renderer, theme, icons, menus and permission manager. It is not another Android APK.

## Content and operations

Home contains Downloads, Images, Documents, Audio, Video, System Files, External Files and Trash. Search, bounded pagination, breadcrumbs, previews, new folders, rename, move, individual-file copy, open, import and export are implemented. Delete inside OnKlav moves content to recoverable Trash; permanent deletion and emptying Trash require confirmation. Folder moves preserve descendants. Recursive folder copying is not offered.

Documents/Notes contains the same canonical notes used by Notes. Existing SDK notes are copied into the new store once, with originals retained. Renaming or moving a note in Files preserves its identity and text; deleting it hides it from Notes until restored. Notes can open directly from Files and Back returns to Files. Delete Private Data for Notes removes its canonical notes. Ordinary exported copies remain user content.

OnKlav stores metadata atomically under `filesDir/onklav-content/index.json`, ordinary file blobs under `objects`, and canonical note records under `notes`. Virtual locations are independent of opaque physical filenames. Individual imported/downloaded files are limited to 256 MiB, the index to 10,000 entries, nesting to 64 levels, and names to 180 UTF-16 units. Notes retains its separate 16 MiB quota. Corrupt trashed notes do not prevent loading active notes. Private databases, caches and live service state remain protected app data rather than editable user files.

System Files presents read-only installed signed SDK packages and their public manifests. It does not expose private app databases, registry mutation, credentials or arbitrary host paths. Packages may be inspected and copied to Android.

## SDK and Android integration

`files.export` text exports now save to Documents/<app>; Annotation file exports save rendered images to Images/<app>. Browser public HTTPS downloads save to Downloads/<app>, with explicit user confirmation, streaming bounds, cancellation and live authorization. Browser downloading does not forward account cookies or promise authenticated media downloads. Annotation working project storage and other private app state remain private; this checkpoint does not migrate every existing app database into public files.

`files.manage` grants access to shared OnKlav content; `files.external` grants access only to Android folders the user connects through the system folder picker. Copying out also requires `files.export`. Internal `library.*` services enforce live session capabilities and pending-operation tokens. Guests receive opaque IDs, never Android URIs or private filesystem paths. Writes require an explicit user action; read refreshes after mutations are permitted.

External Files uses Android Storage Access Framework persisted grants. Users can connect folders, browse, preview, create folders, rename, move/copy when the provider supports it, permanently delete with confirmation, and disconnect. Connected locations are scoped per SDK app and checked on each operation. Disconnect forgets the connection; Delete Private Data clears that app's connections without deleting Android originals or other apps' connections. There is no broad-storage permission. Android restricts which root/data folders can be selected.

Crossing between OnKlav and Android uses explicit import/export copies. External deletion is permanent and does not claim to use OnKlav Trash. Primary tap opens a regular file; Options retains rename/move/Trash controls. Notes opens in-process and Back returns directly to Files. Images, video and audio use OnKlav’s built-in viewer/player, including selected external content. Other file formats use an Android handler with read-only access to one selected temporary copy. Provider-dependent operations report errors instead of falling back to destructive copy/delete behavior.

## Acceptance remaining

Framework-host tests exercise the actual signed guest/JNI, canonical Notes integration, provider contracts and permission boundaries. A physical Android phone is still needed for system picker/provider variations, file-opening handlers, process recovery, accessibility, large media, and performance acceptance. Games remain unchanged and deferred.

Historical 0.47 check stalled on returning from Notes to Files. In 0.49 the activity-intent handoff is replaced by direct navigation, direct-note Back closes Notes to its caller, focus/selection is cleared and SDK close releases busy state. The native/model route is checked; complete screen transition acceptance remains a handset check.
