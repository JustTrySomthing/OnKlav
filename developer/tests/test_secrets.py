"""Fresh broker authorization checks; never emulate an Android Keystore reply."""
import importlib.util,io,json,tempfile,unittest
from pathlib import Path
HERE=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('secrets_sdk',HERE/'onklav.py');sdk=importlib.util.module_from_spec(spec);spec.loader.exec_module(sdk)

class Secrets(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.tmp=tempfile.TemporaryDirectory(prefix='onklav-secrets-');cls.root=Path(cls.tmp.name);cls.project=cls.root/'app';sdk.new_project(cls.project,'org.example.secrets','Secrets',capabilities=['storage.secrets'])
  (cls.project/'src/lib.rs').write_text('''use onklav_ui::{Screen,button,secrets,serde_json::{json,Value}};
fn step(input:Value)->Value{let e=&input["event"];let mut state=input["state"].clone();if !state.is_object(){state=json!({"status":"Ready"});}let mut effect=Value::Null;match e["action"].as_str().unwrap_or(""){
"get"=>effect=secrets::get("token","done"),"set"=>effect=secrets::set("token","fixture-value","done"),"delete"=>effect=secrets::delete("token","done"),"invalid"=>effect=secrets::get("../escape","done"),"done"=>state["status"]=json!(if e["reply"]["ok"]==true{"Done"}else{"Blocked"}),_=>{}}
Screen::new("Secrets",state).body(vec![button("get","Read secret"),button("set","Store secret"),button("delete","Delete secret"),button("invalid","Invalid request")]).effect(effect).finish()}
onklav_ui::app!(step);
''');key=cls.root/'author.key';sdk.packer('keygen',key);cls.package=Path(sdk.build_project(cls.project,key,cls.root/'test.onklav-a')['package']);cls.binary=sdk.build_host()
 @classmethod
 def tearDownClass(cls):cls.tmp.cleanup()
 def install(self,runtime,grants):
  review=runtime.request({'action':'stage','path':str(self.package)});runtime.request({'action':'install','hash':review['hash'],'grants':grants});return review,runtime.request({'action':'launch','id':'org.example.secrets'})['session']
 def event(self,runtime,token,action,error=False):return runtime.request({'action':'event','session':token,'event':{'action':action}},error=error)
 def test_denial_and_revocation_never_authorize_secret_access(self):
  with tempfile.TemporaryDirectory() as root,sdk.Runtime(root,self.binary) as runtime:
   _,token=self.install(runtime,[]);denied=self.event(runtime,token,'get');self.assertNotIn('pending',denied);self.assertEqual('storage.secrets',denied['frame']['state']['sdk.permission.recovery']['capability'])
   runtime.request({'action':'permissions','id':'org.example.secrets','grants':['storage.secrets']});token=runtime.request({'action':'launch','id':'org.example.secrets'})['session'];pending=self.event(runtime,token,'set')['pending'];runtime.request({'action':'permissions','id':'org.example.secrets','grants':[]});runtime.request({'action':'authorize','session':token,'token':pending['token']},error=True)
 def test_native_request_owner_is_bound_to_signature_and_invalid_inputs_fail(self):
  with tempfile.TemporaryDirectory() as root,sdk.Runtime(root,self.binary) as runtime:
   review,token=self.install(runtime,['storage.secrets']);self.event(runtime,token,'invalid',error=True)
   # Start a fresh session after the invalid guest effect; no native work ran.
   token=runtime.request({'action':'launch','id':'org.example.secrets'})['session'];pending=self.event(runtime,token,'get')['pending'];native=runtime.request({'action':'authorize','session':token,'token':pending['token']});self.assertEqual('org.example.secrets',native['owner']['id']);self.assertEqual(review['manifest']['publisher'],native['owner']['publisher']);self.assertEqual({'key':'token'},native['args']);self.assertNotIn('path',native)
   runtime.request({'action':'reply','session':token,'token':pending['token'],'reply':{'ok':True,'value':{'path':'not a secret'}}},error=True)
   for action in ['set','delete']:
    fresh=runtime.request({'action':'launch','id':'org.example.secrets'})['session'];request=self.event(runtime,fresh,action)['pending'];authorized=runtime.request({'action':'authorize','session':fresh,'token':request['token']});self.assertEqual('secrets.'+action,authorized['service']);self.assertEqual(native['owner'],authorized['owner'])
 def test_diagnostics_and_runner_output_redact_sensitive_app_payloads(self):
  secret='must-never-appear';path=Path(sdk.failure_diagnostic(self.project,1,'action-'+secret,ValueError(secret),{'frame':{'state':{'secret':secret}},'pending':{'service':'secrets.set','args':{'value':secret}}},[{'value':secret}]))
  text=path.read_text();self.assertNotIn(secret,text);self.assertTrue(json.loads(text)['sensitive_payload_redacted'])
  out=io.StringIO();result=sdk.run_project(self.project,io.StringIO('state\ninvalid-action-'+secret+'\nset\nshow\nquit\n'),['storage.secrets'],out);self.assertNotIn(secret,out.getvalue());self.assertNotIn('fixture-value',out.getvalue());self.assertIn('redacted',out.getvalue());self.assertFalse(result['native_ui_rendered'])

if __name__=='__main__':unittest.main(verbosity=2)
