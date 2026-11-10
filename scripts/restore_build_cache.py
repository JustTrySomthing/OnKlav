#!/usr/bin/env python3
"""Restore the saved, checksummed build environment after a workspace reset."""
import hashlib
import json
from pathlib import Path
import shutil
import sys
import tarfile

root = Path(__file__).resolve().parents[1]
working = '--working' in sys.argv[1:]
if working:
    sys.argv.remove('--working')
if len(sys.argv) != 3:
    sys.exit("Usage: restore_build_cache.py [--working] DIRECTORY_CONTAINING_PARTS EMPTY_ENV_DIRECTORY")
parts = Path(sys.argv[1]).resolve()
destination = Path(sys.argv[2]).resolve()
if destination.exists() and any(destination.iterdir()):
    sys.exit("Choose an empty destination; existing environments are not overwritten.")
manifest = json.loads((root / "docs/build-cache.json").read_text())
archive = destination.parent / "onklav-build-cache.restore.tar.gz"
destination.parent.mkdir(parents=True, exist_ok=True)
try:
    with archive.open("wb") as output:
        for part in manifest["parts"]:
            path = parts / part["name"]
            digest = hashlib.sha256()
            with path.open("rb") as source:
                while block := source.read(1024 * 1024):
                    digest.update(block)
                    output.write(block)
            if digest.hexdigest() != part["sha256"]:
                sys.exit(f"Cache checksum mismatch: {path.name}")
    destination.mkdir(parents=True, exist_ok=True)
    with tarfile.open(archive) as bundle:
        def restore_filter(member, target):
            # Immutable-workspace transform metadata is not portable across restored runs.
            if "/gradle/caches/" in "/" + member.name and "/gradle/caches/modules-2" not in "/" + member.name:
                return None
            return tarfile.data_filter(member, target)
        bundle.extractall(destination, filter=restore_filter)
finally:
    archive.unlink(missing_ok=True)
native = destination / "host-native/libonklav_core.so"
if hashlib.sha256(native.read_bytes()).hexdigest() != manifest["host_native_sha256"]:
    sys.exit("Host JNI checksum mismatch")
target = root / "core/target/debug"
target.mkdir(parents=True, exist_ok=True)
provenance_path = root / "docs/host-native-provenance.json"
if not provenance_path.is_file():
    provenance_path = root / "docs/native-provenance.json"
if working:
    from working_native import verified_library
    try:
        native = verified_library(root)
    except (ValueError, OSError, KeyError) as error:
        sys.exit(str(error))
elif provenance_path.is_file():
    provenance = json.loads(provenance_path.read_text())
    fallback = root / "core/host-libs/linux-x86_64/libonklav_core.so"
    if hashlib.sha256(native.read_bytes()).hexdigest() != provenance["host_library_sha256"]:
        native = fallback
    if not native.is_file() or hashlib.sha256(native.read_bytes()).hexdigest() != provenance["host_library_sha256"]:
        sys.exit("Rebuild current host JNI with scripts/build_native.py; cached native code is from older source.")
shutil.copy2(native, target / native.name)
m2 = Path.home() / ".m2"
if not m2.exists():
    m2.symlink_to(destination / "m2", target_is_directory=True)
else:
    shutil.copytree(destination / "m2", m2, dirs_exist_ok=True)
(root / "local.properties").write_text(f"sdk.dir={destination / 'sdk'}\n")
# Quote paths for a shell without allowing command substitution.
def quote(value):
    return "'" + str(value).replace("'", "'\"'\"'") + "'"
(destination / "env.sh").write_text(
    f"export JAVA_HOME={quote(destination / 'jdk')}\n"
    f"export ANDROID_HOME={quote(destination / 'sdk')}\n"
    f"export GRADLE_USER_HOME={quote(destination / 'gradle')}\n"
    'export PATH="$JAVA_HOME/bin:$PATH"\n'
)
check = "scripts/check_android.py --working --tests-only" if working else "scripts/check_android.py"
print(f"Restored. Source {destination / 'env.sh'}, then run python3 {check}")
