"""A stalled process must stop instead of leaving app tests waiting indefinitely."""
import importlib.util,tempfile,time,unittest,sys,os
from pathlib import Path
HERE=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('sdk',HERE/'onklav.py');sdk=importlib.util.module_from_spec(spec);spec.loader.exec_module(sdk)
class Deadline(unittest.TestCase):
 def test_stalled_runtime_is_killed_and_streams_are_closed(self):
  with tempfile.TemporaryDirectory() as root:
   p=Path(root)/'stalled';p.write_text('#!/usr/bin/env python3\nimport time\ntime.sleep(60)\n');p.chmod(0o700)
   for request in ({'action':'list'},{'action':'event','value':'x'*1000000}):
    start=time.monotonic()
    with sdk.Runtime(root,p,request_timeout=.2) as rt:
     with self.assertRaisesRegex(ValueError,'timed out'):rt.request(request)
     self.assertIsNotNone(rt.process.poll())
    self.assertLess(time.monotonic()-start,5);self.assertTrue(rt.process.stdout.closed)
 def test_compile_timeout_stops_the_process_tree_on_posix(self):
  if os.name=='nt':self.skipTest('POSIX process-group containment')
  with tempfile.TemporaryDirectory() as root:
   pidfile=Path(root)/'child.pid'
   child='import time;time.sleep(60)'
   parent='import subprocess,sys,time;from pathlib import Path;p=subprocess.Popen([sys.executable,"-c",'+repr(child)+']);Path(sys.argv[1]).write_text(str(p.pid));time.sleep(60)'
   with self.assertRaisesRegex(ValueError,'exceeded'):sdk.execute([sys.executable,'-c',parent,pidfile],timeout=.3)
   pid=int(pidfile.read_text());stat=Path('/proc')/str(pid)/'stat'
   if stat.exists():self.assertEqual('Z',stat.read_text().split()[2])
if __name__=='__main__':unittest.main(verbosity=2)
