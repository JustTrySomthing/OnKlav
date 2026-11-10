"""Regression checks: never reuse stale native, SDK or game artifacts."""
import json
from pathlib import Path
import tempfile
import unittest
from build_inputs import sha, native_current, sdk_current, games_current, android_inputs


class ArtifactReuseTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def put(self, name, data=b'current'):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
        return path

    def proof(self, name, data):
        self.put('docs/' + name, json.dumps(data).encode())

    def native(self):
        names = ['core/Cargo.toml', 'core/Cargo.lock', 'core/src/lib.rs']
        hashes = {n: sha(self.put(n)) for n in names}
        lib = self.put('android/shared/src/main/jniLibs/arm64-v8a/libonklav_core.so')
        self.proof('native-provenance.json', {'files_sha256': hashes,
                   'rustflags': '-C link-arg=-Wl,-z,max-page-size=16384',
                   'mobile_libraries_sha256': {'arm64-v8a': sha(lib)}})

    def test_requested_architecture_requires_its_own_proof_and_bytes(self):
        self.native()
        self.assertTrue(native_current(self.root, ['arm64-v8a']))
        self.assertFalse(native_current(self.root, ['arm64-v8a', 'x86_64']))
        self.put('android/shared/src/main/jniLibs/arm64-v8a/libonklav_core.so', b'stale')
        self.assertFalse(native_current(self.root, ['arm64-v8a']))

    def test_added_and_changed_rust_sources_invalidate_reuse(self):
        self.native()
        self.put('core/src/new.rs')
        self.assertFalse(native_current(self.root, ['arm64-v8a']))
        (self.root / 'core/src/new.rs').unlink()
        self.put('core/src/lib.rs', b'new source')
        self.assertFalse(native_current(self.root, ['arm64-v8a']))

    def test_missing_and_malformed_proofs_never_count_as_current(self):
        self.assertFalse(native_current(self.root, ['arm64-v8a']))
        self.assertFalse(sdk_current(self.root))
        self.assertFalse(games_current(self.root))
        for invalid in [b'broken', b'[]', b'null', b'0']:
            self.put('docs/native-provenance.json', invalid)
            self.assertFalse(native_current(self.root, ['arm64-v8a']))

    def test_game_pack_and_source_must_both_match(self):
        src = self.put('games/engine-checks/main.gd')
        pack = self.put('android/games/src/main/assets/onklav-games.pck')
        self.proof('games-provenance.json', {
            'files_sha256': {'games/engine-checks/main.gd': sha(src)},
            'pack': 'android/games/src/main/assets/onklav-games.pck', 'pack_sha256': sha(pack)})
        self.assertTrue(games_current(self.root))
        self.put('games/engine-checks/main.gd', b'changed')
        self.assertFalse(games_current(self.root))
        self.put('games/engine-checks/main.gd')
        self.put('android/games/src/main/assets/onklav-games.pck', b'old')
        self.assertFalse(games_current(self.root))

    def test_android_snapshot_tracks_code_resources_and_configuration(self):
        self.put('android/app/src/main/kotlin/App.kt')
        self.put('android/app/build.gradle.kts')
        before=android_inputs(self.root,'optimized')
        self.put('android/app/src/main/res/values/strings.xml')
        self.assertNotEqual(before,android_inputs(self.root,'optimized'))
        before=android_inputs(self.root,'optimized')
        self.put('android/app/build.gradle.kts',b'new config')
        self.assertNotEqual(before,android_inputs(self.root,'optimized'))
        before=android_inputs(self.root,'optimized')
        self.put('android/shared/src/release/kotlin/Library.kt')
        self.assertNotEqual(before,android_inputs(self.root,'optimized'))

    def test_android_snapshot_excludes_test_and_other_variant_outputs(self):
        self.put('android/app/src/main/kotlin/App.kt')
        before=android_inputs(self.root,'optimized')
        self.put('android/app/src/test/kotlin/Test.kt')
        self.put('android/app/src/debug/AndroidManifest.xml')
        self.put('android/app/build/generated/File.kt')
        self.assertEqual(before,android_inputs(self.root,'optimized'))


if __name__ == '__main__':
    unittest.main()
