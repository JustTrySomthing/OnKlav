# Video Edit: single dock and larger portrait preview

The owner screenshot of APK0.100.58 showed a permanent Quick tools row duplicating the four category buttons, an empty gap, and a small portrait preview. The prior footer stacked timeline space and reserved inspector space even when no inspector was open.

The default screen now has only Edit / Audio / Layers / Finish. Each opens a compact menu, with no permanent Quick tools row or description cards. Sound controls has a speaker icon and Transitions has distinct arrows, eliminating repeated look icons in the Finish list.

The timeline/category dock and focused inspector now share one bounded footer rather than stacking separate reserved regions. Portrait footage receives the recovered space. Opening a tool keeps preview bounds unchanged and replaces the timeline/footer with its focused controls; More > Timeline retains direct timeline access while editing. The selected target stays in the transport label. Staged text/canvas, Undo/Redo and destructive confirmation remain intact.

A native360x800 default-font check uses a portrait fixture and allocates504dp to the preview, versus358dp under the previous allocation on the same viewport (about41% taller). The screenshot uses a generated portrait fixture, not the owner's video or a physical-device decoder capture. Wider and short windows keep the existing adaptations.

All14 unique focused UI checks passed. The first run passed13 and had one ambiguous automation selector for two Done labels (dialog and underlying inspector); selecting the dialog button fixed the test and its targeted rerun passed. Both original and rerun evidence are preserved. See video-footer-repair-verification.json and evidence/video-footer-repair/.

Panda, other games, SDK packages and media-processing code are unchanged. APK0.100.59 is the corrected delivery;0.100.58 predates this repair.
