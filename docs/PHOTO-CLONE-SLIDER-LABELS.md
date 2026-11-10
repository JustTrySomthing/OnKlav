# Image editor labels and slider icons

Renamed the visible Stamp tool, sampling accessibility text and applied/no-match feedback to Clone. The internal clone operation and saved edit data are unchanged. Added a compact theme-tinted leading icon to the shared image editor slider for brush/stroke size, text size, hardness, color, geometry, effect strength, quality and other adjustments. Labels, numeric values, reset and fine-step controls remain accessible. Updated the existing retouch UI expectation.

Verification: inspected all changed strings and confirmed every icon references an existing shared vector resource. This small UI-only change was not compiled or device-tested; no APK was requested or built.

Compiled and packaged in the verified optimized 0.100.39-rc1 APK. Physical device testing remains separate.
