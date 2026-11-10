"""The downloadable starter must include its real tools and linked documentation."""
import importlib.util,json,tempfile,unittest,zipfile,hashlib
from pathlib import Path
HERE=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('adk_dist',HERE/'package.py');dist=importlib.util.module_from_spec(spec);spec.loader.exec_module(dist)
class Distribution(unittest.TestCase):
 def test_starter_contains_sources_tools_and_verified_content_without_builds_or_keys(self):
  with tempfile.TemporaryDirectory()as temp:
   out=Path(temp)/'starter.zip';result=dist.build_starter(out)
   self.assertEqual('0.4.1',result['sdk_tools']);self.assertTrue(dist.verify_starter(out)['verified'])
   with zipfile.ZipFile(out)as z:
    names=set(z.namelist());self.assertIn('developer/ci_smoke.py',names);self.assertIn('core/src/bin/onklav-pack.rs',names);self.assertIn('developer/docs/CURRENT-HOST.md',names)
    self.assertFalse(any(set(Path(name).parts)&dist.EXCLUDED or Path(name).suffix in dist.SECRET_SUFFIXES for name in names))
    index=json.loads(z.read('CONTENTS.json'));self.assertEqual(96,index['host_protocol_build']);self.assertEqual(151,index['reference_apk']['version_code'])
    for name,digest in index['files_sha256'].items():self.assertEqual(digest,hashlib.sha256(z.read(name)).hexdigest(),name)
 def test_modified_content_is_rejected(self):
  with tempfile.TemporaryDirectory()as temp:
   out=Path(temp)/'starter.zip';dist.build_starter(out);bad=Path(temp)/'changed.zip'
   with zipfile.ZipFile(out)as src,zipfile.ZipFile(bad,'w')as target:
    for item in src.infolist():target.writestr(item.filename,b'changed'if item.filename=='README.md'else src.read(item.filename))
   with self.assertRaisesRegex(ValueError,'mismatch'):dist.verify_starter(bad)
 def test_existing_download_is_not_overwritten(self):
  with tempfile.TemporaryDirectory()as temp:
   out=Path(temp)/'starter.zip';out.write_bytes(b'keep')
   with self.assertRaises(ValueError):dist.build_starter(out)
   self.assertEqual(b'keep',out.read_bytes())
if __name__=='__main__':unittest.main()
