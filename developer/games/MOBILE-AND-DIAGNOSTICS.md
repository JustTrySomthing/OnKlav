# Mobile layout, save migration and diagnostics

## Update a saved game

The `mobile` template is a runnable example. Load the older-save example to rename `old_docks` to `harbor` and preserve its collected key in the new inventory list. Actual version 1 saves go through the same restore callback.

Keep game identity and publisher key stable. Store a schema version in your data, separate from the signed package version. Pass an isolated source snapshot, target version, and a dictionary of consecutive transformations to `save_migration.gd`. Each step must return the next schema. The final callback validates the whole migrated state. If migration fails, return false from your session restore callback so OnKlav can offer recovery while retaining the original. Do not checkpoint a partially migrated game. A successful migration updates state on the game's normal save path.

## Adapt the touch layout

```sh
python3 gamekit.py new MyGame --id com.example.mobile --name "My Game" --template mobile --orientation sensor
```

Use `mobile_layout.gd` inside the host's content area. It supplies base margins, optional explicit safe-area insets, text scaling, touch target sizing, a two-slot action mapping and a reduced-motion duration helper. Insets are in local units, ordered left, top, right, bottom. Leave them at zero when the host has already inset the content. If you supply your own display insets, convert them to local coordinates first. The helper does not query hardware cutouts.

The sample reflows between one and two columns and scrolls vertically if it runs out of room. Large text, the action swap and reduced motion are saved with the session. Use the motion helper for optional animation only. Keep effects and game timing separate so reducing animation does not change game rules.

## Diagnose a signed game package

```sh
python3 gamekit.py diagnose MyGame.onklav-g --host-code 174 --require-resource res://levels.json
```

The command checks signature, engine contract, minimum host version, package and expanded sizes, and the largest archive member. It reads the resource directory from the standard unencrypted Godot 4.7 Web PCK. Repeat `--require-resource` for authored files that your game loads by name. This checks declared resources rather than inferring every dynamic dependency. Use `--engine-contract` with an engine descriptor from the target APK for a retained bundle.

OnKlav build 174 offers **Copy report** after a game package inspection, installation or launch error. The report includes host build, operation, reason and game ID when available. Copying puts the report on the clipboard so the tester can paste it where they choose.
