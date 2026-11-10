# UI restoration — Appearance and shared chrome

Reference: Inner Circle 2.2.85, read-only. New implementation is Kotlin/Compose.
The user requested continued cleanup of the application UI and working theme /
Appearance controls. The 0.8.3 pass audits the complete reference Appearance flow and restores its
missing controls, nested dialogs and shared visual primitives;
it does not claim every application screen has full reference parity.

| Reference behavior | Current implementation |
| --- | --- |
| Staged appearance with message preview, Cancel/Apply | Immutable draft; Cancel leaves active settings unchanged; Apply persists the snapshot |
| Appearance survives configuration recreation | Host visibility, draft and subpage restored with saved state |
| Dark/Light, six font styles, five text sizes | Original choices, system-local font families and persisted message size |
| Fifteen message colors and gradient bubbles | Original color IDs/order, sent colors, contrast-aware gradients and selection ring |
| Original backgrounds and opacity | Independent rounded Backgrounds Save/Cancel dialog, original preview messages, reset/current/none controls, visible-image percentage, fixed image controls, 28 original WebP tiles and saved personal images |
| Named themes and preset themes | Save/load/replace/delete; 40 named themes, original 20 presets, explicit Apply after loading |
| Theme transfer in Appearance | Complete portable .onklav-t ZIP with wallpaper (including built-ins), at most 1 MiB exported image, current-preview template, checked metadata/preview and choose-another import control, SAF save feedback and sharing |
| Compact floating dialogs and inset actions | Reference 18 dp corners, neutral window surfaces, 4 dp insets, centered choices, compact footer fills, thin sliders and compact switch |
| Settings tiles / bottom tabs | Reference section order and icons, adaptive Settings columns, sent-color/gradient selected tabs |
| Shared fonts/colors | Applied to host and built-in tools; Notes title family and message-sized editor / RSS headlines connected; no independent app APKs |
| Appearance Help | Complete selectable guide, Copy All, Save Text File and share flow |

Original artwork is preserved as visual input, including its provenance notes.
No original Java implementation, app binary or legacy game engine is included.
Theme import executes no scripts and grants no permissions. The external app /
game SDK runtime and broker remain disabled/unimplemented as documented separately.

See APPEARANCE-REFERENCE.md for the feature-by-feature comparison, source/resource
review and rendered reference screens. Final 0.8.3 runtime and app-render results
are recorded in verification.json. Functional checks, app captures, APK checks and
physical-phone acceptance are distinct. File-picker/receiver interactions and
physical-handset acceptance still need manual checks. The software emulator has
a System UI ANR history; app captures do not certify its operating-system UI.

Remaining UI work includes per-app menus/editor behaviors, paged app-grid
reordering, messaging/group/media controls, missing Settings operations and the
original game presentations. Keep the gap ledger; do not label this full parity.

0.8.3 verification: build, six shared tests, APK checks, installation and Notes
regression passed. Expanded Appearance runtime coverage is incomplete due to a
Compose/Espresso idle timeout after the settings-light capture. Do not interpret
the implementation table as a claim that every device interaction passed.
See evidence/v0.8.3/final-runtime-diagnosis.txt for the unresolved limit.

0.8.4 Home/Apps/Settings restoration is documented in HOME-REFERENCE.md.
The new sparse-grid/chrome/manager device regression passed on the exact APK;
11 shared tests passed. This does not change the historical Appearance timeout
or establish full application parity.

## 0.24.0 — Settings and Appearance UI cleanup

Original Settings entry order/artwork and Appearance/theme flows are retained.
Real persisted Walkie preferences, chat notification channels, blocked-message
review, safe storage reporting/cleanup, checked metadata backup/restore and local
spam warnings replace old Settings shells. Quiet Hours restores compact time and
repeat controls; dialogs and shortcuts adapt to small viewports/large text.
Read Aloud handles offline voices/preview lifecycle. Game preferences persist
for the pending ports. Full service parity is not claimed: see SETTINGS-PORT.md,
RESUME-0.24.0.md and source-verification.json. No APK or carrier/account operation.
