# Current: Annotation tools moved into Image Editor

The standalone Annotation app is retired in the source update after 0.100.15-rc1. See PHOTO-MARKUP.md and photo-markup-verification.json. Tools now live inline in Image Editor. Existing private artwork and legacy backup remain intact; shared SDK drawing services and archived reference source remain available. Older release notes below describe historical builds.

# Annotation backup continuation — 0.60.0

Settings > Backups > Annotated Images Backup now saves/restores editable image
JPEGs and validated marks/revision/undo/redo. Existing document IDs win; current
viewport/last-open state and permissions remain local. See SETTINGS-PORT.md for
archive/retry boundaries and RESUME-0.60.0.md for current evidence. Provider,
process-death and phone acceptance remain separate; this is not full SDK backup.

# Annotation — current continuation 0.58.0

Annotation is included in the single OnKlav APK. The current generic Kotlin canvas
cancels unfinished gestures on disable, tool/document/revision changes, resize
and touch cancellation. A new DOWN is required before another commit; completion
callbacks are bound to their submitted edit. Switching images resets the one-time
viewport restore flag. Existing viewport persistence from 0.41 remains in use.

Picker results match pending tokens and reauthorize the image read/reply. Exports
reauthorize after rendering and before sharing/copying; these paths were reviewed,
not rewritten in this batch. Actual Android provider/process-death acceptance
remains open. No original glyph, layout, SDK package or Rust core changed.

## Historical 0.15.0 conversion record

The following describes the source-only conversion at that time. Its deferred
APK/native-build statements and next-app ordering are historical, not current.
Current evidence is source-verification.json and RESUME-0.58.0.md.

This chunk converts Annotation only. The user now requests one app at a time,
source/SDK saving and tests, with approval before the next app. APK assembly,
Android Rust ABI rebuilding and APK upload are deferred to a requested batch.
The last verified installable host is 0.14.0; it does not include this conversion.
The complete OnKlav rewrite, SDK, game and carrier work remain unfinished.

## Actual production path

`AppsDesktop annotation -> AppSdk.bundledApps -> SdkAppScreen/SdkAppModel -> AppSdk
-> NativeCore JNI -> sdk::dispatch -> wasmi -> Annotation.onklav-a main.wasm ->
validated frame/effect -> scoped Rust drawing service or authorized shared native
canvas/image service -> generic Kotlin/Compose controls`.

`sdk/apps/annotation` is a separately compiled Rust controller, signed with the
retained publisher. It owns document navigation, tools/color/width selection,
rename, saved-image search/library, text-note dialogs, delete confirmation and
import/export continuations. It is not a descriptor selecting an app-specific
Kotlin screen. The old trusted AnnotationScreen and its ToolScreen route were
removed. No original Java code, classes, old app binary or separate APK is used.

`core/src/drawing.rs` implements durable image metadata, validated image-space
marks, atomic edits, bounded undo/redo, search, deletion, reopening, revision
checks and private storage. Shared Kotlin `SdkDrawingComponent`, `SdkDrawingView`
and `SdkDrawingImages` implement native rendering, local gesture previews,
viewport transforms, image codecs and host integration. These are capabilities
available to any authorized SDK package, not an application-ID switch.

## Original reference and recreated behavior

The reference was Inner Circle 2.2.85 AnnotationActivity, MarkupView/MarkupData
and the shared ToolActivity. Those files were read as design/behavior references
only. The actual reference tools are Move, Pen, Highlight, Text and Erase; it
does not have the arrows that an earlier speculative gap list mentioned.

The controller and shared components recreate:

- Back/title and compact Export, Settings, Saved Images header actions.
- 80-UTF-16-unit image title with Open alongside; saved-count/status below.
- Image-space canvas, one-finger drawing/movement/erasing/text selection, local
  pan/zoom, Fit, bottom-left Undo/Redo and bottom-right round Brush/Tools control.
- Bottom Tools dialog: Move, Pen, Highlight, Aa; Yellow/Green/Red/Black/White;
  Erase a Mark; stroke width 1–12. Highlight rendering is translucent.
- Text notes: tap to add; select with Move to edit; Add/Save/Cancel/Delete;
  maximum 400 UTF-16 units. Native layout measures the note; Rust rejects boxes
  that cannot fit and clamps placement inside the base image.
- Completed strokes, whole-mark erasing, mark movement, undo and redo persist
  immediately through Rust. Field edits use the shared 500ms debounce and flush
  before navigation. Undo/redo histories are durable, capped at 10 snapshots
  each and 2MiB combined serialized history; large histories may retain fewer.
- Left saved-image drawer per Charles's requested direction, + New, title
  search, image/date rows, confirmed deletion/cancellation and reopening. Lists
  page in groups of 40. Search distinguishes no saved items from no matches.
