# Default desktop layout and page-safe moves

The Apps page uses this left-to-right order on a clean install.

| Page one row | Left | Middle | Right |
|---|---|---|---|
| 1 | YouTube | Browser | Radio |
| 2 | Podcasts | Audiobooks | Ebooks |
| 3 | RSS Reader | Games | Write |
| 4 | Email | Calendar | Maps |
| 5 | Clock | Weather | Real Social |

| Page two row | Left | Middle | Right |
|---|---|---|---|
| 1 | Files | Cloud Files | Settings |
| 2 | Utilities | Create | Empty |

Utilities contains Compress, Docu View, Authentic, Format Swap, Calc, Vault,
Convert, Read Aloud, Walkie-Talkie, Flashlight and Power. Create contains Video
Edit, Audio Edit, Image Edit, Voice Record, Screen Record, and Code Edit. Phone and Camera remain in the bottom bar.

## Existing installations

OnKlav seeds this order once the bundled catalog has loaded on a fresh desktop.
Updates read saved icon positions and folder memberships without applying the
clean-install order or moving apps into folders. This includes layouts saved
before an arrangement marker was introduced.

## Icon moves

- An empty destination only moves the dragged icon.
- An occupied destination on the same page rotates only that page's affected
  range; the source provides its own free cell.
- Cross-page drops into occupied cells first use a gap on the destination page,
  including gaps before the target. Only a completely full destination page may
  spill toward the next available following slot/page. Preserve all IDs and
  sparse cells outside the affected range; respect the 64-page capacity.
- Releasing near an edge no longer changes page immediately. Page switching
  requires the existing 550 ms edge dwell; a drop during its animation uses the
  selected target page. The 450 ms folder hover-opening is retained.
- While adding an app to an open folder, helper text now describes dropping it
  into that folder rather than describing the drag-out gesture.

DesktopFoldersTest and DesktopFoldersUiTest cover the clean-install order and
preservation of existing layouts.
