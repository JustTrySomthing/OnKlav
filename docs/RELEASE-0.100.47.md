# OnKlav0.100.47-rc1 / code156

Fixes stale saved-game detection after toolbar Return/Android Back: game and catalog use a fresh, locked atomic session marker across Android processes. Returning to the same saved game now enters its existing saved-game chooser. First play and explicit Quit/New retain the normal start flow. Existing preference state migrates once and cannot reappear after clearing. Gameplay/checkpoints, SDK, audio and dialogs are unchanged.

Three focused framework-host regressions passed in the preceding source increment. Optimized ARM64 assembly, release lint and APK signature/version/content/ZIP/16KB alignment verification now pass. Original package/signer retained. See GAME-SESSION-STATUS.md, game-session-status-verification.json and apk47-verification.json. Physical Samsung Back/gesture acceptance remains pending.