- Export menu: Save to Files, Share, Copy. Exports are a flattened JPEG of the
  base image and committed marks; viewport pan/zoom is excluded. Shared native
  providers supply temporary read-only image URIs for Share/Copy.

## Bounds and capability boundary

One visible canvas is accepted per validated frame. The guest receives document
metadata and action results, never an Android object, URI, native path, bitmap
or the encoded base image. Full marks/images cross only the native-host/Rust
boundary, outside Wasm state. This avoids sending a large drawing through the
450KB guest-state/UI budget on every gesture.

`images.edit` and `storage.private` gate private drawing operations and every
native canvas/image descriptor. Canvas callbacks bind to the live session,
widget, document ID and revision; stale edits are rejected. Text-tap/edit
callbacks are non-gesture events and cannot launch OS actions. Import needs
`files.images`, export needs the matching reviewed `files.export`, `share.text`
or `clipboard.write` grant, plus the drawing/storage grants. Native permission
checks repeat after decoding/rendering and when file selection returns.
Updates, removal and grant changes invalidate sessions. Private-data deletion
removes image/document files with the SDK scope; revoked sessions cannot fetch
old thumbnails or canvas images. No storage/network/Android permission is
passed directly to a guest.

Input is a user-chosen content URI, bounded to 20MiB and 100 megapixels before
full decode. Sampling limits stored dimensions to 1800x1800. EXIF orientation/
mirroring is applied, transparency is flattened to white and JPEG encoding is
bounded to 600KiB. Rust separately validates normalized JPEG size, marker
structure/dimensions and encoding. Stored documents are limited to 300 marks,
30,000 total points, 2,000 points/stroke and finite in-bounds coordinates; titles
and text have the original limits. The shared per-app private quota is 16MiB,
with at most 2,000 image documents and a 4MiB document-file cap. Native thumbnails
sample down while decoding rather than retaining full-size pictures per row.
Exports are <=900KiB JPEG. JPEG validation in Rust is structural; the trusted
native decoder/normalizer is the actual pixel decoder.

## Verification and current build boundary

50 Rust and 40 Kotlin/framework-host tests pass; see annotation-verification.json.
The signed-package integration test executes actual Annotation Wasm through the
real broker, including import, stroke, undo/redo, text-note measurement replies,
rename, scoped export snapshots, library visibility, reopen and revocation.
Rust also tests path/binary/image-coordinate boundaries, stale revisions,
scoping/deletion and the one-canvas resource limit. Native drawing tests check
input limits/revocation, valid image decode, exported dimensions/pixels, text
bounds, image-space gesture coordinates and cancelled strokes. The Compose
host test covers the actual desktop route, tools/color selection, rename,
search, confirmed-delete cancellation, image clipboard URI and reopening. All
existing Kotlin regression tests pass alongside it.

Compilation initially exceeded a combined 180s phase while rebuilding shared
modules. Library and app compile phases are now separate bounded steps. Tests
then caught redundant nested scrolling in the shared menu, which was removed;
normal dialogs no longer receive repeated window-position mutations. The final
Annotation UI failure was a test waiting for a title visible on the main screen
instead of the drawer search field; it now waits for the drawer itself.

The current source builds/tests Linux JNI and Kotlin, with signed SDK provenance.
No APK, mobile ABI compilation, physical installation, OS picker/share-target
acceptance, carrier traffic or performance measurement was performed. Existing
apk-verification.json/verification.json/native-provenance.json retain the last
verified 0.14.0 build. Current Rust source matches host-native-provenance.json.
The source-only ZIP excludes the obsolete mobile Rust binaries. A future APK
requires a full native rebuild and APK verification; never relabel the old APK.

## Remaining acceptance

Handset pinch/pan/gesture comfort, exact original spacing/fonts/glyphs, large-font
and screen-reader access to individual marks, zoom-center preservation across
rotation, cancelled/asynchronous input around orientation changes and process
recovery of in-flight file pickers/exports still need acceptance and follow-up.
At 0.15 the viewport refitted on resize; 0.41 added viewport preservation and
0.58 checks image switching and interrupted gesture recovery. Completed edits survive process restart;
an in-progress gesture or unsubmitted text dialog is transient. The host UI test
seeds a real normalized image through the same pending import/reply contract; it
does not certify every real Android Files provider or sharing destination.
No private Inner Circle library was imported or deleted. Earlier rewritten
Annotation held images only in memory, so there is no persisted rewritten image
library to silently adopt. Language choice alone establishes no speed improvement.

Social is next only after Charles approves; Walkie-Talkie follows it. Earlier
YouTube/Browser/voice/carrier/game gaps remain in their respective ledgers.
