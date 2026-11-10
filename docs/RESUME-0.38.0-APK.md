# OnKlav 0.38.0 debug APK verification

APK: OnKlav-0.38.0-debug.apk  
Version: 0.38.0-dev (45)  
Package: app.onklav.home.prototype.rewrite  
Size: 127,996,207 bytes  
SHA-256: `ec3d72c1272ddf5e283c826d25c97e8b5d269f0e811e5ebc24aa33f85df03d84`

- Existing signing certificate retained; APK v2 signature verified.
- ZIP integrity and 16 KB packaging/native alignment verified.
- Current Rust libraries included for arm64-v8a and x86_64, matched to build provenance.
- Eight signed internal app packages rebuilt and checked inside the host APK.
- One Android application; no nested APKs or legacy Inner Circle implementation classes.
- Zero authored Java source files. Kotlin/Jetpack still uses Android's runtime and the JDK build tools.
- 141 Rust and 125 Kotlin/framework-host tests passed: 266 total, zero failures.
- Compiled runtime classes checked against the current Kotlin compilation.

This is a development APK. Installation/startup on a real phone and live carrier
SMS/MMS/Groups/Walkie operation have not been verified. MMS_READY remains false;
the build does not enable the default SMS role. Other migration gaps remain.
Debug identity is separate from the original working OnKlav application.

The initial native packaging failure and one recovery UI test timeout are retained
in the source evidence. Output-directory creation and deterministic SDK startup
in that UI test were corrected before the successful full verification. No checks
were skipped or timeouts increased. Full details: docs/verification.json and
docs/apk-verification.json in the source archive.

Next work, after Charles approves: finish and verify Messenger SMS/MMS transport,
then Groups and Walkie-Talkie. Carrier acceptance requires real phone/SIM testing.
