# Cats vs Dogs tactical AI and reliable drops — November 4, 2026

## Tactical opponent

The earlier search used a nine-millisecond deadline, which could leave a shallow greedy reply on slower runtimes. The reported opening can create two winning ends: blocking after the third Cat is already too late, so defense must happen a move earlier. New isolated 49-bit state search checks immediate wins and blocks without mutating the model, prunes forced replies and unsafe moves, and completes iterative depths up to seven within a fixed4096-node budget. It retains the last complete iteration and always rechecks mandatory tactics independently of the search budget. Both exported-browser runs completed depth6 for the reproduced fork position and chose a preventive reply. It uses only the public board; tournament/peer journals and wins are unchanged.

## Pointer ownership and preview

The top coin and translucent landing coin were a held/hover aiming preview, not an acknowledged drop. Board input now captures release at the board boundary, converts screen coordinates through the actual canvas transform, lets real touch replace an emulated mouse owner, and prevents secondary fingers or synthetic mouse releases from stealing an active finger. Single release clears ownership and preview before emitting exactly one chosen column. Mouse motion without a held press cannot resurrect a floating preview. Cancellation, focus loss, resize, turn lock and suspension clear the held preview without committing a coin. Drag-to-preview remains supported; a release outside the board cancels.

## Validation

7,991 focused checks include both legal pre-move variants of the reported terminal board, preventing the earlier two-ended fork, independent bitboard-versus-original-rules tactical comparisons over seeded legal boards, opening defenses, fixed node bounds and unchanged live boards. Pointer tests cover mouse-before-touch, delayed synthetic release, drag/release, duplicate release, repeated columns, second fingers, cancellation, turn lock and outside release. All103 first-four-drop winning patterns (96 bottom-row permutations and7 vertical openings) were also played against the actual AI, with no first-four human win. Existing Cats tests and the standard all-game build/export passed.

Real Chromium touch tests at360×780 and320×640 verify that one release commits one coin, repeated taps do not need a second click, idle mouse movement leaves no preview, pause cancels a held preview, and the actual production scene's AI prevents the reported fork. Exact results/hashes are in cats-tactics-touch-verification.json and docs/evidence/cats-tactics-touch/.

No new APK in this source update;0.100.62 predates it. Phone input/performance acceptance remains separate. Prior Panda courses/drainage, all game tournaments/replay and Video Edit updates are preserved.
