# Photo review and audit fixes — 2026-10-30

## Photo findings and changes
- Hidden stacked looks conflicted; Film Noir removed colors applied earlier. Effects now has one explicit look (including Black & white, Sepia and Colorize). Choosing one replaces the previous look, while exposure and other numeric color adjustments remain combinable.
- Colorize provides a tint picker and strength. The active look and amount remain visible. Fully monochrome looks explain why color controls are unavailable; reducing strength restores color influence.
- HDR options and rendering are removed. Old HDR values are ignored. Old stacked projects migrate to one visible look without discarding the original source, geometry or marks.
- Corrected neutral hue rotation and cancellation cleanup of preview bitmaps. Preview and flattened export share the rendering path.
- Save dialog now discloses large-image downsampling; Layers preserves the source and editable history.

## General application audit fixes
Outgoing completed receipts compact into durable per-send records, preserving callbacks and duplicate guards. Incoming SMS/MMS filing has durable recovery state. Failed exports remain recoverable rather than appearing saved. Clock failures restore prior schedules. Conversation-body search queries unloaded provider history; bulk Trash operations use all stored thread metadata. Alphanumeric senders retain distinct identities. Reply All retains original Cc with deduplication and self exclusion.

## Remaining improvements and limits
Full-resolution tiled image processing, richer layer selection/reordering, and on-device accessibility/large-image memory checks remain useful future work. Named audio projects, full-track audio preview, durable background exports/downloads, and video missing-media relinking remain feature gaps from the broader review, not completed by these functional fixes. Physical Samsung/carrier/provider acceptance remains separate. No new APK requested or built.

## Verification
Production compile passed. 46 unique backend checks passed (45 initially, MMS acknowledgement rerun after correcting provider fixture), one Photo UI interaction check passed, and 14 Rust messaging tests passed: 61 total. Test fixtures now honor recovery query projections and scroll lazy wheel options before tapping. Physical device checks and full lint remain unclaimed.

Host and ARM64 JNI were rebuilt from the final source; native source consistency and 16 KiB alignment passed.
