# Panda varied courses and complete collapse — November 4, 2026

## Falling blocks

Awarded blocks were removed from the busy test, and clearing every target ended the shot immediately. Their last visible positions were then frozen until another shot created a world. Scored debris now continues its ballistic fall, counts as busy while visible, and must pass the same 1240-world-unit exit used by the renderer before the report/next shot. Clearing all targets cannot bypass the drain. The collision safety window for new flights is ten seconds; even its deadline cannot strand visible debris. At that deadline, persistently moving unstable pieces are awarded and drained rather than left suspended. Saves retain their bodies/velocities and precise continuation during that drain. Gameplay still uses bounded 120 Hz catch-up and the three-shot player volley.

## Twelve progressive courses

The introductory course remains the familiar 15-piece layout. Later courses grow to 18,20,22,24,26,28,30,32,34,36 and40 pieces. Recipes include a reinforced longhouse, two-height terraces, a supported arch bridge with an upper gatehouse, stepped islands, twin keeps, an uneven skyline, a tall fortress, bridge buttresses, four pavilions, a loaded gatehouse and a four-height temple. Heights and silhouettes vary; courses use one to four platforms, rather than always three stacks on one shelf. New bays have three short redundant supports and supported roof cargo. Material damage thresholds increase after the introductory course, particularly for stone. Fuse crates remain limited strategic opportunities, and identical independent targets keep the player/rival comparison fair.

Touching one island no longer wakes every structure. New revision3 flights use a validated per-body awake mask and connected resting groups; impact wakes the contacted structure, falling bodies can wake another, and blast impulses wake their affected groups. Awake state is captured after all damage/blast events, fixing a save-continuation edge case caught during regression. New solver saves are bounded to45 bodies (40 pieces +4 platforms +projectile). The untouched legacy solver keeps its24-body bound and imported old flights keep their original solver/rate. Saved old courses keep their stored geometry until the next course. The renderer retains bounded particles/style caches and shared engine JS/WASM is unchanged.

The top You/Rival labels increase from10 to13 logical pixels. All prior Video Edit, other game, instant replay, tournament, scoring, charge and audio changes are preserved.

## Verification

Focused course checks cover increasing piece counts, world bounds, equal targets, untouched-course stability, independent island activation, exact mid-fall save continuation, no scored debris above the exit on report, and deadline drainage. The full duel suite covers all courses, tactical shots, deterministic damage/physics continuation, once-only blast chains, legacy import and rare birds. Standard export and three-screen actual-game touch/pause/save/recovery/volley checks are retained, with internal visual inspection of courses2,4,8 and12. Exact results and hashes are in panda-courses-verification.json and docs/evidence/panda-courses/.

No new APK was requested or built. APK0.100.61 predates this source update. No physical-phone frame-rate or subjective difficulty qualification is claimed.
