# OnKlav0.100.45-rc1 / code154

Includes Game SDK0.3.1 controls v2: direct Pause/Play icon toggle, Sound/Settings with silent pause and Done returning directly to play, one exact Quit question with green Resume/red Quit, saved Return/Android Back and New/Resume entry with only implemented mode choices. Preserves the single immersive shared-engine game window, original package and signing identity.

Optimized ARM64 assembly, release lint and APK signature/version/content/ZIP/16KB alignment verification passed. Existing bounded game/browser/SDK evidence is in GAME-CONTROLS-V2.md and game-controls-v2-verification.json; those tests were not rerun for this packaging-only request. Full Android assembly supersedes the earlier compile-only limitation. Physical Samsung gesture/audio/process-recreation acceptance remains pending.
