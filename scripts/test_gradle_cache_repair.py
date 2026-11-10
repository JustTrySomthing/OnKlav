import tempfile
import unittest
from pathlib import Path
from gradle_cache_repair import quarantine_unreadable_transforms


class GradleCacheRepairTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.home = Path(self.temp.name).resolve()
        self.target = self.home / "caches/8.11.1/transforms" / ("a" * 32)
        self.target.mkdir(parents=True)
        (self.target / "metadata.bin").write_bytes(b"unreadable generated metadata")
        self.failure = "Execution failed for task ':android:app:checkDebugAarMetadata'."
        self.line = self.failure + "\nCould not read workspace metadata from " + str(self.target / "metadata.bin")

    def test_only_named_generated_artifact_is_quarantined_once_and_preserved(self):
        dependency = self.home / "caches/modules-2/keep.jar"
        dependency.parent.mkdir(); dependency.write_bytes(b"required dependency")
        moved = quarantine_unreadable_transforms(self.line + "\n" + self.line, self.home)
        self.assertEqual(1, len(moved)); self.assertFalse(self.target.exists())
        self.assertEqual(b"unreadable generated metadata", (moved[0] / "metadata.bin").read_bytes())
        self.assertEqual(b"required dependency", dependency.read_bytes())
        self.assertEqual([], quarantine_unreadable_transforms(self.line, self.home))

    def test_compile_and_test_failures_do_not_trigger_cache_mutation(self):
        self.assertEqual([], quarantine_unreadable_transforms("test FAILED: expected true", self.home))
        compiler = self.line.replace("checkDebugAarMetadata", "compileDebugKotlin")
        tests = self.line.replace("checkDebugAarMetadata", "testDebugUnitTest")
        self.assertEqual([], quarantine_unreadable_transforms(compiler, self.home))
        self.assertEqual([], quarantine_unreadable_transforms(tests, self.home))
        self.assertTrue(self.target.exists())

    def test_other_cache_root_cannot_be_moved(self):
        self.assertEqual([], quarantine_unreadable_transforms(self.line, self.home / "different"))
        self.assertTrue(self.target.exists())

    def test_transform_symlink_cannot_retarget_the_repair(self):
        outside = self.home / "owner-data"; outside.mkdir()
        link = self.target.parent / ("b" * 32); link.symlink_to(outside, target_is_directory=True)
        line = self.failure + "\nCould not read workspace metadata from " + str(link / "metadata.bin")
        self.assertEqual([], quarantine_unreadable_transforms(line, self.home)); self.assertTrue(outside.exists())

    def test_quarantine_symlink_is_not_followed(self):
        outside = self.home / "owner-data"; outside.mkdir()
        (self.home / "quarantined-transforms").symlink_to(outside, target_is_directory=True)
        self.assertEqual([], quarantine_unreadable_transforms(self.line, self.home))
        self.assertTrue(self.target.exists()); self.assertEqual([], list(outside.iterdir()))


    def checker(self, outcomes, clock=None):
        import ast, os, shutil, signal, subprocess, sys, time
        from types import SimpleNamespace
        root = ast.parse(Path(__file__).with_name("check_android.py").read_text())
        function = next(n for n in root.body if isinstance(n, ast.FunctionDef) and n.name == "run")
        evidence = self.home / "evidence"; evidence.mkdir(exist_ok=True)
        calls = []
        kill = []
        def popen(args, **kwargs):
            code, text = outcomes[len(calls)]; calls.append(args)
            kwargs["stdout"].write(text.encode())
            process = SimpleNamespace(returncode=code, pid=123456)
            process.poll = lambda: process.returncode
            def wait(**ignored):
                if process.returncode is None: process.returncode = -9
                return process.returncode
            process.wait = wait
            return process
        namespace = dict(evidence=evidence, ROOT=self.home, env={"GRADLE_USER_HOME":str(self.home)},
                         Path=Path, shutil=shutil, sys=sys, signal=signal,
                         os=SimpleNamespace(killpg=lambda *args:kill.append(args)),
                         subprocess=SimpleNamespace(Popen=popen, TimeoutExpired=subprocess.TimeoutExpired, STDOUT=subprocess.STDOUT),
                         time=time if clock is None else SimpleNamespace(monotonic=iter(clock).__next__),
                         print=lambda *args, **kwargs:None)
        exec(compile(ast.Module(body=[function],type_ignores=[]),"check_android.py", "exec"), namespace)
        return namespace["run"], calls, kill, evidence

    def test_checker_recovers_once_and_preserves_failure_log(self):
        run, calls, _, evidence = self.checker([(1,self.line),(0,"BUILD SUCCESSFUL")])
        run("check",["bash","gradlew"],30)
        self.assertEqual(2,len(calls)); self.assertTrue((evidence/"check.metadata-failure.log").is_file())
        self.assertEqual("BUILD SUCCESSFUL",(evidence/"check.log").read_text())

    def test_checker_stops_on_product_failure_without_retrying(self):
        failure = self.line.replace("checkDebugAarMetadata","testDebugUnitTest")
        run, calls, _, _ = self.checker([(1,failure)])
        with self.assertRaises(SystemExit): run("check",["bash","gradlew"],30)
        self.assertEqual(1,len(calls)); self.assertTrue(self.target.exists())

    def test_checker_has_one_retry_and_one_total_deadline(self):
        run, calls, _, _ = self.checker([(1,self.line),(1,self.line)])
        with self.assertRaises(SystemExit): run("failed",["bash","gradlew"],30)
        self.assertEqual(2,len(calls))
        self.target.mkdir(); (self.target/"metadata.bin").write_bytes(b"generated")
        run, calls, kill, _ = self.checker([(1,self.line),(None,"")],clock=[0,5,31])
        with self.assertRaisesRegex(SystemExit,"exceeded"): run("timeout",["bash","gradlew"],30)
        self.assertEqual(2,len(calls)); self.assertEqual(1,len(kill))


if __name__ == "__main__":
    unittest.main()
