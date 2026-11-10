# Naval Battle combat polish — 3 November 2026

Included in APK0.100.50/code159, the owner-requested quick ARM64 update. See RELEASE-0.100.50.md. Earlier source-only/no-APK status is historical.

Restores the gold ammunition arc from the inspected Inner Circle GameBoard reference, with a longer visible tracer and synchronized original fire/hit/miss/sink PCM cues. Impact has separate explosion/fire/smoke/shockwave and water-splash effects. Ships blink three times on sinking and then remain red wreck silhouettes; hit X marks disappear beneath confirmed wreck geometry.

Enemy attacks automatically switch to Your fleet before launch and hold there through impact and the centered report. The view returns to Enemy waters when the user can shoot. Solo and SMS friend games share the same ordered presentation. Reports include You missed, Direct hit, You sank my ship!, Your ship was hit and Your ship was destroyed, with large centered title, coordinates and outcome colors. Final victory/defeat has a centered report. Motion reduction removes the shell travel and blinking while keeping static feedback.

The new fleet HUD has separate player/enemy status cards, ships afloat, hull health bars and turn/attack status. A compact HUD leaves more board space on short displays. Carrier, battleship, cruiser, submarine and destroyer have different cell-aligned top-down hull/deck/turret/aircraft details. The deployment dock has no boxes or profile thumbnails. Each placed ship disappears from it; remaining ships redistribute across the dock. Dragged board ships retain their grabbed-cell offset and orientation; tap still rotates. Illegal placement receives centered feedback.

## State and privacy

Model actions commit/checkpoint before visual effects. Animation completion never sends or reapplies a move. A delayed SMS result holds its outgoing shell at the target until a validated result is available. Incoming events queue in order and wait silently while paused. Pause freezes animation and active sound; return/reload retains game state. An AI move begins after the outgoing presentation finishes, and its result commits once before its incoming presentation.

Schema1 now optionally stores separate wreck groups, retaining compatibility with older saves. Old solo wrecks reconstruct only from already-confirmed opponent geometry; old friend saves remain readable without guessing unrevealed ship boundaries. New friend wreck shapes come solely from validated sinking proofs. Enemy fleets remain private; no protocol or SDK API changed. Bounds, hit membership, overlap and ship-length multiplicity are checked on restore.

Original audio was copied byte-for-byte from the owner-provided Inner Circle source version235. Four clips total about103KB. See naval-polish-verification.json for hashes. Shared Master volume/mute and Effects routing remain effective; music stays independent. Shared Godot4.7.2 binaries and standard game toolbar/Help/Audio/Settings controls are unchanged.

## Verification

247 model/turn checks,80 hockey checks and12 shared rules pass in the required shared-game export pipeline. Two phone-sized browser runs (320×560 and432×740) cover real touch dragging and dock removal, shell flight, miss/explosion/sinking feedback, automatic own-fleet view, original audio-player cues, pause/freeze and saved return. Rendered deployment, shell, impact, red-wreck and report screens were inspected. A two-isolated-player simulation covers nine alternating SMS turns, a confirmed sunk carrier, paused incoming delivery and duplicate-journal protection. These use simulated native turn delivery, not a physical carrier or speaker acceptance test.

Evidence is under evidence/naval-polish and games-provenance.json. No APK was requested in this increment.
