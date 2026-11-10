# Puzzle, runner and idle starters

These three portrait starters use the same signed package format, controls and runtime as the existing templates.

| Template | Playable example | Reuse |
| --- | --- | --- |
| `puzzle` | Lights Out with a solvable 4×4 board and a result screen | Grid button input, reversible neighbor rules, bounded board validation and replay |
| `runner` | Three-lane obstacle dodging with progressive speed | Fixed-step movement, phone-sized lane buttons and exact run snapshots |
| `idle` | Coin production and upgrades with capped offline credit | Elapsed-time arithmetic, rollback handling, validated currency and upgrade state |

```sh
python3 gamekit.py new MyPuzzle --id com.example.mypuzzle --name "My Puzzle" --template puzzle
python3 gamekit.py check MyPuzzle
python3 gamekit.py build MyPuzzle --key publisher.key --output MyPuzzle.onklav-g
```

Use your own reverse-domain game ID and generate and back up your own publisher key. The signed starter examples are for testing. Use the shared result and return flow and preserve your game ID, publisher and save schema as you extend a starter.

The puzzle and runner preserve their results screens on resume. Permanent achievements can use named data slots. Credit a unique completion ID with the reward so replay or repeated load cannot grant it twice. The idle starter uses the device clock and credits at most eight hours on a saved-session resume. For economies requiring trusted time, a local clock is insufficient. Store snapshots as bounded data and validate before applying them.

For tower defense or tactics, combine the existing bounded grid pathfinding with your own map, wave and unit rules. For racing and sports, use the 2D or 3D scenes and author the specific camera, controls and physics. See [RELEASE-READINESS.md](RELEASE-READINESS.md) for the host feature boundaries.

## More playable starters

| Template | Playable example | Reuse |
| --- | --- | --- |
| `racing` | Two-lap 2D time race with a pace rival | Touch steering, off-line slowdown, track checkpoints, and a race clock |
| `defense` | Five waves along a fixed route with five tower pads | Placement, spending, targeting by route segment, waves, and base health |
| `rhythm` | Twenty generated beats with visual pulses and audio cues | Scoring windows, missed beats, and manual timing offset |
| `match3` | Adjacent swaps on a six-by-six board | Match validation, bounded cascades, gravity, and deterministic refills |
| `merge` | Four-direction tile slides to reach 128 | Single merges per move, deterministic spawning, and stuck-board detection |
| `sports` | Drag-to-putt with friction and rebounds | Touch aim and power, fixed-step motion, scoring, and resets |
| `builder` | Build a town with farms, workshops and homes | Grid placement, costs, resource production, and persistent plots |

Each example uses the existing pause, save, quit and replay contract. The race rival follows a fixed pace. Defense illustrates a single route and sequential enemies. The rhythm chart is generated and timing is frame-driven. Measure actual audio and input latency before shipping a timing-sensitive game. These foundations can be extended with authored tracks, branching routes, teams, music charts or campaign progression.

After generating a project, run `gamekit.py check` to verify import, common lifecycle and scene boot. The seven starter-specific checks also exercise gameplay and save validation:

```sh
godot --headless --path MyRace --script /absolute/path/to/tests/genre_starter_tests.gd -- racing
```

## Classic and casual components

| Template | Playable example | Reusable pieces |
| --- | --- | --- |
| `breakout` | Paddle, bricks and three balls | Pull-to-aim math, normal-based rebounds, fixed-step paddle and brick collisions |
| `wordquiz` | Three local questions with a letter grid | Bounded JSON packs, normalized answer checks, letter and space input |
| `cards` | A nine-card solitaire foundation puzzle | Typed drag payloads, destination validation, tap fallback, legal rank sequencing |
| `adventure` | Investigate a room and choose a path through dialogue | Tappable hotspots, inventory IDs, item-gated choices, validated story snapshots |
| `tactics` | A solo grid encounter against a computer foe | Orthogonal tile movement, wall checks, targeting, turn order, encounter outcomes |

Copy `classic_rules.gd` for aim, rebounds, grid movement or question packs. The brick demo uses simple fixed-step collisions. For fast pinball or narrow obstacles, use Godot collision shapes with continuous collision detection or a swept collision query. Bubble shooters also need your own snap-to-grid and connected-group rules.

Edit `questions.json` for a new word or trivia pack. The starter exports that local file and uses English letters and spaces. For other languages, adapt the key set and answer normalization to match the authored pack. `card_drag.gd` handles the gesture and payload. The destination decides if a move is legal and tap-to-place provides a fallback. `story_state.gd` stores stable scene and item IDs. For hidden-object scenes, overlay hotspot buttons on your artwork. For larger tactical games, build on the existing bounded grid pathfinding and add unit-specific rules and encounter maps.

These examples are solo. For asynchronous friend play, use the existing `turn` template and its SMS turn channel. Keep turns compact and validate them against the saved state. The SDK profile excludes pass-and-play, shared-device multiplayer and anything that needs low latency.
