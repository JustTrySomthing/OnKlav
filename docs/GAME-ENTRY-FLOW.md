# Game entry, pause and quit

Source host0.100.57/code166. Games always opens to its catalog. Returning from another tab or application never opens a game entry dialog. There is no retained game_start_menu intent and no automatic catalog menu effect.

Only an explicit Play opens the selected title's native entry flow. The host reads only that cartridge and match slot before creating any WebView or loading Godot:

- No resumable session (including a completed or explicitly discarded session): show the established artwork/title/mode Start dialog.
- Resumable session: show Resume / New Game / Cancel. Resume starts the loader once and restores directly into play. New Game opens the ordinary mode dialog without loading the engine or deleting the old checkpoint. Cancel or Android Back closes the native entry screen without changing any save or showing an extra save warning.
- After Start/Resume is chosen: create one renderer, show the purple loader once, then enter play without another entry dialog. Friend invitations retain their native connection flow before loading. Corrupt saves are reported explicitly and retained for recovery.

The entry window is transparent over the catalog and disables Android's activity preview. Its initial panel stays transparent. The panel becomes opaque only when a runtime is actually requested. MainActivity has no saved menu token to re-trigger later. All catalog, package-manager and friend entry routes share WebGameActivity's preflight.

Pause preserves the session and toggles its Play icon. Return checkpoints and awaits acknowledgement, closes the renderer, and returns to the list. Foregrounding a still-running game leaves it paused with the pause notice, without opening New/Resume. Resume/New is a selected-title entry choice, not a foreground side effect.

Confirmed Quit first disables further checkpoints and drains pending writes, then awaits an acknowledged session clear. Clear commits a non-resumable tombstone; legacy browser saves cannot resurrect it. The host rejects later session writes from that discarded renderer, even if authored game code issues one. Quit then explicitly closes the host activity. Permanent data.* profiles remain intact. Failed clearing keeps the game open with retry. Air Hockey's dedicated controller follows the same rules.

The shared Game SDK understands host entry protocol1 and consumes the selected New/Resume intent once. Initial New calls start_new; initial Resume validates and restores then resumes directly. In-game New uses local shared controls without unloading and reloading the engine. Older signed third-party packages are not rewritten; developers must upgrade their helpers and publish using their original publisher key. Current generated/upgraded packages require code166; old-host fallback remains in the helper.

The original legacy user:// files and migration validators remain. Host storage is authoritative once it has a record or tombstone. The new launch system leaves legacy files in place; explicit Start is a new session and replaces the host checkpoint only after successful initialization/save.

## Verification

Focused Android entry/catalog/store/window/friend tests, all six game rule checks and SDK tests, plus browser flows with delayed save/discard acknowledgements. Browser checks cover initial Start, direct Resume, Return, Quit and focus-loss checkpoint attempts during discard for all six games. Phone WebView/OEM process death, storage pressure and physical-device presentation remain acceptance work. No APK is built in this source-only change.
