# Shroom

A single-player landscape shooter for OnKlav. Something has gone feral beneath the foundry. Explore five branching sectors and crack the Cap King's crown.

## Play on a phone

In OnKlav, open the Games manager, choose **Install a game**, and select **Shroom.onklav-g**. Return to Games and open Shroom. The minimum host is OnKlav 0.100.57 (build 166).

- Move with your left thumb and drag the right side to aim.
- Hold **Fire**, use **Dash** to evade spores, and tap **Swap** to change weapons. The Nailgun is fast at range. The Sporebreaker hits hardest up close.
- Heal at the glowing Biogel kits. After each sector, choose healing, higher damage, or faster movement.
- Read the mission briefing in the safe airlock, then follow the arrow through illuminated doors. Recover the marked items or reset equipment, then reach extraction. Room connections and objectives change between runs.
- **Touch controls** adjusts sensitivity, handedness, opacity, and size. OnKlav's top bar handles pause, resume, sound, and saved progress.

Keyboard controls are WASD, arrow keys or mouse drag to aim, Ctrl or Space to shoot, Shift to dash, and Q to swap.

## Source

Open `project.godot` in Godot 4.7.2. The package uses OnKlav Game SDK 0.14.1 and the matching Web export template. Use the SDK's `gamekit.py check` and `preview` commands for testing. Signed updates require the original private publisher key and a higher manifest version code.

`expedition.gd` generates connected room layouts and guides movement between rooms. `main.gd` runs encounters, objectives, and combat. `shroom_hud.gd` draws the HUD and weapon effects. `tests.gd` checks combat, save validation, sector and boss progression, defeat, and replay. The included soundtrack is Below the Mycelium, packaged as `assets/below_the_mycelium.ogg`. Original graphics and twelve effects are included. To regenerate the graphics and effects, run `python3 make_assets.py` with Pillow, DejaVu Sans Bold, and FFmpeg with libvorbis installed.
