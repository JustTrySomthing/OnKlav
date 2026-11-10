# Engine compatibility and package updates

OnKlav build 173 selects the engine for an external game by the signed engine version and the exact engine file inventory. The primary bundle still serves built-in games. External game ID, publisher fingerprint, origin and save owner are independent of engine choice.

The APK ships the same Godot Web runtime as build 172. The compatibility path is implemented and exercised with two synthetic engine descriptors in the Android host test. Shipping and exercising a second real engine bundle is a separate engine upgrade.

## Verify an existing package

```sh
python3 gamekit.py verify GAME.onklav-g --host-code 173
python3 gamekit.py verify GAME.onklav-g --host-code 173 --engine-contract retained/engine.json
python3 gamekit.py verify-engine retained
```

The second command verifies a signed package against a specific retained descriptor. The third hashes the actual files in a bundle. Confirm that the descriptor is part of the target host's assets. A matching descriptor supplied to the tool does not by itself establish that the APK includes it.

Build and export still use the current SDK's engine template. Keep the matching SDK to maintain an older title. The upgrade command refuses a different fingerprint and preserves an existing higher minimum host. Treat engine migrations as deliberate, separately tested game updates. Retain game ID and publisher key and increase version_code for changed packages.

## Maintain host bundles

When the primary engine changes, place the earlier verified engine.json and every file in its inventory under `android/games/src/main/assets/godot-web-compat/<release-id>/`. The ID is a simple ASCII directory name. Retain the earlier descriptor and bytes. Do not repack or re-sign released games to change their engine assignment.

Before releasing the new host, run verify-engine on the retained bundle and inspect the final APK to confirm every retained byte is present. Exercise both real bundles side by side with immutable signed fixtures. Install and resume the older package and verify that its origin and saves survive the host update. An engine name alone is not a compatibility match.

## Reusable saved draw pile

`draw_pile.gd` shuffles up to 256 unique nonnegative integer card IDs and records the actual draw order. Distinct IDs may represent cards of the same rank. Save its snapshot with your game state, and validate against the expected deck before restoring.

```gdscript
const DrawPile = preload("res://draw_pile.gd")
var deck := DrawPile.new()
var cards: Array[int] = [0, 1, 2, 3]

func start_round(seed_value: int) -> void:
    if deck.reset(cards, seed_value):
        var first_card := deck.draw()
        print(first_card)
```

The sample tests serialize and reload the draw pile, confirm the next draw is identical, and reject duplicate cards without altering the active pile. For a game with private hands, never send the full deck or a reconstructible seed in an SMS turn.
