# Panda solo reboot — November 4, 2026

This supersedes the paired build/launch campaign and pt3 friend-play instructions for Panda. Other games keep their own play modes.

## Owner's requirements

Single player only. Remove both the user's and the enemy's building phases. Current rule: the user completes their shot bank before the rival (see PANDA-VOLLEY-PACING.md). Angry Birds is the quality/fun reference, with our own identity and twists rather than copied characters or assets. Birds must be rare, occasional visitors entirely within the top quarter of the playable arena. The owner authorizes a complete makeover of presentation, mechanics and gameplay, prioritizing a spectacular, polished and fun game over preserving the old design.

Original request:

> Ok, I want to make the panda game single player only and want to get rid of the user and the enemy building parts. Just have the user take their shots and the enemy take their shots. I mentioned that the game was inspired by angy birds but really falls short of that games quality and level of fun and polish. I also mentioned that the birds were supposed to be rare and only occasionally, and needed to be in the top 1/4 of the playable screen area. And I asked you for a no holds bar d, no sacred cows, do whatever you must makeover, reboot, whater where you make the game look and play amazing and fun. I want a great and spectacular game as good as the original angry bird was, but with our own unique twists. It has to look great have polished gameplay and be fun, fun, fun.

## Implementation

Recovered the newer isolated Panda work that was absent from the current saved checkpoint, integrated it, and refined it. Runtime now uses panda/duel.gd, battle_arena.gd, battle_hud.gd, duel_physics.gd and main.gd. The old model/solver remain for validated old-save migration and legacy regression checks, not new build phases or new multiplayer games.

- Direct solo start; native catalog, invitations and message game shortcuts exclude Panda from friend play. Historical wire syntax remains readable, but retired Panda invitation tokens no longer resolve to a playable friend title. Original order migration still puts Panda first only once.
- Twelve distinct prebuilt course silhouettes in three environments; separate identical targets maintain each side's damage. Complete player volley, with a readable collapse/score report per shot, then the rival volley; no building or aiming countdown. Three pandas per side, with a once-only extra-panda reward on selected late courses.
- Wood, springy bamboo, fragile glass and heavy stone have different masses, friction, bounce, damage and cracks. Knocked-over and shattered pieces score once; chain bonuses, marked fuse crates and finale fuse-to-fuse explosions reward support targeting.
- Original cannon/panda identity, visible pull anchor/full-power room, trajectory, recoil, reserve loading, motion interpolation, expressive blocks, material shards, impact hold, score animation, confetti/stars and world-specific ambient detail. Compact landscape results reserve room for Continue.
- Original generated biome music plus launch, whizz, impact, glass, tumble, combo, bird and miss cues use canonical music/effects buses. Audio generator is retained in scripts/generate_panda_duel_audio.py.
- Rare model-owned birds have a 12% eligible-turn chance, a four-player-turn cooldown and a single short flyby. Full silhouette stays in the top quarter; never continuously visible and never required to finish.
- Fixed-step bounded solver uses a sweep broad phase. Saves retain both damaged boards, exact in-flight bodies, rewards, bird scheduling and pending result hold. Legacy in-flight solo saves import without discarding their projectile. Separate best score/course stars use the shared profile storage.

## Validation

Final results are recorded in docs/evidence/game-sdk/panda-duel.log, docs/games-provenance.json and docs/evidence/panda-solo-reboot/. Browser testing uses the actual exported game with an injected test-only state probe and mocked native storage bridge. Phone/carrier/performance acceptance remains separate. No new APK was requested or assembled in this change.

Final verification: 48,422 solo-duel checks passed (including exact save/physics continuation, once-only fuse chaining, all twelve courses and rare-bird bounds), 2,102 legacy Panda checks passed, and the shared game suite/export passed. Exported content hashes match the actual current source and unchanged shared engine. All 12 courses passed the no-impact stability probe. Android focused suites passed 21 tests (FriendGames10, GameEntryFlow8, FriendGameUi3) plus shared host checks. Real exported-game Chromium touch/turn/audio/pause/save/resume/Continue checks passed at360×780,320×640 and640×360; final result layouts were visually inspected. These checks do not establish handset frame rate or subjective Angry Birds-level quality. No physical handset test or new APK build occurred.
