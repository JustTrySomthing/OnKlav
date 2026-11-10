# Radio visualizer and Image Editor gesture polish

Source update after OnKlav 0.100.15-rc1/code124. The last delivered APK predates this update; no APK or SDK package is produced by this source-only request.

## Radio visualizer

The effect selector now takes the remaining row width, while color and transport retain 48dp targets. A narrow 320dp native layout test verifies the Pause action stays fully visible and clickable.

All six scenes now use the selected swatch and nearby tonal hues. Removed unrestricted hue cycling, purple palette mixing, complementary spectrum coloring, and Aurora's fixed green/purple colors. Aurora retains its curtain/sky construction with the user's chosen tint.

Wave uses a single captured-waveform ribbon with a shorter zero-crossing window in portrait, weighted smoothing, cubic interpolation, softly windowed ends, restrained fill and a luminous edge. It does not fabricate audio or draw three unrelated traces.

Spectrum uses mirrored frequency bars about both axes, with low frequencies and a bass crest near the middle, treble toward tapered edges, two related upper/lower tones and no overlaid waveform. Height symmetry and taper are tested.

Mandala has four independently counterrotating contours at approximately 7.4, 9.5, 11.5 and 13.5 degrees/second while audible. Rotation is independent of intensity. Each contour uses a separate raw-spectrum frequency envelope with nonlinear response, exaggerated peak-to-valley radial travel and contour-attached beads sharing its exact radius/angle. Selected hue stays recognizable. Pause/silence do not invent beats.

No transport, networking, SDK reply schema, capability, publisher or service behavior changed. Previous radio connection/pending-action recovery remains intact.

## Image Editor

Removed the Geometry row containing Fixed canvas, Fit image, Fill canvas and Reset geometry. Removed Move horizontally/Move vertically from the tools wheel. The underlying translation parameters remain for direct manipulation and old saved edits.

The old preview keyed its pointer handler to zoom/pan, cancelling ongoing input on state changes, and suppressed panning at default zoom. The new gesture lifetime uses stable keys and fresh callbacks, with per-gesture accumulated state and a touch-slop threshold.

In Geometry, drag translates the image and pinch changes image scale about the gesture centroid, inside the unchanged fixed crop canvas. Viewport/crop/source conversion preserves the finger anchor, crop, colors, effects, perspective and rotation; bounds remain enforced. A completed or interrupted changed gesture commits once through the existing model history. In Color/Effects, drag and pinch inspect the view without altering exported edits. Reset zoom resets that inspection view.

## Verification

Evidence is in docs/evidence/radio-geometry-polish. Focused native host tests cover narrow and landscape transport, all six rendered scenes and paused stability, selected-color bounds, counterrotation and radial contrast, symmetric spectrum, full photo workspace/menu/history/save actions, original crop rendering consistency, and sustained real Android pointer drag/pinch across recompositions. No handset, actual live station, audio route, carrier or GPU/performance acceptance is implied. Refer to radio-geometry-polish-verification.json for final counts and lint result.

Next requested APK must increment the host version and include these sources, preserve the existing signing identity/native/SDK packages, refresh generated DEX/assets/package caches, and run the normal APK verifier. No Rust, native, Wasm or game rebuild is needed when current proofs still match.
