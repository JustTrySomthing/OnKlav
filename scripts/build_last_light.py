#!/usr/bin/env python3
"""Export the APK-owned Last Light Express game against the shared engine."""
import os
from pathlib import Path
import sys
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SDK = ROOT / "developer/games"
sys.path.insert(0, str(SDK))
import gamekit


def build():
    project = ROOT / "games/last-light"
    output = ROOT / "android/games/src/main/assets/included-games/last-light.zip"
    template = project / ".onklav-web-template.zip"
    template_created = not template.exists()
    if template_created:
        template.write_bytes(gamekit.template_bytes())
    try:
        with tempfile.TemporaryDirectory(prefix="onklav-last-light-") as temp:
            web = Path(temp) / "web"
            gamekit.export_web(project, web, os.environ.get("GODOT_BIN", "godot"))
            shared = gamekit.contract()["files_sha256"]
            with tempfile.TemporaryDirectory(dir=output.parent, prefix=".last-light-") as staging:
                package = Path(staging) / output.name
                with zipfile.ZipFile(package, "w", zipfile.ZIP_DEFLATED) as bundle:
                    bundle.write(project / "manifest.json", "manifest.json")
                    bundle.write(project / "web-audio.js", "web/web-audio.js")
                    for wav in sorted((project / "audio").glob("*.wav")):
                        bundle.write(wav, "web/" + wav.name)
                    for file in sorted(web.iterdir()):
                        if file.is_file() and file.name not in shared:
                            bundle.write(file, "web/" + file.name)
                package.replace(output)
    finally:
        if template_created:
            template.unlink(missing_ok=True)
    print(output)


if __name__ == "__main__":
    build()
