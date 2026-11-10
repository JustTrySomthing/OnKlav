Current rules/replay update: see GAME-REPLAY-AND-TOURNAMENT-REPAIRS.md. It supersedes the historical scoring, match-length and finished-game entry behavior below.

# Included in APK0.100.51 / code160

The owner-requested quick ARM64 APK now includes this update. See RELEASE-0.100.51.md and apk51-verification.json. Source-only/no-new-APK statements below describe the earlier implementation checkpoint.

# Cats vs Dogs polish — 3 November 2026

Source update after APK0.100.50/code159. No new APK has been built for this increment;50 predates it.

Solo and SMS friend games now enter the same responsive Cats vs Dogs scene. The original glossy orange cat and blue dog artwork replaces the letter grids. The board has recessed holes, a beveled frame, soft paw details and clear column targeting. Press-and-slide previews the destination column and ghost landing spot; release commits one move. Drops accelerate with gravity, bounce gently on landing, emit small paw particles and play separate swish/landing cues with team-specific pitches. Full columns give a centered report without consuming a turn.

The portrait scoreboard identifies You/Computer or You/Friend and highlights the active side. Four pips and a numeric best-chain indicator track actual connected progress, with piece counts on larger layouts. Three-connected feedback, a growing gold winning line, highlighted winning tokens, bounded confetti and centered win/draw reports make outcomes clear. The New game control uses the reserved footer area so the board does not jump when a game finishes. Orange/blue colors follow the actual team, including a friend host choosing Dogs.

Single-player Dogs now take immediate wins, block immediate losses and search the public board with center preference and bounded iterative deepening. Search checks a9ms budget between nodes and has a600-node ceiling; it does not use threads or hidden information. The existing validated F0–F6 actor journal is shared with friend play. Model state/checkpoints commit before animation; presentation never applies or sends another move. Paused incoming moves queue silently and animate after resume. Duplicate native history cannot replay pieces, sounds or celebrations.

## SDK and save compatibility

The scene attaches once through canonical OnKlavGame and uses the existing GameChrome, turn channel, Effects bus and shared preferences. No extra toolbar, selector, pause/exit handler or replacement dialog was added. Help, Sound, Settings, direct Pause/Play, X with green Resume/red Quit, return/save and New/Resume retain the SDK flow. New returns to the native game-specific catalog choice. Reduced motion replaces travel/bounce with a short target fade and removes trails/confetti.

Current saves retain a validated base board plus replayable journal. Older solo boards are adopted only after integer/range/gravity/count/winner/last-move checks; a pending computer turn resumes once. JSON numbers are normalized before replay, keeping piece counts and empty-cell checks correct. Existing friend tapes and native sequence counters remain readable, with side/match identity retained. Invalid saves stay with the shared recovery UI until explicit discard. No private Inner Circle game data is imported.

Artwork and small original project sound cues were copied from the supplied Inner Circle source235, preserving artwork provenance. Five sound clips supply swish, landing and win/lose/draw outcomes. Music and effects remain independent; Master mute/volume applies to both. Textures/audio load locally and use the same shared Godot4.7.2 binaries. No protocol, host permission or SDK API change was required.

## Validation

The shared-game export pipeline tests Cats vs Dogs, Naval Battle, Air Hockey and the shared rules before certifying the bundled content. Exact counts and hashes are in cats-dogs-polish-verification.json and games-provenance.json.

Browser runs at320×560 and432×740 verify actual touch slide/preview/drop, nonzero WebAudio output and muted silence, animation/audio pause, AI response, full-column rejection, result geometry, fixed board allocation, saved return without replay, original solo adoption, and shared Help/Quit/New/Resume dialogs. Deployment/play/victory screens were visually inspected. Two isolated friend contexts exchange seven turns with a Dogs host/Cats guest, paused arrival, duplicate delivery, legacy tape import and saved return. Delivery is simulated; physical Samsung/carrier/speaker acceptance and public-store qualification remain separate.

Evidence and harnesses are under evidence/cats-dogs-polish. No APK was requested for this source increment.
