package app.onklav.shared

/** Help for the new format, following the original Appearance help's complete scope. */
val appearanceHelp =
    """
Themes and Backgrounds

Load & Save Themes keeps light/dark mode, message colors, font style and size, gradient bubbles, the background and both opacity settings together. Preview changes, then tap Apply. Cancel leaves your active appearance unchanged.

Font Style

Clean Sans, Handwritten, Classic Serif, Narrow Sans, Typewriter and Serif Italic use local Android fonts. Each choice previews its own style. Fonts may look slightly different on different phones. Font style is included in saved and exported themes.

One File to Share

Export Current Theme and Export or Share produce one .onklav-t file containing the appearance settings and selected wallpaper, including built-in artwork. Save it to Files or share it. The recipient chooses Import Theme File, reviews the checked preview, imports it and taps Apply. No separate image or folder is needed. Messages and permissions are never included.

Make a Theme

Download Theme Template exports your current preview as a complete .onklav-t file. Open it as ZIP on a computer. Keep theme.txt and, when used, background.jpg at the ZIP root. Edit theme.txt with a plain-text editor, then give the ZIP a .onklav-t extension.

Keep format=onklav-theme and version=1. Fields are name, dark, palette, font, size, gradient, background, overlayDark and overlayLight. Palette IDs are 0–14, font IDs 0–5, and sizes are 16, 17, 20, 23 or 26. Booleans are true or false. Set background=image when background.jpg is included, or leave background empty for no image. Overlay values are 0–100; a larger overlay hides more of the picture. Do not include folders, scripts or other files.

Import and Replace

Imports are checked before they can be used. Review the theme name, mode, message color, size, font and included-image information. Choose another file if needed. A matching saved name asks before replacement. Import stores the theme and updates the preview; Apply activates it. The new system uses .onklav-t, not the older app's .ictheme format.

Your Backgrounds

Choose an image in Backgrounds, adjust its opacity and tap Save. Then tap Apply in Appearance to activate the preview. Cancel in Backgrounds restores the previous draft. Saved images remain available under Your Backgrounds even if the original photo is moved. Portrait pictures near 1080 × 1920 work well; pictures fill the view and may crop.

The × asks before deleting a saved copy. The original photo stays untouched. Themes that used the deleted image keep their colors and use no background. Reset to Default Background selects Cosmic Drift; No Background removes the picture. Opacity is unavailable without a background. Dark and Light keep separate opacity values.

Limits and Copies

You can keep 40 named themes and 100 personal backgrounds. Input images are limited to 30 MiB and 100 megapixels, then reduced to a longest edge of at most 2048 pixels. Bundles allow 8 MiB total, 16 KiB settings and 6 MiB image data. New exports optimize the wallpaper to at most 1 MiB. OnKlav manages private filenames. Uninstalling removes private themes and images; exported copies in Files remain.
"""
        .trimIndent()
