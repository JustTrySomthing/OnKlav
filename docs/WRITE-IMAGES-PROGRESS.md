# Write embedded images — source work in progress

Added picker-based embedded images, bounded PNG document marks, orientation normalization and native rendering. Write documents retain image data across save/reopen; HTML embeds data URLs and PDF uses the image-bearing layout. DOCX with images is disabled in UI and rejected before opening an output destination. Plain-text export warns that formatting and images are discarded. HTML/DOCX image import and DOCX image export remain unsupported. Initial PDF-container host test hit Robolectric PDF native-document limitations; final check instead verifies pixels in the same layout used by PDF. Actual handset PDF output still needs acceptance.

Seven focused Write host/UI checks passed; runtime classes match. Notes migration/removal and encrypted-folder implementation remain unfinished. Native core and SDK package identities unchanged. No APK.

