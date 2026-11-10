# Last Light Express 0.2.2

The courier crosses a moving train in ten levels, each with an interior, a roof, and a junction checkpoint. The selected branch changes the finish requirements. The Depot restores health and requires docking inside the locomotive. The Floodline adds live floodwater and requires lighting the beacon on the locomotive roof.

## Play

In OnKlav, open Games and select Last Light Express. Rotate sideways for the expanded game with compact overlay controls. Turn upright to hold play and restore the OnKlav toolbar. Hold a direction and tap Jump toward a ring to tether. Drop ducks below the roof. Strike works at close range. At Junction 07, Jump selects the Depot and Strike selects the Floodline. A desktop Godot run also supports A/D, Space, S, J and K.

Pausing and resuming uses the OnKlav session contract. Before Junction 07, a resumed route starts in the mail car. After that checkpoint, a resume starts on the signal car and retains the junction decision and beacon state. The game uses the shared landscape Web runtime and a stable, APK-owned save origin.

## Build

Run `python3 scripts/build_last_light.py` with the repository's Godot 4.7.2 and Web template configured. The export verifies the shared engine hashes before packaging content. `python3 scripts/build.py --variant optimized` builds and verifies the ARM64 OnKlav APK with both included game packs.

The game source and the audio renderer live in `games/last-light`. The score is one pre-mixed PCM loop. The score and effects use the browser’s Web Audio buffers on Android WebView. The game reserves toolbar space in portrait and hides the toolbar in landscape. The compact overlay controls use drawn arrows and text labels. The score, effects, character and train art are prototype assets. Physical-phone audio, frame pacing, and touch acceptance still need device testing.

## Renderer and save audit

Host build 189 reported a terminated WebView renderer. The report alone does not identify the underlying cause on the phone. This revision caps the route's internal viewport at a lower base resolution, disables HiDPI canvas backing on the web, and draws the skyline from three reusable textures. The foreground controls keep their previous layout.

A separate Android save problem was reproducible in the callback contract. The game supplied a zero-argument callable where the host passed the snapshot. Final victory commits a non-resumable checkpoint, leaving the save slot available for replay. Quit still discards the session. If WebGL context is lost, OnKlav now shows its recovery screen, and renderer diagnostics distinguish startup from an initialized game. Repeated unresponsive callbacks share one dialog.

The headless regression suite exercises both branches, touch and resize behavior, audio metadata, the host save callback, and 36,000 simulation ticks with 20 portrait/landscape cycles. Physical Android WebView launch, GPU memory, and frame pacing remain unverified.

The world camera is zoomed in by 18% around the courier’s forward view. Touch controls and HUD retain their established size and screen coordinates.

The 0.1.5 update increases the world view by another 10% and gives the courier and enemies an additional 8% visual scale. Each branch ends at a marked finish on its own deck. The first completion opens Night Run, which reuses the train layout with faster bridges and a tougher set of enemies. Checkpoints retain the stage and pending completion screen.

The campaign has ten stages with seven to twelve cars. Later routes vary roof heights and gaps, add timed electric strips, and introduce charging runners and ranged sentries. Health, shield, and charged-strike pickups appear on freight cars. The Iron Warden guards the final roof exit with a shield and a two-shot volley in its second phase. Campaign saves retain the current stage and retry screen. Music and effects use the stream backend, including on Android WebView.
