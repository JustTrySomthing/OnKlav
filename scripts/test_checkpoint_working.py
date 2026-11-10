import json
from pathlib import Path
import tempfile
import unittest
import zipfile
from checkpoint_working import create, native_status, sha


class CheckpointTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name) / "source"
        self.root.mkdir()
        self.base = Path(self.temp.name) / "base.zip"
        self.output = Path(self.temp.name) / "next.zip"
        data = {
            "core/src/lib.rs": b"original",
            "core/host-libs/linux-x86_64/libonklav_core.so": b"host",
            "android/shared/src/diagnostic/jniLibs/arm64-v8a/libonklav_core.so": b"mobile",
            "docs/developer-improvement-progress.json": b'{"complete":false}',
        }
        proof = {"core_source_sha256": {"core/src/lib.rs": sha(b"original")}, "linux_jni_sha256": sha(b"host"), "arm64_jni_sha256": sha(b"mobile")}
        data["docs/working-native-verification.json"] = json.dumps(proof).encode()
        with zipfile.ZipFile(self.base, "w") as z:
            for n, value in data.items():
                p = self.root / n
                p.parent.mkdir(parents=True, exist_ok=True)
                p.write_bytes(value)
                z.writestr("OnKlav/" + n, value)
            z.writestr("checkpoint.json", json.dumps({"updated_sha256": {n: sha(v) for n, v in data.items()}, "new_files": []}))

    def test_overlay_preserves_baseline_and_records_new_source(self):
        original = self.base.read_bytes()
        (self.root / "docs/new.md").write_text("saved work")
        (self.root / "docs/developer-improvement-progress.json").write_text('{"latest_review":"docs/new.md"}')
        status = create(self.root, self.base, self.output, ["docs/new.md"])
        self.assertTrue(status["matches"])
        self.assertEqual(original, self.base.read_bytes())
        with zipfile.ZipFile(self.output) as z:
            c = json.loads(z.read("checkpoint.json"))
            self.assertEqual(b"saved work", z.read("OnKlav/docs/new.md"))
            self.assertEqual("docs/new.md", c["latest_increment"])
            for n, h in c["updated_sha256"].items():
                self.assertEqual(h, sha(z.read("OnKlav/" + n)))

    def test_stale_native_is_saved_as_requiring_rebuild_without_forging_proof(self):
        (self.root / "core/src/new.rs").write_text("new source")
        (self.root / "core/src/lib.rs").write_text("changed")
        status = create(self.root, self.base, self.output, ["core/src/new.rs"])
        self.assertTrue(status["rebuild_required"])
        self.assertEqual(["core/src/lib.rs", "core/src/new.rs"], status["changed_or_missing"])
        with zipfile.ZipFile(self.output) as z:
            self.assertEqual(sha(b"original"), json.loads(z.read("OnKlav/docs/working-native-verification.json"))["core_source_sha256"]["core/src/lib.rs"])

    def test_unsafe_paths_and_missing_files_preserve_previous_output(self):
        self.output.write_bytes(b"previous")
        for name in ["../outside.rs", "/tmp/out.rs", "docs/../../out.rs", "keys/signing.json", "core/target/key.rs", "docs/key.key"]:
            with self.assertRaises(ValueError):
                create(self.root, self.base, self.output, [name])
            self.assertEqual(b"previous", self.output.read_bytes())
        (self.root / "core/src/lib.rs").unlink()
        with self.assertRaises(ValueError):
            create(self.root, self.base, self.output, [])
        self.assertEqual(b"previous", self.output.read_bytes())

    def test_complete_handoff_base_is_verified_and_updated_without_losing_members(self):
        full = Path(self.temp.name) / "full.zip"
        with zipfile.ZipFile(self.base) as old, zipfile.ZipFile(full, "w") as archive:
            files = {n: old.read(n) for n in old.namelist() if n != "checkpoint.json"}
            for n, data in files.items():
                archive.writestr(n, data)
            archive.writestr("OnKlav/HANDOFF-MANIFEST.json", json.dumps({"files_sha256": {n: sha(data) for n, data in files.items()}}))
        previous = full.read_bytes()
        (self.root / "HANDOFF-MANIFEST.json").write_text(json.dumps({"files_sha256": {n: sha(data) for n, data in files.items()}}))
        (self.root / "docs/new.md").write_text("recovered")
        self.assertTrue(create(self.root, full, self.output, ["docs/new.md"])["matches"])
        self.assertEqual(previous, full.read_bytes())
        with zipfile.ZipFile(self.output) as archive:
            manifest = json.loads(archive.read("OnKlav/HANDOFF-MANIFEST.json"))
            self.assertIn("OnKlav/docs/new.md", manifest["files_sha256"])
            for n, digest in manifest["files_sha256"].items():
                self.assertEqual(digest, sha(archive.read(n)))
        self.assertTrue(create(self.root, self.output, Path(self.temp.name) / "third.zip", [])["matches"])
        with zipfile.ZipFile(full, "a") as archive:
            archive.writestr("OnKlav/docs/untracked.md", "unsafe omission")
        with self.assertRaisesRegex(ValueError, "inventory mismatch"):
            create(self.root, full, self.output, [])

    def test_new_sdk_cargo_inputs_are_preserved(self):
        folder = self.root / "sdk/apps/radio"
        folder.mkdir(parents=True)
        for name in ["Cargo.toml", "Cargo.lock"]:
            (folder / name).write_text("radio source")
        self.assertTrue(create(self.root, self.base, self.output, ["sdk/apps/radio/Cargo.toml", "sdk/apps/radio/Cargo.lock"])["matches"])
        with zipfile.ZipFile(self.output) as z:
            self.assertEqual(b"radio source", z.read("OnKlav/sdk/apps/radio/Cargo.lock"))

    def test_intentional_deletion_is_retained_across_checkpoints(self):
        name = "docs/obsolete.md"
        (self.root / name).write_text("obsolete")
        intermediate = Path(self.temp.name) / "intermediate.zip"
        create(self.root, self.base, intermediate, [name])
        (self.root / name).unlink()
        create(self.root, intermediate, self.output, [], [name])
        third = Path(self.temp.name) / "third.zip"
        create(self.root, self.output, third, [])
        with zipfile.ZipFile(third) as archive:
            self.assertNotIn("OnKlav/" + name, archive.namelist())
            self.assertIn(name, json.loads(archive.read("checkpoint.json"))["deleted_files"])

    def test_corrupt_base_is_rejected(self):
        with zipfile.ZipFile(self.base, "a") as z:
            c = json.loads(z.read("checkpoint.json"))
            c["updated_sha256"]["core/src/lib.rs"] = "0" * 64
        corrupt = Path(self.temp.name) / "corrupt.zip"
        with zipfile.ZipFile(self.base) as old, zipfile.ZipFile(corrupt, "w") as new:
            for n in old.namelist():
                new.writestr(n, json.dumps(c) if n == "checkpoint.json" else old.read(n))
        with self.assertRaises(ValueError):
            create(self.root, corrupt, self.output, [])


if __name__ == "__main__":
    unittest.main()
