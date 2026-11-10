"""A working restore must never silently accept changed core code or binaries."""
import hashlib,json,tempfile,unittest
from pathlib import Path
from working_native import verified_library

class WorkingNative(unittest.TestCase):
    def fixture(self,root):
        files={'core/Cargo.toml':b'package','core/Cargo.lock':b'locked','core/src/lib.rs':b'source','core/host-libs/linux-x86_64/libonklav_core.so':b'host','android/shared/src/diagnostic/jniLibs/arm64-v8a/libonklav_core.so':b'phone'}
        for name,data in files.items():
            p=root/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data)
        p=root/'docs/working-native-verification.json';p.parent.mkdir()
        p.write_text(json.dumps({'core_source_sha256':{n:hashlib.sha256(v).hexdigest()for n,v in files.items()if not n.endswith('.so')},'linux_jni_sha256':hashlib.sha256(b'host').hexdigest(),'arm64_jni_sha256':hashlib.sha256(b'phone').hexdigest()}))
    def test_restore_requires_exact_source_and_both_native_outputs(self):
        with tempfile.TemporaryDirectory()as temp:
            root=Path(temp);self.fixture(root)
            self.assertEqual(b'host',verified_library(root).read_bytes())
            added=root/'core/src/new.rs';added.write_text('new')
            with self.assertRaisesRegex(ValueError,'omits'):verified_library(root)
            added.unlink();source=root/'core/src/lib.rs';source.write_bytes(b'changed')
            with self.assertRaisesRegex(ValueError,'sources changed'):verified_library(root)
            source.write_bytes(b'source');mobile=root/'android/shared/src/diagnostic/jniLibs/arm64-v8a/libonklav_core.so';mobile.write_bytes(b'wrong')
            with self.assertRaisesRegex(ValueError,'ARM64'):verified_library(root)

if __name__=='__main__':unittest.main()
