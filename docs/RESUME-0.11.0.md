# Resume 0.11.0

Verified checkpoint: Notes and RSS are signed Rust/Wasm SDK apps, installed inside the sole OnKlav APK. Six apps remain: Read Aloud, Browser, YouTube, Annotation, Social and Walkie. See RSS-PORT.md for parity gaps and verification scope.

Build: source restored-env/env.sh and /root/.cargo/env from workspace; export ONKLAV_APPS_SIGNING_KEY to migration-private/apps.publisher.key. Build SDK packages, native libraries, then scripts/check_android.py. Existing Android and SDK publisher keys must be preserved.

Stalls fixed: debug R8 disabled; generated Gradle caches excluded from restore; unit checks and APK build now separate with 180-second limits; Compose ViewModels bind the current Application. Do not run concurrent filesystem/build jobs while Gradle is active. Tests invoke production Compose accessibility actions, not physical handset touch.

63 tests passed (38 Rust, 25 Kotlin). Verified signed APK has arm64/x86_64 Rust libraries, Notes and RSS packages, no nested APK or legacy Java-defined classes. Full handset, live publisher network, voice and carrier acceptance remain pending. Next port: Read Aloud based on original 2.2.85 ReaderActivity, using a brokered speech SDK service and Rust controller.
