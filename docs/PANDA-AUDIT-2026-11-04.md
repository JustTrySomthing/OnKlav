# Panda reboot audit — November 4, 2026

Scope: actual solo game model, physics, touch/lifecycle, save recovery, audio, native catalog/launch and retained friend-match routing. No APK requested.

## Findings and repairs

1. **Retired friend sessions could intercept solo launch.** Panda was absent from new invitations but existing `active`/`resume` matches could still redirect catalog entry or an explicit `friend_match` activity intent into a friend save slot. Solo-only games now ignore those sessions during entry, retain their historical records, and reject accepting/retrying/sending retired Panda turns. Tests cover retained history, solo selection, and direct activity intents. Other games retain friend play.
2. **Structural collisions could not cause fracture damage.** The inherited solver reported impact strength only for collisions involving a circle. The new Panda solver now reports rectangular structural impacts too, enabling falling pieces to crack/shatter other pieces. The legacy solver is unchanged for old in-flight imports.
3. **Failed star/profile saves could become unrecoverable.** A course was marked written before the asynchronous result, so the shared Retry Save path could save the session but leave a failed profile slot pending forever. Profile writes now use immutable copies, one in-flight write, monotonic star/best-score merging and an acknowledged dirty flag. Failure remains pending; a manual suspend/Retry Save retries even while paused. Failed profile reads can also retry on suspension. Browser checks inject a real profile-write error and verify recovery and retained stars.
4. **Interrupted gestures could fire incorrectly.** Arena resize now cancels the pull without consuming a shot. A canceled secondary finger no longer cancels the primary finger's gesture. Owner cancellation still releases input.
5. **Malformed save states could load into an unshootable turn or incompatible collider layout.** Restore now rejects an exhausted/cleared active aiming board, flight/report with no fired shot, wrong body roles and mismatched block dimensions before changing live state. Valid native and migrated legacy in-flight checkpoints retain exact continuation.
6. **Impact cues suppressed fracture sounds.** A shared cooldown let the impact sound suppress glass/wood cues from the same event. Separate per-cue cooldowns allow the material cues while bounding repeated sounds. Audio suspension uses named lifecycle callbacks.
7. **Final celebration used the last round's outcome.** The finished campaign now uses total campaign scores for the winner's celebration, consistently with its title and summary.

## Verification

Focused new checks live in `games/engine-checks/panda/audit_tests.gd`; model/regression/export evidence is in `docs/evidence/game-sdk/`. Browser checks exercise the actual exported game and a test-only mocked native storage bridge at narrow portrait and landscape sizes, including real touch, resize cancellation, sound-menu pause, player/rival turns, session restore and failed-profile retry. Native tests cover catalog/invitation/session boundaries.

The first Android attempts were blocked by shared cache locks, copied generated metadata and unreadable temporary compiler outputs. Final native validation uses a clean source checkout with the exact modified Kotlin/tests and an isolated dependency cache. These infrastructure failures are not counted as passing tests.

Real Samsung/Android 15 touch, sound, frame-rate and visual acceptance remain untested. No APK was assembled.

Final verification: 15 focused audit checks, 48,422 solo-duel checks, 2,102 legacy Panda checks, the shared game suite and production game export all passed. The export hashes match current source. Browser tests passed at360×780,320×640 and640×360, including real touch, resize cancellation, pause/resume, both damaged boards and injected profile-write failure/retry. Android focused suites passed 23 tests (FriendGames11, GameEntryFlow9, FriendGameUi3), with shared host checks and the compiled/runtime-class comparison passing. Every Android Kotlin source used by the review matches the clean tested checkout. Final evidence is in docs/evidence/panda-audit/. No physical-device or APK validation is implied.
