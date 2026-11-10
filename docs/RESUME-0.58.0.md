# 0.58.0 — Annotation gesture recovery

Production path: generic SdkDrawingView -> SdkAppModel.canvasEvent -> Rust SDK
canvas_event -> revision-checked durable drawing service. The new host guard
requires an active DOWN, cancels transient state on disable/tool/revision/document
changes, resize and CANCEL, and invalidates old completion callbacks on document
updates/clear. Tool changes do not unlock an outstanding submission. A document
switch resets viewportRestored so its own saved state can be applied.

Focused SdkDrawingTest checks interrupted input, delayed callbacks, document
viewport switching, normalization/export and existing image-space drawing/resize.
See source-verification.json for actual results. Rust/SDK/Godot inputs unchanged;
reuse source-matched native artifacts and signing identity. No icon/layout change.

Image-picker tokens and live read/reply grants, plus post-render export grants,
already exist and were reviewed without changes. Actual OS provider, process-death,
handset gesture and accessibility acceptance remain open. No carrier acceptance.

Next: Group 2 Settings SDK-document backup and restore coverage review. Inspect
current implementations first; four broader groups remain, games deferred.

First APK assembly failed while Gradle snapshotted a disappearing .rsync-tmp
file in its generated output folder. The retained first-attempt log records the
filesystem error; one assembly retry follows without application changes.

Final: 6/6 focused canvas checks pass. Retry assembly completed in 24.7s;
APK verification passed in 2.92s. See build-debug.json and apk-verification-debug.json.
