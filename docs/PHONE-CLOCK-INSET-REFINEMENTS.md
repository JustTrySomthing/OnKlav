# Phone, Clock and inset refinements — 2 November 2026

These source changes are included in the subsequently requested 0.100.38-rc1/code147 APK. See RELEASE-0.100.38.md and apk38-verification.json.

- Phone: keep the keypad and controls in one non-scrolling workspace. Center the number in the flexible region above the keys and Save/Call/Delete in the flexible region below. Both regions grow with available height; key sizing reserves equal control regions and compact spacing for small screens.
- Clock: use the same sentBrush appearance helper as message bubbles for the main clock card, selected tab and enabled alarm cards. This shares theme colors, dark-to-light direction, angle and gradient-off behavior. Enabled alarm text uses the matching sentInk foreground.
- Games/full-page routes: paint solid theme status/navigation bar backplates above the wallpaper before inset content. The Games catalog retains its wallpaper within the content area.
- Embedded landscape Camera: paint the entire workspace black before applying horizontal safe-drawing insets, covering the exposed left/right gutter without placing camera controls under the cutout.

Validation: production and all test sources compiled; all 22 selected/shared checks passed (five Phone/Clock UI cases and 17 shared checks), runtime class bytes match compiled Kotlin, and Phone/yellow Clock captures were visually reviewed. Details are in screenshot-refinements-verification.json. Physical Samsung rotation/cutout acceptance remains separate from framework-host checks.
