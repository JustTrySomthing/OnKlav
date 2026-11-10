"""Cross-platform process handling and machine-readable CLI output contracts."""
import contextlib,importlib.util,io,subprocess,unittest
from pathlib import Path
from unittest.mock import MagicMock,patch
HERE=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('cli_sdk',HERE/'onklav.py');sdk=importlib.util.module_from_spec(spec);spec.loader.exec_module(sdk)
class PortableCli(unittest.TestCase):
 def test_packer_status_uses_stderr_and_keeps_json_stdout_clean(self):
  out=io.StringIO();err=io.StringIO()
  with patch.object(sdk,'execute',return_value='Created package\n')as execute,contextlib.redirect_stdout(out),contextlib.redirect_stderr(err):sdk.packer('inspect','example.onklav-a')
  self.assertEqual('',out.getvalue());self.assertEqual('Created package\n',err.getvalue());self.assertTrue(execute.call_args.kwargs['capture'])
 def test_windows_timeout_attempts_process_tree_termination(self):
  process=MagicMock();process.pid=4321;process.communicate.side_effect=[subprocess.TimeoutExpired('cargo',.1),('',None)];process.poll.return_value=None
  with patch.object(sdk.os,'name','nt'),patch.object(sdk.subprocess,'Popen',return_value=process),patch.object(sdk.subprocess,'run')as stop:
   with self.assertRaisesRegex(ValueError,'exceeded'):sdk.execute(['cargo'],timeout=.1)
  self.assertEqual(['taskkill','/PID','4321','/T','/F'],stop.call_args.args[0]);process.kill.assert_called_once()
if __name__=='__main__':unittest.main()
