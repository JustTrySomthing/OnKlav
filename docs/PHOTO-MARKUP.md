# Image Editor Tools and Annotation retirement

Source update after 0.100.15-rc1/code124. No new APK requested/built. The latest delivered APK predates this and the earlier visualizer/gesture/MMS/HDR source updates.

## User workflow

Tools is the fifth, rightmost bottom tab, with a pen icon and Tools label. Pen is the default on a newly opened image. The bottom panel contains accessible icon controls for Pen, Highlighter, Text, Move marks and Erase marks. Palette swatches and default-marked width/text-size sliders stay inline. Wide layouts combine tools, color and size into one row, leaving more preview space. Text is typed/edited in a bottom field, placed by tapping the canvas, and committed with Add/Apply or keyboard Done. Adding text selects Move for immediate positioning. Selected text can be edited or deleted; the eraser removes whole marks, never source pixels.

Move drags marks, drags empty space to inspect/pan, and two-finger gestures inspect/pinch-zoom without leaving a stroke. Reset zoom restores the inspection view. Gesture previews are transient until release; cancellation, multi-touch, source/tool changes and permission loss do not commit an unfinished stroke. A move/erase/draw gesture produces one history step. Undo/Redo also discard provisional text; Restore clears pending text along with edits and can itself be undone. Save commits valid text still visible in the inline editor before opening the existing save choices; invalid text remains editable inline.

Marks are an editable layer on the fixed source canvas: crop clips/repositions the output window; Geometry changes the image underneath, while labels remain upright. Colors/effects grade the image, then marks render on top with their chosen colors. Save copy and Save changes share this flattened renderer, while the working source/draft remains editable. Original comparison excludes marks.

## Production path and bounds

The existing signed Photo package still authorizes its native workspace through storage.private. PhotoEditorWorkspace -> PhotoMarkupPanel/Canvas -> immutable PhotoEdit.marks -> model history/atomic draft -> PhotoRender -> existing save-back/copy pipeline. No new permissions, capabilities, cloud services, native/JNI code, Wasm or dependency changes.

PhotoMarkup reuses SdkDrawingImages.drawMark for pen/highlighter rendering. Text is a plain multiline label with subtle shadow, replacing Annotation's note box. Coordinates and stroke/font sizes are normalized to the fixed source canvas for preview/export scaling. The overlay and hit-testing share the crop/view projection, independent of image geometry. Segment hit testing supports long sparse strokes and enlarged text hit targets. Marks have stable IDs for replacement/deletion.

Legacy flat numeric PhotoEdit JSON still loads. An optional _marks array stores bounded immutable mark records in the same atomic draft. Limits mirror the drawing SDK: 300 marks, 30,000 total points, 2,000 points per stroke and 400 UTF-16 units per label. Coordinates, sizes, colors and IDs are validated; movement is clamped to the full canvas. Text fitting rejects labels too tall/wide for that canvas. Cropping can intentionally clip marks. History retains up to 60 undo snapshots and bounds their serialized mark/edit data at 2MiB. Uncommitted text/gestures are transient; committed marks survive a new model through the draft. Original image bytes are never painted destructively.

## Removing the old app without deleting work

Annotation is removed from the platform catalogue, bundledApps/default installs and host APK assets. Bootstrap unregisters any installed app.onklav.annotation through the existing SDK remove operation with delete_data=false. That invalidates old sessions and clears grants, preserving its private drawing files. Inactive retired records are omitted from the Apps management list. Fresh installs include 24 signed bundled apps; the 26 source SDK packages still include Counter and archived Annotation as development/test references. The planner never restores archived Annotation into host assets, and APK verification requires it to be absent.

Legacy annotated-image backup/export/restore remains available and tested after retirement. Saved legacy artwork is retained in its original private library; it is not silently imported into or allowed to overwrite the current Image Editor draft. Shared drawing services, rendering helpers, SDK capabilities and reference-controller source remain available to developers. The old standalone UI test is replaced with fresh-install/upgrade retirement coverage; legacy backup fixtures explicitly install the archived signed reference package solely to create test data.

## Verification and limitations

See photo-markup-verification.json and evidence/photo-markup for final counts and lint evidence. Coverage includes native default Pen/portrait/landscape controls, completed and cancelled strokes, highlight opacity, movement, erasing, selected-text editing/deletion, Undo/Redo, pending-text Save/Restore, actual PNG pixels, draft serialization, legacy JSON, bounds, fixed-canvas projection, export scaling, crop-context exact pixel consistency, two-finger zoom without marks, fresh install/upgrade/session revocation/data retention, legacy backup round-trip and generic SDK drawing regressions. Python planner tests cover archived bundle cleanup and the original source/signing freshness checks.

The first native UI fixture wrote source bytes while retaining an existing editor model/draft, so cancellation and landscape checks initially encountered old marks. It now opens a fresh file through the real model.open path and verifies an empty starting state; cancellation then passes. A parenthesis compile correction and all diagnostic logs are retained. Final lint required the LineBreaker break-strategy constant rather than the equivalent Layout constant; this was corrected without a suppression/baseline. A collapsed-viewport guard prevents singular projection during keyboard/window resizing and has a focused test. Desktop retirement fixtures use the existing EmptyKeyStoreRule only for empty-key cleanup, not encryption or hardware qualification.

Native rendering screenshots are reviewed. Actual handset touch/stylus/IME/large-font/accessibility/provider/process-death/GPU and sustained memory/performance acceptance remain separate, as do earlier carrier/radio gates. Whole-mark erasing is intentional; this is not pixel photo erasing or neural object removal.

Next requested APK must increment version, include all pending source, preserve the original Android signer and verified native/SDK identities, refresh generated DEX/assets/package caches and verify that Annotation is absent from the APK. No Rust/native/SDK Wasm/game rebuild is needed while current proofs match.

Final validation: 33 unique targeted Android cases and 16 Python build-planner cases passed. Full lint has zero errors; exact warnings and current runtime class count are in photo-markup-verification.json.
