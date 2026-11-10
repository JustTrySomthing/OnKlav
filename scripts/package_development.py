#!/usr/bin/env python3
"""Deliver the only OnKlav APK plus its actual source; never per-app APKs."""
import hashlib
import json
from pathlib import Path
import shutil
import sys
import zipfile

root = Path(__file__).resolve().parents[1]
if len(sys.argv) != 2:
    sys.exit('Usage: package_development.py DESTINATION (per-app delivery is retired)')
destination = Path(sys.argv[1]).resolve()
destination.mkdir(parents=True, exist_ok=True)
report = json.loads((root / 'docs/apk-verification.json').read_text())
assert len(report['apks']) == 1 and report['apks'][0]['name'] == 'host'
apk = root / 'android/app/build/outputs/apk/debug/app-debug.apk'
assert hashlib.sha256(apk.read_bytes()).hexdigest() == report['apks'][0]['sha256']
output = destination / 'OnKlav-Rewrite-Host-Dev.apk'
shutil.copyfile(apk, output)
excluded = {'build', 'target', '.gradle', '.godot', '.kotlin', '__pycache__', '.git'}
source = destination / 'OnKlav-Platform-Source.zip'
count = 0
with zipfile.ZipFile(source, 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
    for path in sorted(root.rglob('*')):
        relative = path.relative_to(root)
        if excluded.intersection(relative.parts) or not path.is_file():
            continue
        if relative.parts[:2] == ('docs', 'evidence') and path.suffix.lower() in {'.png', '.jpg', '.jpeg', '.webp'}:
            continue
        if path.name == 'local.properties' or path.suffix in {'.apk', '.keystore', '.jks', '.pyc'}:
            continue
        assert not path.is_symlink() and path.suffix != '.java'
        archive.write(path, 'OnKlav/' + relative.as_posix())
        count += 1
with zipfile.ZipFile(source) as archive:
    assert archive.testzip() is None
print(json.dumps({'host_apk': str(output), 'source_archive': str(source), 'source_files': count, 'apk_count': 1}))
