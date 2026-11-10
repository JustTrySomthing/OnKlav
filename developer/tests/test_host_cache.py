"""Bounded checks for incremental rebuilds after restoring source files."""
import importlib.util,json,os,tempfile,unittest
from pathlib import Path
from unittest.mock import patch
HERE=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('cache_sdk',HERE/'onklav.py');sdk=importlib.util.module_from_spec(spec);spec.loader.exec_module(sdk)

class HostCache(unittest.TestCase):
 def test_changed_restored_sources_refresh_without_touching_unchanged_inputs(self):
  with tempfile.TemporaryDirectory() as tmp,patch.object(sdk,'ROOT',Path(tmp)):
   root=Path(tmp);a=root/'a.rs';b=root/'b.rs';ledger=root/'inputs.json'
   a.write_text('first');b.write_text('unchanged');os.utime(a,ns=(1,1));os.utime(b,ns=(1,1))
   initial=sdk.refresh_runtime_inputs(ledger,[a,b]);ledger.write_text(json.dumps(initial));unchanged=b.stat().st_mtime_ns
   a.write_text('changed');os.utime(a,ns=(1,1));updated=sdk.refresh_runtime_inputs(ledger,[a,b]);self.assertGreater(a.stat().st_mtime_ns,1);self.assertEqual(unchanged,b.stat().st_mtime_ns);self.assertNotEqual(initial['a.rs'],updated['a.rs'])
   ledger.write_text(json.dumps(updated));before=a.stat().st_mtime_ns;sdk.refresh_runtime_inputs(ledger,[a,b]);self.assertEqual(before,a.stat().st_mtime_ns)
 def test_invalid_ledger_refreshes_safely(self):
  with tempfile.TemporaryDirectory() as tmp,patch.object(sdk,'ROOT',Path(tmp)):
   root=Path(tmp);source=root/'lib.rs';source.write_text('source');ledger=root/'inputs.json'
   for invalid in ['broken','[]','null']:
    ledger.write_text(invalid);os.utime(source,ns=(1,1));result=sdk.refresh_runtime_inputs(ledger,[source]);self.assertIn('lib.rs',result);self.assertGreater(source.stat().st_mtime_ns,1)
 def test_build_retains_cache_and_stamps_only_verified_sources(self):
  with tempfile.TemporaryDirectory() as tmp,patch.object(sdk,'HERE',Path(tmp)),patch.object(sdk,'runtime_fingerprint',return_value='hash'),patch.object(sdk,'runtime_inputs',return_value=[]),patch.object(sdk,'execute') as execute,patch.object(sdk,'Runtime') as runtime:
   stamp=Path(tmp)/'target/host/onklav-runtime-source.txt';stamp.parent.mkdir(parents=True);stamp.write_text('old')
   runtime.return_value.__enter__.return_value.request.return_value={'build':sdk.HOST_BUILD,'source':'hash'}
   sdk.build_host();self.assertEqual(1,execute.call_count);self.assertEqual('build',execute.call_args.args[0][1]);self.assertEqual('hash',stamp.read_text())
   runtime.return_value.__enter__.return_value.request.return_value={'build':sdk.HOST_BUILD,'source':'wrong'}
   stamp.write_text('old')
   with self.assertRaisesRegex(ValueError,'does not match'):sdk.build_host()
   self.assertEqual('old',stamp.read_text())

if __name__=='__main__':unittest.main(verbosity=2)
