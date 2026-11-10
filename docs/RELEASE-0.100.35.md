# OnKlav 0.100.35-rc1 / code 144

ARM64 owner-testing APK with the original package and signing certificate. Install as an update; the single APK contains the host, shared Rust services, signed internal apps and Godot games. This is the fast unshrunk debug variant, matching the previous delivered build’s variant, rather than a size/performance comparison release.

Includes all six audit repairs (transactional Files filing/recovery, destination limits and Cloud workflow, logical SMS/MMS receipt ordering, Calendar recurrence cutoffs and reminder failure/retry behavior, and Cloud encrypted-account budgets). See AUDIT-REPAIRS-2026-11-01.md.

Phone and Camera now open directly within the persistent bottom menu without sliding or root back arrows. Phone has compact top tabs, adaptive keypad spacing, a centered Call action with bottom clearance, and a direct Use OnKlav action for Android’s Default Phone approval. The protected native InCallService and private own-call activity remain registered. Setup already includes Default Phone selection, app access, notifications and lock-screen calls/alarms. Android still requires user consent and retains emergency behavior. See PHONE-CAMERA-ROOT-NAVIGATION.md.

The preceding source tasks passed61 unique focused Android audit regressions,30 Python tooling checks, and24 focused Phone/Camera host checks. These are retained prebuild results, not a new test run during packaging. Final assembly and APK verification passed: package/version/original signer, ZIP integrity,16KB ZIP/native alignment, current native and bundled payload hashes, required feature classes, shared two-voice speech assets and licenses. Source-matched native/SDK/speech/game payloads were reused.

The first packaging attempt found one missing local dependency. Its exact official artifact was restored with checksum verification; the final offline build passed. See apk35-verification.json and evidence/v0.100.35.

Physical phone/carrier, Camera capture, lock-screen/OEM and Bluetooth acceptance remain separate. No install/data-reset action was performed.
