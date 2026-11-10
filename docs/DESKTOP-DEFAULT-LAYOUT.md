# Requested desktop layout and page-safe moves — 2026-11-01

Default pages match the owner's two reference screenshots, read left to right:

| Page one row | Left | Middle | Right |
|---|---|---|---|
| 1 | Browser | Email | YouTube |
| 2 | Radio | Podcasts | AudioBooks |
| 3 | eBooks | RSS Reader | Games |
| 4 | Calendar | Clock | Weather |
| 5 | Write | Social | Maps |

| Page two row | Left | Middle | Right |
|---|---|---|---|
| 1 | Utilities | Create | Settings |
| 2 | Files | Cloud Files | Empty |

Utilities retains Compress, Docu View, Authentic and Format Swap and adds Calc,
Vault, Convert, Read Aloud, Walkie-Talkie, Flashlight and Power. Create contains
Video Edit, Audio Edit, Image Edit, Voice Record, Screen Record and Code Edit.
Phone and Camera remain exclusively in the bottom bar.

## Existing installations

Once the installed app catalog is ready, arrangement_version 2 applies the
requested order and seven Utilities moves plus Voice Record to Create. Existing
canonical folders are reused; if missing, a matching named folder is reused or
the requested folder is recreated once. Unrelated custom folders/memberships and
extra apps remain available. The new order is applied to the current desktop
catalog, without extracting unrelated apps from user folders. Folder membership,
positions and migration version are saved in one preference edit. Afterwards,
custom moves/folder deletions survive reopening and catalog refreshes. Malformed
folder data remains protected rather than overwritten.

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

Validation: desktop-default-layout-verification.json and docs/evidence/desktop-layout.
Packaged in the subsequently requested 0.100.34-rc1/code143 APK; see RELEASE-0.100.34.md.
Native device gesture/rotation acceptance remains separate from host pointer/UI fixtures.
