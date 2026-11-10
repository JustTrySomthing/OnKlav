# Saved-game catalog detection correction

The game already checkpoints/pauses on toolbar Return and Android Back. Its WebGameActivity runs in :games, while the catalog runs in the main Android process. The paused-game indicator used cached SharedPreferences; the catalog could keep an old empty value, opening the normal start dialog instead of launching the saved-game chooser.

GameSessionStatus now uses an AtomicFile under noBackupFilesDir. Each read reloads the tiny marker, with an OS file lock and a per-process thread monitor around reads/writes. Return writes synchronously before opening the catalog. Resume/background use the same marker; Quit/New clear it. Existing preference state migrates once; a cleared empty file prevents stale legacy data from resurrecting it. Reads are bounded and I/O errors do not crash the app. The actual game checkpoint format/content, save/pause implementation, gameplay, audio, SDK and dialogs are unchanged.

Three focused framework-host tests pass: an already-created catalog reader observes game-writer save/change/clear; existing markers migrate but cannot reappear after Quit/New; oversized/invalid hints are rejected and valid installed-game IDs survive. Production and test Kotlin compile. Shared game assets remain source-matched without rebuilding them. These checks exercise independent store instances; real Samsung cross-process/Android Back acceptance remains separate.

Next source0.100.47/code156. No new APK for this increment; delivered0.100.46/code155 predates this correction. See game-session-status-verification.json and evidence/game-session-status.
