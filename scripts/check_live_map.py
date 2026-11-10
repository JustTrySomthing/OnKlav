"""Opt-in single OSM tile check through the complete signed app/JNI/HTTP/cache path.

Requires the restored Android/JDK environment and source-matched working JNI.
No APK, installed shipping package, credentials, geocoding or routing are created.
The disposable signing key and package are removed when the check ends.
"""
import os
from pathlib import Path
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'developer'))
import onklav


def main():
    with tempfile.TemporaryDirectory(prefix='onklav-live-map-') as temporary:
        folder = Path(temporary)
        key = folder / 'disposable-author.key'
        onklav.packer('keygen', key)
        try:
            built = onklav.build_project(ROOT / 'developer/examples/reference-map', key, folder / 'reference-map.onklav-a')
        finally:
            key.unlink(missing_ok=True)
        env = os.environ.copy()
        env['ONKLAV_LIVE_MAP_PACKAGE'] = built['package']
        subprocess.run([sys.executable, str(ROOT / 'scripts/check_android.py'), '--working', '--tests-only', '--timeout', '120',
                        '--test', 'app.onklav.home.SdkLiveMapProviderTest'], cwd=ROOT, env=env, check=True)


if __name__ == '__main__':
    main()
