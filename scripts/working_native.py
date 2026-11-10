"""Verify the unreleased JNI checkpoint without changing released provenance."""
import hashlib
import json
from pathlib import Path

def verified_library(root):
    root=Path(root)
    proof=json.loads((root/'docs/working-native-verification.json').read_text())
    expected=proof['core_source_sha256']
    current={'core/Cargo.toml','core/Cargo.lock',*(p.relative_to(root).as_posix() for p in (root/'core/src').rglob('*.rs'))}
    if current!=set(expected):raise ValueError('Working native proof omits or retains core sources. Rebuild the native outputs.')
    for name,digest in expected.items():
        if hashlib.sha256((root/name).read_bytes()).hexdigest()!=digest:raise ValueError('Working native sources changed. Rebuild the native outputs.')
    library=root/'core/host-libs/linux-x86_64/libonklav_core.so'
    if not library.is_file() or hashlib.sha256(library.read_bytes()).hexdigest()!=proof['linux_jni_sha256']:
        raise ValueError('Working Linux JNI is missing or does not match its source proof.')
    mobile=root/'android/shared/src/diagnostic/jniLibs/arm64-v8a/libonklav_core.so'
    if not mobile.is_file() or hashlib.sha256(mobile.read_bytes()).hexdigest()!=proof['arm64_jni_sha256']:
        raise ValueError('Working ARM64 JNI is missing or does not match its source proof.')
    return library
