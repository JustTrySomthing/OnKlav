# Utility icon refresh

Format Swap now shows two files and opposing conversion arrows. Screen Recorder uses capture corners and a recording dot; Voice Recorder uses a diagonal handheld microphone and cord; Vault uses a safe door, wheel, hinges and handle. User references informed original vector geometry; attached raster/watermarked artwork is not copied or bundled.

All four use transparent32dp vector resources with consistent visual weight. Desktop uses `Icon(... tint=primary)`: removing the previous opaque tiles also prevents the Recorder/Vault glyphs becoming solid tinted blocks. Existing desktop outer tile, theme colors, resource names and catalog routing remain unchanged. No functional app/SDK/native changes.

XML parsing, viewport/stroke bounds and desktop/compact renders were checked and visually inspected in light/dark palettes. Preview is a vector-path rendering rather than a phone screenshot. No Android compilation or new APK was needed/performed for this resource-only update;0.100.27-rc1 remains the latest delivered APK and predates the new icons.
