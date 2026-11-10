# Game controls, replay and tournament repairs — November 4, 2026

Owner request: restore Air Hockey dragging; stop Cats vs Dogs allowing simple first-four-drop wins; finish-game Play again/New game must restart immediately; Blackjack is ten hands with tie-breaks; Cats vs Dogs is first to three wins; make Dominos a clear round-win tournament.

## Controls and AI

Air Hockey's emulated mouse press could arrive before the real finger, claim the mouse sentinel, then cause all real drags to be ignored. Real touch can now take over an emulated mouse owner; another finger cannot steal control, and synthetic mouse events cannot replace an active touch. Rink-boundary input survives decorative GUI controls, retains coordinate conversion, pause checks, release and clamping. Native tests cover mouse-first takeover and dragging; real Chromium touch checks cover portrait and landscape with actual paddle displacement.

Cats AI establishes safe root replies before time-bounded iterative deepening, excluding moves that allow an immediate opposing win whenever a safe move exists. Immediate wins/blocks remain independent of the search deadline. Vertical and reported horizontal first-four openings pass reducer checks, and the exported game's real touch opening test blocks the reported pattern. This is a bounded tactical opponent, not a claim of unbeatable play.

## Direct replay

The shared OnKlavGame.replay action restarts the selected mode, checkpoints, waits for successful save acknowledgement and resumes directly. It never invokes native opponent selection, unfinished-save replacement confirmation or fallback instructions. Result buttons in Panda, Cats, Blackjack, Dominos and Naval use direct replay; Hockey keeps its direct restart. Explicit replacement of an unfinished save from entry/recovery retains its existing protection.

Friend rounds/rematches use host-issued N moves in the existing ordered X transport, with counters and tapes retained. Cats replays round markers and derived wins; Blackjack deals the next hand or resets a finished tournament; Naval resets both finished fleets when the host's N is acknowledged. Guest controls wait for the host. Both peers must use the updated game runtime; older runtimes did not understand these continuations. Actual carrier/two-phone acceptance remains separate.

## Tournaments

- Blackjack solo: ten hands; each winning hand earns one win, pushes earn none. Most wins after hand ten decides the tournament. Ties continue through tie-break hands until a decisive lead. Solo save wraps the original fully validated card/deck record with round and result history; old solo saves become the current first hand. Friend ten-hand rules now also continue ties. The scoreboards show tournament wins and hand/tie-break number, with actual hand totals still available.
- Cats vs Dogs: first to three board wins. Draws award no win. The scoreboard and footer show progress and target. Next round clears the board immediately; Play again starts a fresh series. The validated original board prefix and full move/round-marker tape reconstruct wins across suspension. Historical unfinished boards remain readable.
- Dominos: first to three round wins. Empty a hand to win; if blocked, lower remaining pip total wins; equal totals draw. One win per completed winning round, no per-tile score. Both /3 WINS cards and first-to-three text are visible. Original pip accounting remains only the validated per-round ledger. Old saves retain the current table/hand; unconvertible historical pip totals are not fabricated into round wins, and the current completed round counts as the first series result.

All prior Panda performance/scoreboard and Video Edit work is preserved. No new APK in this source update; the latest APK 0.100.60 predates it. Verification JSON and logs record exact checks. Headless/browser checks do not establish physical-device, carrier or subjective gameplay acceptance.
