"""Real storage/broker tests with staged fixtures, not Android-provider acceptance."""
import importlib.util,json,tempfile,unittest
from pathlib import Path
HERE=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('import_sdk',HERE/'onklav.py');sdk=importlib.util.module_from_spec(spec);spec.loader.exec_module(sdk)

class TextPicker(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.tmp=tempfile.TemporaryDirectory(prefix='onklav-import-');cls.root=Path(cls.tmp.name);cls.project=HERE/'examples/text-inspector';key=cls.root/'test-author.key';sdk.packer('keygen',key);cls.package=Path(sdk.build_project(cls.project,key,cls.root/'test.onklav-a')['package']);cls.binary=sdk.build_host()
 @classmethod
 def tearDownClass(cls):cls.tmp.cleanup()
 def start(self,runtime,grants):
  review=runtime.request({'action':'stage','path':str(self.package)});runtime.request({'action':'install','hash':review['hash'],'grants':grants});return runtime.request({'action':'launch','id':'org.example.textinspector'})['session']
 def event(self,runtime,token,action):return runtime.request({'action':'event','session':token,'event':{'action':action}})
 def import_fixture(self,runtime,root,token,name,mime,content):
  pending=self.event(runtime,token,'sdk.files.import')['pending'];self.assertEqual('library.import',pending['service']);self.assertEqual('downloads',pending['args']['folder']);staged=root/'content-import.tmp';staged.write_bytes(content)
  entry=runtime.request({'action':'library_host','session':token,'token':pending['token'],'operation':'import','name':name,'mime':mime});self.assertFalse(staged.exists());self.assertNotIn('path',entry);self.assertNotIn('uri',entry)
  out=runtime.request({'action':'reply','session':token,'token':pending['token'],'reply':{'ok':True,'value':{'imported':1}}});self.assertEqual('downloads',out['frame']['state']['picker']['folder']);self.assertNotIn('pending',out);return entry
 def test_imported_utf8_is_processed_and_binary_failure_keeps_previous_result(self):
  with tempfile.TemporaryDirectory() as tmp:
   root=Path(tmp)
   with sdk.Runtime(root,self.binary) as runtime:
    token=self.start(runtime,['files.manage']);self.event(runtime,token,'sdk.files.open');text='Hello café\r\n🙂 second line\r\n';entry=self.import_fixture(runtime,root,token,'fixture.txt','text/plain',b'\xef\xbb\xbf'+text.encode())
    loaded=self.event(runtime,token,'sdk.files.entry.'+entry['id']);state=loaded['frame']['state']['app'];self.assertEqual('fixture.txt',state['name']);self.assertEqual(len(text),state['characters']);self.assertEqual(5,state['words']);self.assertEqual(2,state['lines']);self.assertNotIn('pending',loaded)
    self.event(runtime,token,'sdk.files.open');binary=self.import_fixture(runtime,root,token,'binary.bin','application/octet-stream',b'\x00\xff\x01');failed=self.event(runtime,token,'sdk.files.entry.'+binary['id']);self.assertTrue(failed['frame']['menu_open']);self.assertEqual(state['preview'],failed['frame']['state']['app']['preview']);self.assertEqual(state['words'],failed['frame']['state']['app']['words']);self.assertEqual('fixture.txt',failed['frame']['state']['app']['name'])
    self.event(runtime,token,'sdk.files.error.close');self.assertEqual(2,sdk.test_project(self.project,self.package)['passed'])
 def test_denied_and_revoked_imports_cannot_commit_a_staged_file(self):
  with tempfile.TemporaryDirectory() as tmp:
   root=Path(tmp)
   with sdk.Runtime(root,self.binary) as runtime:
    denied=self.start(runtime,[]);self.event(runtime,denied,'sdk.files.open');out=self.event(runtime,denied,'sdk.files.import');self.assertNotIn('pending',out);self.assertEqual('files.manage',out['frame']['state']['sdk.permission.recovery']['capability'])
    runtime.request({'action':'permissions','id':'org.example.textinspector','grants':['files.manage']});token=runtime.request({'action':'launch','id':'org.example.textinspector'})['session'];self.event(runtime,token,'sdk.files.open');pending=self.event(runtime,token,'sdk.files.import')['pending'];staged=root/'content-import.tmp';staged.write_bytes(b'keep staged data')
    runtime.request({'action':'permissions','id':'org.example.textinspector','grants':[]});runtime.request({'action':'library_host','session':token,'token':pending['token'],'operation':'import','name':'blocked.txt','mime':'text/plain'},error=True);self.assertEqual(b'keep staged data',staged.read_bytes())
    runtime.request({'action':'permissions','id':'org.example.textinspector','grants':['files.manage']});restored=runtime.request({'action':'launch','id':'org.example.textinspector'})['session'];self.event(runtime,restored,'sdk.files.open');listing=self.event(runtime,restored,'sdk.files.entry.downloads');self.assertEqual([],listing['frame']['state']['picker']['items'])

if __name__=='__main__':unittest.main(verbosity=2)
