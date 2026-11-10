# Shared game systems

SDK 0.13 uses the existing engine and Android package and bridge contracts. The signed campaign and turn examples run on the current OnKlav APK.

## Progress, scoring and save slots

Start with `gamekit.py new ... --template campaign`. The starter keeps its current level in the session save and stores permanent progress in the named `campaign_profile` slot. Use different names for different profiles. Named slots are scoped to the installed game and publisher.

`player_progress.gd` records each completed level's best score and medal, checks prerequisites, and derives the permanent reward from completed level IDs. Keep those IDs stable across releases. For a spendable currency, store the wallet and the set of granted completion IDs together in one named snapshot. Serialize writes, await acknowledgement and keep the last confirmed state when a write fails. After an ambiguous acknowledgement, reload before accepting another transaction. Never sum every replay of a level into the permanent reward.

## Gesture controls

Create a `touch_gesture.gd` node under the interactive Control and connect that Control's `gui_input` to `feed`. Listen for `tapped`, `held`, `dragged`, `swiped` and `cancelled`. It owns one pointer and ignores extra fingers until release. Distances are in the Control's local coordinates. Set the drag and swipe thresholds in those units and the hold threshold in seconds. A swipe can follow drag updates. On cancellation discard unfinished drag UI. The campaign uses the helper for board taps and offers ordinary buttons for the same actions.

## Authored level packs

`level_pack.gd` reads a bounded local JSON resource with schema 1 and a list of stable IDs with data payloads. Pass a genre-specific validation callback. It returns an empty dictionary if any row fails. The campaign's `levels.json` is exported automatically. The SDK also includes data examples in `examples/level-packs/` for waves and story choices.

For waves, validate integer counts, HP, route length and adjacent coordinates before creating units. For a story pack, validate bounded text and choices, then check all destination IDs against the loaded pack before showing any choice. Add your authored file to the Web export preset's include filter. If a project updates an authored pack, keep IDs stable and migrate saves before removing a level. The wave and story files are data examples. Each game supplies the simulation and presentation.

## SMS turn recovery

Use `gamekit.py new ... --template turn --play-mode turn_sms`. The signed turn starter demonstrates solo play and SMS-based friend play. It checkpoints the model and channel cursors together. The helper skips duplicate journal entries, waits at an out-of-order gap and validates cursors before restoring. The starter blocks an invalid board update across reopen and exposes the native retry action for pending delivery. Retry asks the host to retry the existing job. It does not submit another move.

Persist game state and cursors in the same snapshot, advance the model only on journal events, and leave native delivery sequencing to the host. Multiplayer is for SMS-based turns only.

## Run the shared checks

Generate the campaign and run:

```sh
godot --headless --path MyCampaign --script /absolute/path/to/tests/shared_systems_tests.gd
```

The checks exercise a named save, replay and reward deduplication, local pack validation, gesture cancellation, and simulated duplicate, out-of-order and reopened turn journals.
