# Shroom in OnKlav

Shroom is included directly below Crash Test Panda on first launch after the catalog upgrade. Games can be reordered after that. Shroom is landscape and single player. The built-in entry remains visible even if an older installation hid it.

The host ships `android/games/src/main/assets/included-games/shroom.zip` as a built-in asset authenticated by the Android APK signature. The loader retains the first standalone release’s save and profile namespace. User-installed cartridges still require independent publisher signatures. The included cartridge takes precedence over earlier installed copies. A newer installed update from the same publisher can take precedence over the included cartridge. The Games catalog displays one Shroom entry.

## Expedition update

New runs begin with an objective briefing in a safe airlock. Each of the five sectors generates a connected room layout with illuminated doorways and room-based enemy detection. Follow the compass to samples, relays, canisters, pumps, or the Cap King. Finishing the first four sectors requires reaching extraction after completing the objective. Previous arena checkpoints continue with their original rules until a new run starts.

The game catalog uses a glossy transparent mushroom cutout. Shroom's custom loader keeps its progress bar and failure notice. Combat shows health and the current objective at the top, with labeled controls and a dash recharge meter. Results have a large Play again button.

## Gameplay fixes

- Consume a spore before applying damage. A lethal hit may drain the remaining projectiles, so the collision loop exits as soon as the run ends.
- Resolve wall collisions before player hits, and ignore combat input until the session starts.
- Preserve the RNG cursor, pending spawn delay, score streak and cooldowns on resume. Original checkpoints without these fields still load.
- Retry failed high-score writes with a bounded delay and display a Cap King health bar during the boss fight.

## Build and verify

The editable game project is `games/shroom`. From the source root, run `python3 scripts/package_included_shroom.py --godot /path/to/godot` with Godot 4.7.2. This runs the game checks and refreshes the APK asset. Assemble and verify OnKlav using `scripts/build.py`. The included asset is a host build input, while separately distributed cartridges follow the Game SDK signing workflow.

Shroom 0.2.1 gives the first-sector red mushrooms a tilted glossy cap, cream spots, green eyes, and a more distinct face. Other enemies share the detailed model with different palettes and identifying details. Fire, Dash, and Swap use larger touch targets and labels with layout limits for smaller screens.

The next source revision enlarges Fire, Dash, and Swap with viewport limits, adds sculpted cap gills and more expressive mushroom faces, and replaces block markers with illuminated capsules and a ringed exit. The new animation follows Reduce Motion. The web export can be tested before release. 

## Shroom 0.2.2 release candidate

The soundtrack is **Crown of Decay**, one octave below the original audition. This describes the 0.2.2 release. For the later Below the Mycelium soundtrack, see [the Phase 16 merge](SHROOM-PHASE16-MERGE.md). The game loops the rendered Vorbis audio on the Music bus. The 0.2.2 source and Web export include the latest control, enemy, pickup, and exit improvements described above. OnKlav 0.100.72 includes Shroom 0.2.2.

## Shroom 0.2.3 source checkpoint

The loader uses a custom beveled SHROOM wordmark with the game's plum, coral and mint palette. Expedition rooms have colored light pools, oxidized tiles, and seeded grilles, crates and fungal growth. The entry airlock stays enemy-free. Each sector has additional guards.

Fresh caps survive the first hit. Impacts emit green droplets, and kills throw a larger burst and leave green slicks. A damage flash and grunt accompany player hits. Nailgun and Sporebreaker use separate firing cues, stronger kickback, and 18- and 5-round magazines. Empty magazines reload automatically. On keyboard, R also reloads. Reduce Motion lowers screen-flash intensity and weapon movement and suppresses airborne particles and portal camera motion.

The extraction effect plays for 1.65 seconds before the perk prompt. Ammo, reload state, recent slicks and extraction progress persist in optional save fields. Saves from 0.2.2 retain their existing enemies and restore with full magazines. The approved lower-octave recording is converted to Vorbis for the looping Music bus, with streamed playback started on sector entry and restored on resume. The package script checks the soundtrack, splash and combat audio resources.

The included cartridge was regenerated with Godot 4.7.2 and the checked-in OnKlav Web template. Shroom's headless suite passed 1,320 checks, including first-hit survival, ammo and stain restoration, soundtrack start, and extraction timing.
