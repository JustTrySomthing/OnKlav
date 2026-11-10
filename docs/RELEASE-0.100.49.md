# OnKlav0.100.49-rc1 / code158

Includes Naval Battle start recovery and the restored Inner Circle opponent/number/contact/side/connection flow. Existing games reopen their recovery controls instead of an error-only dead end. Cancelling a pending friend attempt releases its slot; Try Again starts a fresh verification window. Contact hold-to-reverify works independently of game invitations. Original readable SMS now names OnKlav.

All14 preceding focused host checks passed (8 broker/verification,3 saved-session and3 rendered UI/routing methods). Opponent, number and connection-failure dialogs were visually reviewed. Optimized ARM64 assembly, release vital lint and APK signature/package/version/content/ZIP/16KB checks pass. Original signer and package are preserved; install as an update. No broad test suite was repeated for the version/package-only change. Physical Samsung/two-phone/carrier acceptance remains separate.

This APK contains GAME-START-AND-FRIEND-DIALOG-REPAIR.md and all preceding source fixes. Earlier source-increment notes saying no new APK are historical. See apk49-verification.json and evidence/v0.100.49.
