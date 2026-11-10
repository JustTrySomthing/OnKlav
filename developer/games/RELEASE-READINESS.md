# OnKlav game developer readiness

Baseline: SDK 0.14.1 tooling, Godot 4.7.2 Compatibility Web export, OnKlav Android build 174. This audit covers the local SDK, package verifier, Android launcher and WebView host, lifecycle, storage, and starter libraries. It does not qualify Android devices or certify the sandbox against hostile games.

## Game coverage

| Game type | What works with today's contracts | Remaining developer or platform work |
| --- | --- | --- |
| Puzzle, match-three, word, trivia, rhythm-lite, card, board, idle and arcade | 2D scenes, touch, audio buses, timers, local profile and session saves | Author game-specific rules, level data and UI. Rhythm games need handset latency measurements. Idle games should calculate elapsed time on resume. |
| Platformer, runner, top-down RPG, twin-stick shooter and tower defense | 2D physics, multitouch starter controls, gameplay state, steering and bounded grid pathfinding | Add genre-specific maps, progression, content and enemy behavior. |
| Lightweight 3D racing, first-person action, sports and simulation | Compatibility renderer, 3D physics and first-person starter | Add cameras, vehicles, AI and control schemes. Measure sustained performance and memory on real phones. |
| Pass-and-play, same-device and real-time multiplayer | Outside the supported profile | SMS-based remote turns are the only supported multiplayer mode. |
| Remote turn-based board/card games | Host-owned SMS turns after an accepted invitation | Small turn messages only. Test delivery and recovery with two physical devices. |
| Real-time online multiplayer, cloud saves, social leaderboards and live content | No supported host service | Requires a separately versioned networking or platform service. |
| Store purchases, ads, notifications, haptics, sensors, controllers and AR | No portable OnKlav API or device support guarantee | Define opt-in capability contracts and handset acceptance before promising these features. Touch and keyboard starters are available. |
| Large or high-end 3D games, native plugins and C# | Current renderer, package budgets and Web sandbox constrain these games | Requires a different runtime profile. The current profile is single-threaded WebGL2 Compatibility. |

The templates are examples rather than required game architectures. Developers can author other genres with the supported Godot scene and scripting features. The shared toolkit handles common controls and lifecycle. It does not need a template for every genre to preserve compatibility.

## Compatibility before a stable developer release

1. **Package and engine.** Android build 173 selects an allowlisted bundled engine by exact version and inventory, and keeps the game origin independent of the engine. The SDK can verify against a retained descriptor. Keep the current engine pinned until a second real bundle is packaged and tested alongside it. See [ENGINE-COMPATIBILITY.md](ENGINE-COMPATIBILITY.md).
2. **Host bridges.** Treat controls v2, entry, save and SMS turn protocols as separate compatibility surfaces. Add capabilities by explicit version negotiation. Maintain earlier behavior for packages that omit them. Package options should reflect what that build actually supports. Never grant ambient Android permissions.
3. **Game updates.** Keep game ID and publisher key stable and increase version_code. Preserve the save origin across host updates and game reinstall. Game authors own payload migrations. An unreadable save needs an explicit recovery path. Future runtime routing must preserve the origin as well as the package signature.
4. **Session ending.** The game owns its results UI, completion state and permanent rewards. Save permanent progress in a separate named slot, make reward commits idempotent, and handle failed saves before claiming success. Choose whether finished sessions can reopen on their results screen. Set is_resumable explicitly if they should start at entry instead. Use the shared replay action and test end, replay, return, resume and quit. The SDK cannot infer completion from a game-specific score or state field.
5. **Reference packages.** Keep old signed packages and known saves as immutable fixtures. Check new hosts against prior packages for install, launch, pause, update, remove, reinstall, recovery and storage isolation. Check a new SDK package against the oldest host it declares. A desktop browser preview does not exercise the Android bridge.
6. **Release qualification.** Run a clean developer onboarding on Linux, macOS and Windows. On at least a representative lower-end Android device and the target Samsung device, exercise install/update, input and accessibility, safe areas, rendering, interrupted audio, process death, storage errors and long sessions. Include independent sandbox review and a distribution license decision before broad public release.

## Current audit changes

The SDK verifier now checks the same 200-character archive path and UTF-16 manifest text limits as Android. An invalid signed build is checked in staging before it can appear at the requested output path. Use `gamekit.py verify GAME.onklav-g --host-code 172` to check the declared minimum host against a target build. This does not substitute for installing on that host. The four previously signed starter examples remain readable under the updated tooling.

Recommended release status: developer preview for the documented offline and SMS turn-based profile. A stable public compatibility promise still depends on exercising real retained bundles, immutable cross-version fixtures and handset qualification. Online and native capability profiles can be added separately without asking existing offline developers to rewrite their games.
