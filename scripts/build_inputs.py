"""Hash checks for artifact reuse, independent of Android/Rust tools."""
import hashlib
import json
from pathlib import Path


def sha(path):
    digest = hashlib.sha256()
    with Path(path).open('rb') as stream:
        while block := stream.read(1024 * 1024):
            digest.update(block)
    return digest.hexdigest()


def proof(root, name):
    try:
        result = json.loads((root / 'docs' / name).read_text())
        return result if isinstance(result, dict) else {}
    except (OSError, ValueError):
        return {}


def matches(root, expected, actual):
    return bool(expected) and set(expected) == set(actual) and all(
        (root / name).is_file() and sha(root / name) == digest
        for name, digest in expected.items())


def native_current(root, abis, host=False):
    p = proof(root, 'host-native-provenance.json' if host else 'native-provenance.json')
    inputs = ['core/Cargo.toml', 'core/Cargo.lock'] + [
        f.relative_to(root).as_posix() for f in (root / 'core/src').rglob('*.rs')]
    if not matches(root, p.get('files_sha256', {}), inputs):
        return False
    if host:
        path = root / 'core/target/debug/libonklav_core.so'
        return path.is_file() and sha(path) == p.get('host_library_sha256')
    if p.get('rustflags') != '-C link-arg=-Wl,-z,max-page-size=16384':
        return False
    for abi in abis:
        path = root / f'android/shared/src/main/jniLibs/{abi}/libonklav_core.so'
        if not path.is_file() or sha(path) != p.get('mobile_libraries_sha256', {}).get(abi):
            return False
    return True


def sdk_current(root):
    p = proof(root, 'app-sdk-provenance.json')
    from sdk_build_plan import APPS, FLAGS, inputs, plan
    try:
        # A subset of package records is not proof of a complete host/starter.
        if set(p.get('packages', {})) != set(APPS) or p.get('flags') != FLAGS:
            return False
        if p.get('inputs_sha256') != inputs(root):
            return False
        _, states = plan(root, p)
        return not any(state['build'] or state['restore_bundle'] or state.get('remove_bundle',False) for state in states.values())
    except (OSError, KeyError, TypeError, ValueError, AttributeError):
        return False


def game_inputs(root):
    paths=[f for f in (root/'games/engine-checks').rglob('*')
           if f.is_file() and '.godot' not in f.parts and f.suffix!='.uid']
    paths += [root/n for n in ('scripts/build_games.py','scripts/build_web_engine.py','developer/games/loading.py','developer/games/templates/shared/loading.css','developer/games/templates/shared/loading.js','developer/games/templates/shared/loading.html','developer/games/templates/shared/loading-logo.svg','developer/games/engine.json','developer/games/engine-template.zip','developer/games/onklav-controls.json','docs/godot-web-engine.json','third_party/godot-web-fixes/worker-group-cleanup.patch') if (root/n).is_file()]
    return sorted(f.relative_to(root).as_posix() for f in paths)


def games_current(root):
    p = proof(root, 'games-provenance.json')
    if p.get('assets_sha256'):
        actual=[f.relative_to(root).as_posix() for folder in ('godot-web','godot-builtin') for f in (root/'android/games/src/main/assets'/folder).glob('*') if f.is_file()]
        return matches(root,p.get('files_sha256',{}),game_inputs(root)) and matches(root,p['assets_sha256'],actual)
    pack = root / p.get('pack', 'missing.pck')
    return (matches(root, p.get('files_sha256', {}), game_inputs(root))
            and pack.is_file() and sha(pack) == p.get('pack_sha256'))


def android_inputs(root,variant):
    """Production inputs only: editing a test cannot silently change an APK."""
    paths=[root/n for n in ['build.gradle.kts','settings.gradle.kts','gradle.properties','gradlew','gradle/wrapper/gradle-wrapper.properties','gradle/wrapper/gradle-wrapper.jar'] if (root/n).is_file()]
    for module in (root/'android').iterdir():
        if not module.is_dir():continue
        config=module/'build.gradle.kts'
        if config.is_file():paths.append(config)
        paths.extend(module.glob('*.pro'))
        paths.extend(p for p in (module/'libs').rglob('*') if p.is_file())
        sources=['main',variant]+(['release'] if variant=='optimized' and module.name!='app' else [])
        for source in sources:
            folder=module/'src'/source
            if folder.is_dir():paths.extend(p for p in folder.rglob('*') if p.is_file())
    return {p.relative_to(root).as_posix():sha(p) for p in sorted(set(paths))}
