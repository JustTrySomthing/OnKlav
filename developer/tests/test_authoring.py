"""Focused real-broker checks for the next unreleased developer workflow."""
import importlib.util,io,json,os,tempfile,unittest
from pathlib import Path
from unittest.mock import patch
HERE=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('authoring_sdk',HERE/'onklav.py');sdk=importlib.util.module_from_spec(spec);spec.loader.exec_module(sdk)
class Authoring(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.tmp=tempfile.TemporaryDirectory(prefix='onklav-authoring-');cls.root=Path(cls.tmp.name);cls.project=cls.root/'form'
  sdk.new_project(cls.project,'org.example.authoring','Authoring form','form');cls.binary=sdk.build_host()
  cls.key=cls.root/'author.key';sdk.packer('keygen',cls.key);cls.package=Path(sdk.build_project(cls.project,cls.key,None)['package'])
 @classmethod
 def tearDownClass(cls):cls.tmp.cleanup()
 def test_declarative_project_validates_and_persists_using_the_real_broker(self):
  result=sdk.validate_project(self.project,self.binary);self.assertEqual(4,result['form_schema']['fields']);self.assertEqual('form.json',result['form_schema']['file'])
  self.assertEqual(4,sdk.test_project(self.project,self.package)['passed']);self.assertIn('include_str!("../form.json")',(self.project/'src/lib.rs').read_text())
 def test_bad_configuration_stops_before_wasm_build_or_signing(self):
  p=self.root/'bad';sdk.new_project(p,'org.example.badconfig','Bad config','form');v=json.loads((p/'form.json').read_text());v['fields'][0]['reqired']=True;(p/'form.json').write_text(json.dumps(v))
  with patch.object(sdk,'packer',side_effect=AssertionError('signing started')):
   with self.assertRaisesRegex(ValueError,r'form.json:.*fields\[0\].reqired'):sdk.verify_project(p)
  self.assertFalse((p/'target/wasm32-unknown-unknown').exists())
 def test_form_capability_error_does_not_start_a_runtime(self):
  p=self.root/'denied-config';sdk.new_project(p,'org.example.configpermission','Config permission','form');m=json.loads((p/'manifest.json').read_text());m['capabilities']=[];(p/'manifest.json').write_text(json.dumps(m))
  with patch.object(sdk,'Runtime',side_effect=AssertionError('runtime started')):
   with self.assertRaisesRegex(ValueError,'Add storage.private'):sdk.validate_project(p)
 def test_failed_scenario_keeps_a_private_local_diagnostic(self):
  scenario=self.root/'failure.json';scenario.write_text(json.dumps({'grants':['storage.private'],'steps':[{'action':'form.field.name','value':'Keep draft','expect':{'values.name':'different'}}]}))
  with self.assertRaises(sdk.ScenarioFailure) as failure:sdk.test_project(self.project,self.package,scenario)
  error=failure.exception;self.assertEqual(1,error.step);self.assertEqual('form.field.name',error.action);path=Path(error.diagnostic);data=json.loads(path.read_text());self.assertEqual('Keep draft',data['last_frame']['state']['values']['name']);self.assertNotIn('session',data);self.assertNotIn('token',data)
  if os.name!='nt':self.assertEqual(0o600,path.stat().st_mode&0o777)
 def test_logic_runner_edits_saves_and_reopens_without_native_simulation(self):
  out=io.StringIO();result=sdk.run_project(self.project,io.StringIO('form.field.name Local item\nform.save\nreopen\nstate\nquit\n'),output=out)
  self.assertEqual(2,result['events']);self.assertEqual(1,result['reopens']);self.assertFalse(result['native_ui_rendered']);frames=[json.loads(line) for line in out.getvalue().splitlines()];self.assertEqual('Local item',next(x['state']['values']['name'] for x in reversed(frames) if 'state'in x))
 def test_malformed_scenario_is_rejected_before_process_start(self):
  scenario=self.root/'bad-scenario.json';scenario.write_text(json.dumps({'steps':[{'action':'save','reopen':True}]}))
  with patch.object(sdk,'Runtime',side_effect=AssertionError('runtime started')):
   with self.assertRaisesRegex(ValueError,'choose action or reopen'):sdk.test_project(self.project,self.package,scenario)
 def test_validator_refuses_a_different_project_schema_implementation(self):
  p=self.root/'different-parser';sdk.new_project(p,'org.example.parserfork','Different parser','form');source=p/'vendor/developer/components/src/form_schema.rs';source.write_text(source.read_text()+'\n// changed local parser\n')
  with self.assertRaisesRegex(ValueError,'differs from this validator'):sdk.validate_project(p)
 def test_numeric_schema_persists_drafts_and_exposes_typed_data(self):
  p=self.root/'typed';sdk.new_project(p,'org.example.typed','Typed inputs','form');(p/'form.json').write_text(json.dumps({'fields':[{'id':'quantity','label':'Quantity','type':'integer','required':True},{'id':'day','label':'Day','type':'date'}]}));(p/'scenario.json').write_text(json.dumps({'grants':['storage.private'],'steps':[{'action':'form.field.quantity','value':'7'},{'action':'form.field.day','value':'2024-02-29'},{'action':'form.save','expect':{'status':'Saved','data.quantity':7}},{'reopen':True,'expect':{'values.quantity':'7','data.quantity':7,'data.day':'2024-02-29'}}]}))
  self.assertEqual(4,sdk.verify_project(p)['scenario']['passed'])
if __name__=='__main__':unittest.main(verbosity=2)
