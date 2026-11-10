# Image Edit: persistent tools, Clone Stamp and red-eye feedback

Tools retains the Pen, Highlighter, Text, Move, Erase and Retouch row during every retouch mode. Selecting a drawing tool returns directly to it. The redundant Drawing back button is removed. Retouch choices are Red eye, Stamp, Heal, in that order.

Stamp starts in sampling mode. Tap the image to select a source; sampling changes no image pixels or history. Paint short strokes to copy that area with feathered edges. A crosshair identifies the source. The offset stays aligned across strokes; Sample selects a new source. Pinch still zooms/pans and cancels pending paint. Corrections apply on finger-up as one Undo entry. Original comparison disables painting.

Stamp stores a normalized source anchor with each stroke. Replay freezes the corrected image before each clone stroke, preventing overlapping paint from sampling its own new pixels. Out-of-image donor pixels are skipped; transparent source pixels contribute proportionally, while target alpha is retained. The temporary donor bitmap is recycled after each stroke (up to the existing 16-million-pixel bound). No model, dependency, permission or native/SDK change was added.

Red eye now accepts some pink/purple flash pupils missed by the previous maximum-channel ratio. It reduces excess red and blue locally, feathers boundaries and preserves neutral catchlights. Brush only the pupil. Actual changed-pixel accounting drives Red eye corrected / No red pupils found here feedback. Heal and Stamp also report whether a correction changed pixels. Save errors are retained rather than overwritten by success feedback. This is a targeted red-pupil brush, not face or eye detection.

All corrections retain source-space anchoring before crop, geometry and color, editable Layers persistence, original pixels, Undo/Redo and the existing bounded stroke/area budgets. Legacy projects without Stamp remain readable. UI sample state resets when a new source image opens.

Verification is in photo-stamp-verification.json and photo-stamp-checks. Generated pixel/portrait fixtures are host checks, not real-phone portrait or large-image performance acceptance. No APK requested or built; 0.100.29 predates this update and the preceding Video Edit surface polish.
