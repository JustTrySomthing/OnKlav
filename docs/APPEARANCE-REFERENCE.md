# Appearance comparison against Inner Circle 2.2.85

The original is a read-only behavior/design reference. Its AppearanceDialog,
BackgroundPicker, AppearanceHelp, ThemeTransferDialog, AppearanceState, UiTheme,
FontChoice and palette/background catalogs were reviewed in full. OnKlav uses
new Kotlin/Compose components and its new data-only theme format. No original
Java or original application binary is included in the delivered project.

| Original control / behavior | 0.8.3 rewrite |
| --- | --- |
| Large staged Appearance window, persistent Cancel / Apply | Fixed-height editor, preview-only draft, parent remains behind child dialogs |
| Dark / Light button and help | Mode switch independent of palette; complete selectable help, Copy All and Save Text File / sharing |
| Message preview | Two original messages, incoming/sent alignment, 14 dp bubbles, chosen font, size, color, gradient and background |
| Font Size | Thin seek bar with five original sizes (16, 17, 20, 23, 26) and accessibility labels |
| Font Style | Six font-preview list rows, selected check, no invented outlined choice cards |
| Message Color | Fifteen original IDs in original hue order, gradient-aware circular swatches and selection ring |
| Gradient Bubbles | Independent switch, preview and theme persistence |
| Backgrounds & Overlay | Separate rounded Backgrounds dialog and separate Save / Cancel draft |
| Background preview | Original “A little more you.” / “Make yourself at home.” messages |
| Background Opacity | Visible-image percentage, reverse mapping to overlay, disabled without image; separate values for Dark and Light |
| Reset / current selection | Reset to Default Background, Current label, No Background selection |
| Built-in gallery | 28 original WebP artworks; two-column 145 dp crops, bottom caption strips and 3 dp selection border |
| Your Backgrounds | Below the built-in gallery; saved images retained separately from provisional imports; delete confirmation preserves original photo and clears theme references |
| Fixed background controls | Choose Your Own Image / Choose Another Image; Cancel and Save remain below the scrolling gallery |
| Load & Save Themes | Save Current / Restore Default, sorted Your Themes, preset order 19 down to 12 then 0 through 11 |
| Theme cards | Bold name and mode, compact preview, Load Theme, Export or Share, delete personal theme |
| Named themes | 40 names, 40-character entry, case-insensitive replace confirmation, explicit delete confirmation |
| Import | File picker, checked settings/image summary, preview, Choose Another File, replace confirmation; Import stages and Apply activates |
| Export / Share | Snapshot, save/share chooser, completed-save feedback and private URI grant |
| Portable theme | Built-in and personal wallpaper embedded as JPEG, optimized to at most 1 MiB; settings + image in one .onklav-t archive |
| Theme Template | Exports the current complete preview, not a blank/default appearance |
| Image constraints | 30 MiB / 100 MP input, normalized private JPEG no larger than 2048 pixels on its longest edge; 100 saved personal images |

The new .onklav-t schema and extension intentionally replace .ictheme; the old
Java parser is not bundled. Existing 0.8.2 OnKlav snapshots/themes/images remain
readable. Complete old-app visual parity is not inferred from this table: runtime
checks and comparison captures are recorded separately in verification.json.

Rendered reference review: Settings, Appearance top/lower, Backgrounds and the
theme chooser were captured and inspected in a separate emulator UI-inspection
copy. See evidence/v0.8.3/original-ui-reference.json for the native-ABI limitation
and byte comparison. Palette XML and dialog drawables establish an 18 dp rounded
neutral surface with 4 dp insets; those replace the mistaken square gray dialogs
from 0.8.2. Choice labels are centered and the footer paints a 40 dp fill within
a 48 dp action target. No reference APK is included in this source archive.

## 0.24.0 — Settings and Appearance UI cleanup

Original Settings entry order/artwork and Appearance/theme flows are retained.
Real persisted Walkie preferences, chat notification channels, blocked-message
review, safe storage reporting/cleanup, checked metadata backup/restore and local
spam warnings replace old Settings shells. Quiet Hours restores compact time and
repeat controls; dialogs and shortcuts adapt to small viewports/large text.
Read Aloud handles offline voices/preview lifecycle. Game preferences persist
for the pending ports. Full service parity is not claimed: see SETTINGS-PORT.md,
RESUME-0.24.0.md and source-verification.json. No APK or carrier/account operation.
