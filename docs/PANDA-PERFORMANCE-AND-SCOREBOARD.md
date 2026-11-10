# Panda performance and scoreboard refinement — 2026-11-04

Responds to continued slow-motion shots/blocks on the owner's phone and the requested presentation changes.

New flights use 120 simulation updates per second instead of 240, with the six-pass contact solver retained, typed hot-loop bodies, bounded monotonic elapsed-time pacing and suspension resets. Tick counters remain in 1/240-second units for timings. Existing unmarked 240 Hz flights and legacy flights retain their original step mode until the next shot. Save records include validated physics revision and tower-awake state, preserving exact continuation.

Unstruck towers now remain asleep until a meaningful projectile collision. This fixes collapse/scoring without impact and avoids simulating the whole resting tower during the initial launch arc. The twelve-course no-impact check failed on the first 120 Hz attempt; the impact-activation repair passes all twelve. The earlier failed log is retained as evidence. Structural collapse/damage and chain rewards continue after impact.

All visible point values use one tenth of the previous scale: basic piece 10, blast 20, golden duck 35. Original internal score precision remains intact for compatible saves, profile bests, and winner comparisons; display rounds to whole points. Current and restored games use the same display conversion. No old score is divided repeatedly on load.

Score cards show only panda, You/Rival and score; rival active outline matches its blue helmet. Remove the environment/course caption below them. Round text is black, with a larger number. Charge begins at 0%, has no false filled segment at zero, and resets on cancel/turn change; a valid released shot still has the existing minimum physical launch power. Bottom shot text is larger and white with a dark outline/shadow. Results retain shadowed text, add star shadows, replace the statistics under stars with the round winner and number, and use a centered 176-unit Continue button.

Validation and source hashes are in panda-performance-scoreboard-verification.json, game build provenance and docs/evidence/panda-scoreboard/. Software/native/browser measurements do not establish Galaxy S21+ frame rate or subjective quality. No new APK is included in this source refinement; APK 0.100.60 predates it. Video Edit and all previous merged changes remain preserved.
