Current layout update: the browsing rail described below is superseded by VIDEO-FOOTER-REPAIR.md. Keep one four-category dock with compact menus and a shared timeline/inspector footer. Do not restore the idle Quick tools row.

# Video Edit compact workspace — isolated update, November 4, 2026

Based on OnKlav-Continue-Here.zip version 351, SHA-256 ba21407e194e4338691ab87aab44d5b1649d2ceae89579ecba0fbd722245c1e7.

## Behavior

- Preserves Edit / Audio / Layers / Finish. Tool browsing is now a horizontal icon rail with short labels, replacing the tall two-column cards and explanatory paragraphs. Add clips and Add audio retain their existing Files routes. The strip scrolls for narrow screens and large text; each action remains at least 48 dp.
- Removes the workspace's outer horizontal inset so landscape canvas previews reach the full available width. The canvas remains proportional; fitting/cropping and export dimensions retain their existing meanings.
- Keeps playback, timeline and tools together without an empty tool-browser gap: spare vertical space belongs to the preview stage, with wide footage centered at full width. Allocates portrait preview height from the remaining usable workspace after playback, timeline and settings budgets, rather than the former 43% height cap and large fixed footer reserve. Category/tool changes do not resize the preview. Landscape windows keep the side inspector. Keyboard entry temporarily yields the timeline/transport as before.
- Uses a neutral surface around the canvas, with black confined to the actual output canvas. This distinguishes workspace margins from exported letterboxing.
- First media import into an empty project chooses the nearest supported canvas shape based on source dimensions and display rotation. Metadata reading stays off the UI thread. Existing projects, subsequent clips, audio and overlays retain their chosen canvas. Unsupported metadata preserves the prior fallback. Supported ratios remain 16:9, 9:16, 1:1, 4:5, 4:3; this change does not introduce a custom-ratio format.
- Reduces spacing within focused settings while preserving staged text/canvas Apply/Cancel, explicit target selection, fixed save feedback, Undo/Redo, destructive confirmation and explicit transform/trim modes.

Reference reviewed: YouCut's official Google Play listing and editor screenshot, https://play.google.com/store/apps/details?id=com.camerasideas.trimmer . The implementation retains OnKlav's own themes, controls and editing engine.

## Concurrent-work handoff

This is a separate Video Edit update, not a replacement for the shared source archive. It changes only eight Video-related source/test files. No game files, SDK, app version, signing identity or shared navigation files are changed.

Unzip OnKlav-Video-Edit-Update.zip. Run `python3 merge_video_update.py /path/to/current/OnKlav` for a read-only preflight, then append `--apply` to apply. The script verifies every source/update hash before changing any file, accepts either the untouched base or the first Video Edit update, and refuses the entire operation if any target differs from the recorded base. Already-applied files are accepted. For a conflict, compare `base/`, `previous/` and `updated/` or merge `video-edit.patch` manually; do not replace a newer project archive with this older base.

Copy this document to docs/ after the merge. Re-run the focused checks and build the combined APK in the primary chat when requested.

## Verification

Production and test Kotlin compilation passed. All 35 focused checks passed: 11 VideoPhoneWorkflowTest, 2 VideoCanvasMetadataTest, 2 VideoCanvasSizingTest, 1 VideoEditorUiTest, 8 VideoProTest and 11 VideoRefinementsTest. Coverage includes every tool, stable preview bounds, 360x320/large-text and 540x320 landscape windows, timeline recovery, actual JPEG EXIF/thumbnail orientation, video metadata rotation, project recovery, save failure, Undo/Redo, trims, audio timing/MP3 normalization, captions and overlays.

Wide, portrait, short-window and narrow-landscape renders were inspected. UI renders use generated photo fixtures. Video metadata tests use framework-host fixtures; they are not hardware decoder or phone export qualification. See verification/video-compact-verification.json and the JUnit XML files in the update archive. No APK was built.

## Follow-up audit repairs

- Short windows previously could clip the compact rail, and the inline timeline could leave no usable inspector area. The palette now scrolls vertically as well as horizontally. At short portrait/split-window heights, a visible Timeline action opens the same interactive timeline in a bounded dialog; the preview allocation stays independent of the selected tool. Narrow landscape windows (at least 480 dp wide) use the side inspector.
- Photo thumbnails now apply the source's EXIF rotation/reflection before display, with a bounded decode and recycled intermediate bitmap. This aligns the fallback preview with automatic canvas selection without rewriting the original photo.
- Composition preview end callbacks now check that their player is still current, matching the other playback callbacks. A late event from a released preview cannot move the replacement preview to the end.
- The complete Video source/test set in shared project versions 351 and 352 was compared: no intervening Video changes. All eight update targets match the untouched version 352 base. The updater also accepts the first Video Edit update, while refusing unrelated edits.

Final audit: all 35 checks passed. Physical handset codecs, keyboard behavior and export performance remain outside host-test verification.
