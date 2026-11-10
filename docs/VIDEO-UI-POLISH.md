# Video Edit interface polish

Refines the preview-first phone workflow without changing media processing, file formats or export behavior.

- A primary-color selection strip names the active clip, overlay or audio track; timeline accessibility also names it. The main clip outline no longer competes with a selected overlay/audio track. Visible clip outlines remain visible when clip boundaries extend beyond the viewport.
- Tool groups have concise purpose headings. Dropdowns show their field labels instead of displaying an unexplained current value. Labels and current values remain separate for accessibility.
- Immediate editors show automatic-save/pending state in the existing fixed header slot. Successful edits show Saved / Undo available only when history exists; text and canvas show Draft / Apply to save. The preview allocation is unchanged.
- Audio timing/fades/sound and overlay settings offer direct routes to choose the required track/layer. Missing clip sound, audio extraction requirements, full track/layer capacity and unavailable split position have concise explanations.
- Tool spacing and scrolling remain suitable for small touchscreens; no additional global banner moves the preview.

Verification results: see `video-ui-polish-verification.json`. Host-rendered previews use a generated source-image fixture; physical-phone playback, keyboard, audio/video export and performance acceptance remain pending. No APK requested or built.

Production and test compilation passed. All 10 focused UI checks passed in one run, including the existing end-to-end editor workflow and new selection/save-state/unavailable-settings routes. Small screen/large text, landscape, portrait canvas and all-tool preview bounds passed. Final source hashes match the compiled source.
