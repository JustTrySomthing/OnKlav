#!/usr/bin/env python3
"""Export Shroom as an APK-owned asset using the shared game engine."""
import argparse
import os
import sys
from pathlib import Path
import tempfile
from zipfile import ZIP_DEFLATED, ZipFile, ZipInfo

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "developer/games"))
from gamekit import encoded, export_web, packed_resource_names


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--godot", default=os.environ.get("GODOT_BIN", "godot"))
    args = parser.parse_args()
    output = ROOT / "android/games/src/main/assets/included-games/shroom.zip"
    with tempfile.TemporaryDirectory(dir=ROOT, prefix=".shroom-export-") as tmp:
        web = Path(tmp) / "web"
        manifest = export_web(ROOT / "games/shroom", web, args.godot)
        files = {"web/" + path.name: path.read_bytes() for path in web.iterdir()
                 if path.is_file() and path.name not in manifest["engine_assets"]}
        project = ROOT / "games/shroom"
        files["web/shroom-score.js"] = (project / "shroom-score.js").read_bytes()
        resources = packed_resource_names(files["web/index.pck"])
        required = {
            "assets/below_the_mycelium.ogg",
            "assets/shroom_loader.png.import",
            "assets/nail.wav.import",
            "assets/break.wav.import",
            "assets/hurt.wav.import",
            "assets/reload.wav.import",
            "assets/portal.wav.import",
        }
        if missing := required - resources:
            raise ValueError("Shroom export is missing: " + ", ".join(sorted(missing)))
        if {name for name in resources if "below_the_mycelium.ogg" in name} != {"assets/below_the_mycelium.ogg"}:
            raise ValueError("Shroom export must contain one raw soundtrack")
        files["manifest.json"] = encoded(manifest)
        staged = Path(tmp) / output.name
        with ZipFile(staged, "w") as archive:
            for name, data in sorted(files.items()):
                entry = ZipInfo(name, (2026, 1, 1, 0, 0, 0))
                archive.writestr(entry, data, compress_type=ZIP_DEFLATED)
        staged.replace(output)


if __name__ == "__main__":
    main()
