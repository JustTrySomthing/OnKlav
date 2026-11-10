# Image Edit: compact Retouch brushes

Open Tools, then the bandage (Retouch) button. Red eye and Heal share a brush-size control and the existing Undo/Redo and original comparison controls. The ordinary annotation tool row remains visible; choose any tool to switch directly. Stamp now sits between Red eye and Heal; see PHOTO-STAMP-AND-RETOUCH.md. Pinch with two fingers to zoom/pan; a two-finger gesture cancels the pending correction. Strokes apply on finger-up as one undoable edit.

Red eye targets red-dominant pixels inside the brush, reduces their excess red and feathers the edge; neutral catchlights and non-red pixels remain unchanged. Brush only the pupil. Healing chooses a nearby donor patch by comparing surrounding rings, color-matches it and feathers it into a small spot. It is intended for blemishes and small marks, not removing large objects. At boundaries without a usable donor it preserves pixels.

Corrections are stored as normalized source-space operations, not destructive changes to the working original. They replay before geometry and color, retaining the target through crop/rotation and using the same pipeline for preview, JPEG/PNG save and editable Layers. Legacy projects load with no corrections. Drafts, history, restoration and Layers use the existing persistence system.

No model, cloud service, new dependency or permission was added. Work is bounded to 80 correction strokes / 1,000 points, 256 points per stroke and a cumulative brush-area budget; oversized imported work is rejected. Healing uses bounded local scratch arrays. A corrected source copy exists only during rendering and is recycled after geometry. Export retains the existing 4,000-pixel limit and memory-error handling.

Host verification uses generated red-pupil and skin/texture fixtures, not real portraits. Physical-phone touch precision, portrait variety and large-photo memory/performance qualification remain pending. No APK built for this change.

## Verification

Original implementation verification (before Stamp): production compilation and 23 unique staged focused checks passed: 7 retouch pixel/geometry/validation/persistence checks, 2 retouch UI checks, 6 markup checks, 3 Layers checks and 5 existing drawing UI checks. See `photo-retouch-verification.json` and `photo-retouch-checks`. UI fixture clock/wait corrections and generated-cache failures are retained in the evidence. Final production hashes match the compiled source.
