# Main navigation and desktop folders

Current layout/move update: see DESKTOP-DEFAULT-LAYOUT.md.

The main bar is Phone, Camera, Apps, Messages, Groups. Existing message/group
route identities stay stable. Phone and Camera open their normal native app
routes and return to the previous home page. Settings and Games are desktop apps;
Settings remains visible even if an old visibility preference says to hide it.
Their Android Back and header Back controls return to the previous OnKlav page.
Settings child pages return to Settings rather than losing their parent route.

Each Apps-button activation advances a reset key: the leftmost apps page opens,
including when already on Apps. A normal app return or background/foreground cycle
does not advance that key. Persisted old page numbers are no longer used to choose
the opening page. Existing sparse icon positions and page drag/reorder controls
are retained for apps that remain on the desktop.

## Folders

- The top-right outline folder-plus opens a themed name dialog. Names are trimmed,
  1–40 characters, with no control characters. Folder icons are solid theme-tinted
  folders with the first uppercase character in the middle.
- Defaults group Utilities (Compress, Docu View, Authentic, Format Swap, Calc, Vault,
  Convert, Read Aloud, Walkie-Talkie, Flashlight, Power) and Create (Video Edit,
  Audio Edit, Image Edit, Voice Record, Screen Record, Code Edit). The explicitly
  requested arrangement-version 2 update restores these destinations once if
  missing; subsequent folder deletion/renaming is preserved.
- Long-press an app and drag onto a folder. Hover opens it after 450ms; release
  within the open folder or on the original folder icon to add it. The open
  folder is a same-window centered modal surface, preserving the pointer stream.
- Open a folder, hold an app, drag outside the surface and release in the desktop
  viewport to move it back to Apps. Cancellation/out-of-viewport releases preserve
  membership. Dragging a folder reorders it; folders are not nested.
- Nine icons per folder page; swipe or use previous/next controls for more. There
  is no nine-app membership cap. The existing overall desktop/catalog capacity
  still applies. Folder pages can scroll vertically on short/large-font screens.
- Rename and Delete folder are in its options menu. Deletion is confirmed and
  returns apps to the desktop without uninstalling or deleting app data.
- Accessibility custom actions offer Move to [folder] and Move to Apps in addition
  to desktop position/page movement. Hidden or uninstalled apps are not rendered
  inside folders; their saved membership is retained for a later restore.

Membership and sparse positions are written together in apps_desktop preferences.
Malformed folder data is reported and not overwritten. Explicit empty folder data
is different from first use. These are launcher folders, not filesystem folders.
No Rust, SDK guest capability, network service or app-private-data migration is
introduced. The latest layout refinement is included in APK 0.100.34-rc1; see RELEASE-0.100.34.md.

The original folder implementation’s production compilation, 11 focused host data/UI checks and full production/test lint passed with no errors. Checks include physical pointer gestures in the host fixture, page reset, persisted grouping, create/rename/delete, Settings/Phone/Games navigation and small-screen large-text access. See desktop-folders-verification.json and evidence.
Real-device touch, accessibility, rotation and launcher/OEM acceptance remain.
