# OnKlav 0.100.33-rc1 / code 142

Fast unshrunk ARM64 debug build with the existing package and original signer.
Install as an update. Source-matching native/SDK/speech/game outputs were reused.
No additional size optimization or broad testing loop was performed.

Includes the latest Phone/desktop refinement: Phone and Camera remain in the
bottom navigation and are excluded from the desktop/folder contents; folder
edge-hover reserves its tile before reorder and opens the same-window folder
popup; Phone settings now offer internal voicemail access-number, blocked-number
and ring-volume controls instead of external settings shortcuts. Native in-call
presentation has an OnKlav header/status badge, foreground new-call routing and
a regression safeguard keeping Add call's keypad usable. Contact editing remains
inside OnKlav. Android-owned consent and emergency-call UI remain system controlled.

Before assembly, 32 focused checks passed and full production/test lint reported
zero errors. APK assembly and verification passed: package/version/original signer,
ZIP integrity, 16 KB ZIP/native alignment, current feature class definitions
including PhoneLocalSettings/PhoneSettingsUiKt, and matching bundled payloads.
Assembly took 51.01 seconds and verification 3.98 seconds on this workspace.
See apk33-verification.json and docs/evidence/v0.100.33.

Real carrier calls, OEM lock/full-screen behavior, audio routes and live cloud
accounts still require device acceptance; this build is not store certification.
