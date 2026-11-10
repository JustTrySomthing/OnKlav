Current rules/replay update: see GAME-REPLAY-AND-TOURNAMENT-REPAIRS.md. It supersedes the historical scoring, match-length and finished-game entry behavior below.

> Current launch and quit contract: [GAME-ENTRY-FLOW.md](GAME-ENTRY-FLOW.md).

# Per-game cartridge saves — SDK0.6.1 / host0.100.57 code166

Every game/publisher has an independent private cartridge. Any number of games may have retained progress; only one renderer runs at a time. Opening a title must resolve that title only. Never route it to a different paused game or require another title to finish. The old global game-session marker is a backwards-readable hint, not a save or launch lock.

## Ownership and storage

The host binds the cartridge to the installed game ID and pinned publisher origin, or a stable included-game ID. Content cannot supply an owner, filesystem path or another game's slot. Updates retain this identity. Uninstalling a game package retains saves. Clearing OnKlav data removes them; this is local storage, not cloud backup.

One automatic resume slot belongs to solo play. Friend sessions use separate slots bound to their match IDs. Named data slots (`data.profile`, for example) hold unlocks, inventory, high scores or user-managed save slots independently of the automatic session. A slot holds at most2MiB of JSON state; each cartridge permits32 slots. Deleting a named slot or explicitly removing a completed friend history releases that slot. Do not silently drop a live match to make room.

On Android, the host writes a versioned envelope containing owner, slot, payload version, resumability, timestamp, revision, integrity checksum and game-defined JSON. File and process locks serialize access; AtomicFile protects replacement; a previous valid checkpoint is retained. A fresh writer lease invalidates older renderers for that same game/publisher. There are no credentials, device permissions, arbitrary-file APIs or general Android object bridge. The bounded console protocol only accesses this renderer's own storage. Top-level navigation is blocked; reopen through OnKlav to reload a title.

## Standard interaction

Games opens to the list only. After explicit Play, the host presents the selected title's existing Start dialog or, if it has a resumable session, Resume / New Game / Cancel. New opens the mode/start dialog; no renderer or loader exists until Start/Resume is chosen. Cancel retains the previous save. A new session replaces the old slot only when initialization and saving succeed. Explicit Quit confirms loss and clears only the current session, preserving permanent data. A cleared session leaves a host tombstone so old IndexedDB data cannot resurrect it.

Return pauses and checkpoints, waits for a host commit acknowledgement, then destroys the renderer and returns to Games. Pending timers/music cannot keep running behind another title. On foregrounding an existing renderer, stay paused with its Play icon/notice; never automatically show New/Resume. The controller also checkpoints every2seconds during play and on application pause/focus loss; developers must checkpoint meaningful actions themselves. Abrupt termination can lose work since the last committed checkpoint; do not promise power-loss-proof saving.

A failed save freezes play and offers retry/stay/explicit leave without saving. Previous committed data remains intact. A failed read or incompatible game restore shows recovery and retains originals; it never becomes a silent fresh game. A valid previous checkpoint can be tried explicitly through the shared recovery dialog. Restore validates game-specific state and performs supported format migrations before returning true.

## Developer contract

```gdscript
OnKlavGame.attach_session(self, snapshot, restore, start_new, HELP,
    "user://legacy-game-save.json", {}, {
        "format_version": 1,
        "is_resumable": func(_data: Dictionary): return not game_finished
    })
```

`snapshot() -> Dictionary` contains JSON data only: inventory, quests, level, simulation state, pending decisions, RNG state as needed. `restore(data) -> bool` validates all types, bounds, identities and rules before mutation. `start_new()` initializes the entire session. The payload format version is stored in the host envelope; the game should also keep its own schema/version in its data so its restore callback can migrate it. `_onklav_*` keys are reserved.

`checkpoint() -> bool` accepts/queues a snapshot; it does not synchronously guarantee Android durability. `await flush_saves() -> bool` waits for all submitted writes and their acknowledgements. The standard Return/New/Quit flow handles this automatically. Do not emit a return/exit message before acknowledgement.

```gdscript
var ok: bool = await OnKlavGame.save_data("profile", {
    "schema":1, "inventory":["key"], "unlocks":[1,2]
})
var saved: Dictionary = await OnKlavGame.load_data("profile")
if saved.get("ok",false) and saved.get("exists",false):
    # Validate before applying.
    print(saved.state)
var removed: bool = await OnKlavGame.delete_data("profile")
```

Data-slot names use lowercase ASCII identifiers, starting with a letter, at most64characters. Session and data slots are deliberately separate. Saving permanent progress is the developer's responsibility; New/Quit does not clear it.

The game declares a meaningful resume boundary: exact simulation, turn, or checkpoint. OnKlav cannot infer what an inventory, physics state or quest flag means. Stable games must save all state needed for that boundary, not serialize live nodes/resources or executable scripts.

## Compatibility and friend games

Included games all use this contract, including Air Hockey's dedicated controller. On first opening, valid original game-specific checkpoints in Godot user:// are imported through each game's existing validator. Native host storage is then authoritative. Original browser files are retained. Older signed third-party games remain installable with their legacy save behavior; authors must publish an SDK upgrade under the same ID/key to gain acknowledged host saves. Do not rewrite signed packages behind the developer's back.

Friend matches retain their opponent, match ID, native event journal, delivery jobs and game/channel cursors. Different titles may have independent active invitations/matches. A second live match for the same title remains blocked. Switching titles never sends Quit. Confirmed New/Quit explicitly ends the current peer match. Resuming rejects mismatched match IDs; paused arrivals are journaled once without advancing presentation. The wire format is unchanged.

## Verification scope

Use model tests, session-controller tests, native store/ownership tests, catalog route checks, and real web-runtime flows with delayed/failing host acknowledgements. Physical Android WebView process death, storage pressure, OEM behavior and handset performance remain separate acceptance work. Desktop/standalone web previews use bounded temporary-file/flush/rename checkpoints; browser filesystem synchronization is asynchronous and does not provide the Android host acknowledgement guarantee.
