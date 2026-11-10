# OnKlav 0.39.0 — chrome, motion, icon and startup

APK: OnKlav-0.39.0-debug.apk  
Version: 0.39.0-dev / code 46  
Package: app.onklav.home.prototype.rewrite  
SHA-256: `f55f980ee3e30d76d3c4a06f1b8b2d9b72c3f6138fd0a919267037030eec1a5b`

Changes requested from the Samsung screenshot:

- Header and bottom navigation backgrounds extend to both screen edges; content keeps its original inset spacing.
- Dedicated appearance-colored status/navigation inset backgrounds cover wallpaper on edge-to-edge Android 15. System contrast scrims are disabled without changing icon contrast preferences.
- SDK side drawers use eased 340 ms entry/280 ms exit and retain their contents during closing; shared popups ease into position over 300 ms.
- Your purple/white O–K speech-bubble design is recreated as editable vector artwork. Legacy, adaptive and themed launcher icons are included.
- A matching launch splash is configured for older Android and Android 12+. First draw waits for initial SDK startup, with a bounded 350 ms minimum/2 s maximum hold.

Verification: 127 Kotlin/framework-host tests passed, including full-width header/
navigation bounds on all five tabs and intermediate popup motion. The unchanged
Rust/native build retains its 141 passing tests, with current source/library hash
checks: 268 checks total. Runtime classes, APK version/signature/ZIP/16 KB alignment,
both Android ABIs, signed internal apps, vector resources and launch theme checked.
Signing identity and application ID are retained, so this updates the 0.38 debug app.

This is not a handset screenshot/pixel or carrier acceptance claim. Please confirm
the final appearance on the Samsung phone. MMS/default-role gates stay closed;
other migration gaps remain. No transport work was started in this UI correction.
Initial compile/test diagnostics and the final successful full invocation are kept
under docs/evidence/v0.39.0. Full details: docs/verification.json.
