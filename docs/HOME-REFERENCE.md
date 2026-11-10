# Home and Settings reference restoration — 0.8.4

Reference: Inner Circle 2.2.85. This pass changes the new Kotlin/Compose host;
it does not embed or compile the legacy application.

| Reference | New production path |
| --- | --- |
| Three columns, five rows, intentional empty positions | `OnKlavScreen -> AppsDesktop -> LauncherLayout`, immutable sparse positions in private preferences |
| Long press to reorder or place in a bottom row | Viewport-owned pointer gesture, live preview and valid-drop commit; cancel restores the snapshot |
| Hold 500 ms at a horizontal edge to change/create a page | Bounded 64-page model; one turn per edge entry; release in edge gutter also turns |
| Horizontal swipe and page indicators | HorizontalPager, saved current page, 32 dp dot strip; selected dot radius 3 dp, others 2 dp, 12 dp pitch |
| Large-font/short-screen access | Minimum cell based on label size, vertical scrolling and held-icon edge scrolling |
| Accessible movement | Earlier/later and previous/next-page actions use the same position model |
| Original app artwork | Declarative vector resources extracted from the original glyph geometry; 56 dp icon frame, 10 dp inner padding, 12 sp two-line centered label |
| Bottom navigation | Original five glyphs, 24 dp artwork, 11 sp text, 68 dp targets and reference selected-fill insets |
| Main headers | 48 dp row, 22 sp title; three Messages actions, circled-plus Groups action, right-aligned italic Games hint |
| Inbox / Archive / Trash | Shared reference controls and compact search/selection row on Messages and Groups; Messages folders are local to this host and do not delete provider messages |
| Original game cards | Original four thumbnails, 16 dp card corners, 12 dp padding, 64×68 dp artwork, 19/14 sp text, 56 dp Play column, 2 dp outline; reorder retained privately |
| Settings Apps and Games | Separate package-center screens: included catalogs, download actions, hide/show menus, protected system items, install and guide footer; never aliases for launcher tabs |
| Settings icons and menus | Original 20 dp glyphs and 14 sp tile text; Sounds, Security, Storage and Help submenus; reference-style Notifications, Spam, Backups, Storage, Walkie Talkie, Quiet Hours, Reminders and Privacy panels |
| Full-page settings | Setup checklist, local Profile editor with bounded private photo and Save/Cancel, Google Drive connection layout |
| Read Aloud settings | Dedicated voice/speed/preview dialog using the existing phone speech engine preferences, rather than opening the Read Aloud application |

## Artwork provenance

`android/shared/src/main/res/drawable/ref_*.xml` are static icon artwork, not
ported Java classes. `scripts/import_reference_vectors.py` emits those resources
from declarative paths. Source geometry was reviewed in Navigation, AppsPane's
AppGlyph, WalkieIcon, SettingIcon, ContactHeaderAction, ComposeIcon, BrowserIcon
and ActionIcon. Original implementation classes are excluded.

`android/app/src/main/assets/catalog` retains the original Games artwork and its
provenance files (hockey, naval, blackjack and the Cats vs Dogs catalog image).
It contains no original executable code, game packages or APK.

## Limits that remain visible

The SDK runtime, author trust, permission broker, installation/update/removal and
portable built-in app/game export are still unfinished. The package picker calls
the existing Rust archive inspector and reports its result; it never authorizes
execution or reports a completed install. Included apps/games can be hidden or
shown but cannot be deleted or exported as pretend SDK packages. No per-app APK
is produced.

Settings whose services are unfinished display their reference controls disabled
with an explicit availability note. No nonfunctional Save button pretends to
activate spam protection, quiet hours, message notifications, backups, reminders,
voice chat, Drive, SIM management or game compatibility settings. The original
included speech model is not imported in this UI pass. Android permission and
notification-settings links open the host's system settings where supported.
Profile fields/photos are private local metadata; no verification badge or
profile-sharing claim is made.

Groups and original games are not complete ports. The Group editor cannot save
or create a conversation. The Games launch dialog identifies the current engine
previews and does not claim multiplayer works. No private Inner Circle data was
copied, migrated or deleted. The Appearance 0.8.3 idle-timeout evidence remains
historical; this pass must not retroactively label that regression passing.

## Verification

See `verification.json`, `apk-verification.json` and `evidence/v0.8.4` for exact
build, unit, installation, gesture, runtime and visual results. Five model tests
cover sparse positions, occupied reordering, recreation encoding, page bounds,
catalog reconciliation and hit testing. Device tests exercise the production UI
and retained preferences. App captures and reference captures are separate from
real-phone, carrier, performance and full-product acceptance.
