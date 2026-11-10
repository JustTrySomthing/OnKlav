# Your OnKlav game

Open project.godot in Godot 4.7.2, or use gamekit.py run/check/build.
Use OnKlavGame pause/resume/save/load. Validate your own saved state before applying it.
Handle save failures visibly; do not discard damaged saves automatically.
The template uses its reverse-domain ID for a separate user data directory.
Touch targets must remain usable on small phones; test actual touch, audio and lifecycle.
No Android export template, Android SDK, per-game APK, native extension or C# is required.
Share the signed .onklav-g directly; users choose whether to install it. No owner approval.
OnKlav supplies one shared Godot web engine; packages contain game content, not a native engine.
Game JavaScript/WebAssembly has no Android bridge or host-file access. This first runtime is offline.
There is no guest Files, TTS, account or network multiplayer broker yet. Do not invent these APIs.

Follow GAME-CONTROLS.md in the SDK. Defaults include the OnKlav Games boot image, shared audio buses, centered dialogs, paused label/Play pulse, and original catalog start routing. Studio splash overrides are supported through project.godot.
