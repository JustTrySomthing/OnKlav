# OnKlav data-only theme packages, version 1

Themes import inside Settings > Appearance > Load & Save Themes. They are not
Android applications. The current extension is `.onklav-t` (the original spoken
`.onflav-t` spelling remains a naming question; no executable content is involved).
This is the new OnKlav format; it does not reuse or execute legacy Java code.
Legacy .ictheme parsing/migration is not implemented in this pass.

A package is a ZIP containing exactly `theme.txt` and, for an included image,
`background.jpg`. No folders, duplicate entries, scripts or other filenames are
allowed. Compressed size is at most 8 MiB; settings 16 KiB; image 6 MiB. Images
are decoded with pixel limits, orientation correction and normalization into a
new private host filename. Archive entry paths are never extracted to disk.

`theme.txt` is strict UTF-8, one key=value per line. Empty lines and # comments
are permitted. Duplicate, unknown, missing or out-of-range settings are rejected.
Use the Appearance menu's Download Theme Template to get a valid starting ZIP.

| Key | Value |
| --- | --- |
| format | onklav-theme |
| version | 1 |
| name | 1–40 characters, no control characters |
| dark / gradient | true or false |
| palette | 0–14; stable order in Appearance.kt |
| font | 0–5; Clean Sans, Handwritten, Classic Serif, Narrow Sans, Typewriter, Serif Italic |
| size | 16, 17, 20, 23 or 26 |
| background | Empty for none, a built-in background ID, or image with background.jpg |
| overlayDark / overlayLight | 0–100; larger overlay obscures more of the wallpaper |

Internal custom background IDs are never exported. New exports embed every selected
wallpaper, including built-in artwork, optimized to at most 1 MiB. The host embeds the image
and converts the ID to `image`. Import stages the appearance for review; Import
saves the named theme and draft, and Apply activates it. Replacing a same-name
theme requires explicit confirmation. Named-theme and personal-image limits are
40 and 100 respectively. Removing an image requires confirmation and clears its
references from active/saved appearances; it is separate from staged color edits.

Backgrounds is a separate Save/Cancel draft. Its opacity slider shows image visibility
(100 minus the stored overlay), with independent Dark/Light values; it is disabled
without an image. Input pictures are bounded to 30 MiB / 100 MP and normalized to
at most 2048 pixels on the longest edge. Provisional images are discarded on cancel;
saved personal backgrounds and named-theme images remain available.
