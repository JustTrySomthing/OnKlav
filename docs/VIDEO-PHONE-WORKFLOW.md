# Video Edit: preview-first phone workflow

## Findings and resulting behavior

The previous layout hard-coded a tool-open preview to20% of remaining height, could hide
it entirely on shorter/large-text screens, stacked a large inspector beside an always-visible
dock/timeline and used one long form for most advanced tools. Preview panning/pinching and
near-edge timeline trimming were active during ordinary browsing. Several destructive or
project-wide actions happened immediately. Text and canvas settings covered the video in
modal forms. Ordinary dirty-state banners could change layout while adjusting a slider.

The new workspace allocates its preview from the window and canvas aspect ratio, independently
of selected tool/category. Portrait video receives additional vertical space with room reserved
for controls. Landscape uses a side inspector. Tool palettes, focused inspectors and staged
text/canvas editors replace each other in one bounded dock; they never add another stack under
the preview. Transport/Undo/Redo remain outside it. With the keyboard visible, timeline and
transport temporarily yield space to the input panel and preview. This keyboard behavior still
needs physical IME qualification.

The main timeline is compact and vertically scrollable for extra tracks; clip/overlay/audio
selection remains available. Default drags scrub only. Trim handles become editable only in
Trim & split, and preview transforms only in Transform, Motion or a selected Overlay. Opening
a tool pauses playback and seeks into its explicit target if the playhead is elsewhere. Target
clip/track/layer names remain visible in the inspector header. Add media is a direct home tool;
zoom and exact time are in More. Tapping the transport time opens exact time, not rename.

## Panel audit

| Tool | Focused pages / behavior |
| --- | --- |
| Trim & split | Trim/photo duration, split/duplicate; Arrange for order/freeze; More for frame stepping/snapping/reset |
| Transform | Position/size; Rotate/flip. Preview gestures are intentional only in this mode |
| Speed | Constant speed and presets; separate speed curve page |
| Motion | Points, zoom, position, rotation, opacity and easing; curve/preset controls retained |
| Audio | Tracks/add/voiceover/extract/beats; Volume/narration ducking; Timing; Fades; Sound/tone/pitch/cleanup/balance |
| Text / captions | Add/select text and caption import/export; staged dock editor with Text, Style, Position, Timing and Motion |
| Overlay | Layers/add/select/split/duplicate; Position; Timing; Chroma key; Mask |
| Color | Looks; one adjustment at a time; imported LUT controls. Original also clears imported LUT |
| Transitions | Selected incoming boundary and length, with existing overlap behavior |
| Fades | Opening/ending fade controls and presets |
| Canvas | Staged ratio and fit/fill, beside the visible preview; resolution/fps/quality stay in Export |
| Preview quality | Existing preview resolution and proxy/cache actions retained |
| Export / projects | Existing Files destinations, progress/cancel, portable backups and project library retained |

Section tabs stay pinned and their content scroll resets on section changes. Slider values
and resets are compact, separate from navigation and have48dp targets. Done commits and
returns to the tool palette; a failed save keeps the inspector open and exposes Retry save.
Only save errors show a layout-consuming status banner. Undo/Redo remain visible for edits.

Deleting clips, overlays, tracks/text, resetting clip effects, clearing beat markers and
applying color to every clip require explicit confirmation. New text does not show Delete;
Cancel discards its staged draft and Apply commits it. Timeline selection and Undo/Redo are
disabled while composing text, avoiding application to a different target. New text placed at
a clip's end gets a useful remaining interval rather than a one-millisecond title. Existing
project formats, originals, backup behavior, permissions and SDK identities are retained.
Clip/audio selection now stops when pending edits cannot be saved.

## Verification and limits

The UI checks exercise every tool's preview bounds and non-mutating navigation, ordinary
preview/timeline gestures, destructive confirmation and Undo, text Cancel/Apply, transient
unsaved state, landscape, large text on a320x568 phone and a portrait canvas. Prior data/media
checks cover speed curves, overlays, title animation, audio processing, timing, recovery and
save-failure behavior. Actual logs/XML and native Compose previews are under video-phone-checks
and evidence/video-phone. Rendered test previews use an explicitly generated source image;
they are not handset recordings or proof of decoder/export performance.

This is an Android source/UI refinement. No Rust/SDK package format or native encryption/media
engine changes, new APK, physical phone acceptance or app-store approval is implied. Hardware
codec/export speed, real keyboard behavior, Android/OEM lifecycle and final store qualification
remain device/release acceptance work. No existing supported editing tool is intentionally removed.

## Final verification

Production and unit-test Kotlin compilation passed. All 28 unique focused checks passed: 19 media/data checks and 9 UI checks. Final production hashes match the compiled source. The initial test assumed an accessibility slider remained unsaved; the test was corrected to cover committed and explicitly pending edits. A generated Gradle transform-cache error was cleared before the successful final run. Logs/XML are in `docs/video-phone-checks`. No APK was built. Physical phone keyboard, playback, export and performance qualification remains pending.
