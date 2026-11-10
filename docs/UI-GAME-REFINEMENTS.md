# App and game refinements

The shared Android ClosePageButton is now a transparent IconButton with its existing 48dp touch target, purple X and owning page callback. This covers the Home/Page headers, SDK app frames and slide-out headers. Existing CompactAction close controls were already transparent. Canonical GameChrome uses an unboxed X across all six bundled games and the Game SDK; other toolbar controls keep their styling. Keyboard focus remains visible.

The call control always reads Speaker. It switches immediately to the system Speaker route and restores the prior available non-speaker endpoint when turned off (earpiece, then another available private route if the former endpoint disappeared). Actual Telecom callbacks determine its colored On / gray Off state. Android 14+ endpoint types and legacy route masks are used, independent of localized names. Unavailable routes disable the control or show an inline error; no audio-choice dialog is opened. Call lifecycle behavior is preserved.

Cats vs Dogs normal fall time is now 0.50 + row * 0.045 seconds, with a 0.65-second landing hold. The next AI turn waits at least 0.9 seconds after the landing, or the remaining report duration plus 0.2 seconds. Three-connected reports last 1.7 seconds. Reduced motion keeps a short fade and a readable 0.60-second hold. Commits, rules and saved journals are unchanged.

Neon Dominos has dark neon tiles with luminous pips/edges, cyan/pink perimeter runners, an active-score rail, playable-tile glints, energy flight trails and expanding landing rings. Effects remain bounded (5 rings/120 particles), never change the model, and respect reduced motion. Nova is now your rival throughout the game and catalog, with singular/plural tile, point and round wording. All-visible rack, readable chain, drag/tap controls, save schema and AI remain intact.

Naval Battle uses selected styling for normal, pressed, hover and disabled fleet tabs. Your Fleet retains its green highlight during the incoming flight, impact/report hold and final 0.55-second hold; the highlight changes when the displayed board changes. Busy tabs remain disabled. Rival feedback uses complete grammatical phrases.

Single-player-only catalog dialogs use a green Start button with a 48dp minimum height and 144–204dp width instead of the oversized mode card. Friend-enabled mode choices retain their existing flow. The Game SDK documents the new close and Start presentation, and recognizes the updated canonical helper for upgrades.

## Verification

23 focused Android checks passed for speaker routing/selection, close action/touch target, solo Start size, game catalog and call lifecycle. A final 6-test catalog/UI run passed after the catalog wording update. Fourteen SDK unit methods and all shared game/session/model/layout checks passed. A browser fixture exercised real Cats falling/hold/AI timing, Naval selected-disabled style through the complete incoming turn, Dominos flight/impact/effect bounds and reduced motion. Dominos was visually reviewed at 432x800 and 320x640. Native raster capture was unsupported by this headless Robolectric setup; transparent/green styles were inspected in source, with Compose semantics, actions and bounds tested. Phone hardware route/listening and physical-device performance remain untested.

Current source is 0.100.56/code165. No APK was requested or built; the latest delivered APK remains 0.100.55/code164. Evidence is in evidence/ui-game-refinements and ui-game-refinements-verification.json.
