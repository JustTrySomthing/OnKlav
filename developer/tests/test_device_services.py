"""Fresh broker checks with real file staging; no camera or Android delivery claim."""
import importlib.util,tempfile,unittest,json
from pathlib import Path
HERE=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('device_sdk',HERE/'onklav.py');sdk=importlib.util.module_from_spec(spec);spec.loader.exec_module(sdk)

class DeviceServices(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.tmp=tempfile.TemporaryDirectory(prefix='onklav-device-');cls.root=Path(cls.tmp.name);cls.project=cls.root/'app';sdk.new_project(cls.project,'org.example.devices','Devices',capabilities=['camera.capture','notifications.post'])
  (cls.project/'src/lib.rs').write_text('''use onklav_ui::{Screen,button,camera,notifications,serde_json::{json,Value}};
fn step(input:Value)->Value{let mut state=input["state"].clone();if !state.is_object(){state=json!({"status":"Ready","file":null});}let e=&input["event"];let mut effect=Value::Null;match e["action"].as_str().unwrap_or(""){
"capture"=>effect=camera::capture("captured"),"notice"=>effect=notifications::show("notice","Title","Body","done"),"schedule"=>effect=notifications::schedule("reminder","Title","Body",60,"done"),"bad_delay"=>effect=notifications::schedule("reminder","Title","Body",59,"done"),"cancel"=>effect=notifications::cancel("notice","done"),"bad"=>effect=notifications::show("../escape","Title","Body","done"),"captured" if e["reply"]["ok"]==true=>state["file"]=e["reply"]["value"].clone(),_=>{}}
Screen::new("Devices",state).body(vec![button("capture","Take photo"),button("notice","Show notification"),button("schedule","Schedule reminder"),button("bad_delay","Invalid delay"),button("cancel","Cancel notification"),button("bad","Invalid request")]).effect(effect).finish()}
onklav_ui::app!(step);
''');key=cls.root/'test.key';sdk.packer('keygen',key);cls.package=Path(sdk.build_project(cls.project,key,cls.root/'test.onklav-a')['package']);cls.binary=sdk.build_host()
 @classmethod
 def tearDownClass(cls):cls.tmp.cleanup()
 def start(self,runtime,grants):
  review=runtime.request({'action':'stage','path':str(self.package)});runtime.request({'action':'install','hash':review['hash'],'grants':grants});return review,runtime.request({'action':'launch','id':'org.example.devices'})['session']
 def event(self,runtime,token,action,error=False):return runtime.request({'action':'event','session':token,'event':{'action':action}},error=error)
 def test_camera_commit_requires_a_live_capture_token_and_returns_only_file_metadata(self):
  with tempfile.TemporaryDirectory() as tmp:
   root=Path(tmp)
   with sdk.Runtime(root,self.binary) as runtime:
    _,token=self.start(runtime,['camera.capture']);pending=self.event(runtime,token,'capture')['pending'];request={'action':'camera_host','session':token,'token':pending['token']};runtime.request(dict(request,token='forged'),error=True)
    runtime.request({'action':'reply','session':token,'token':pending['token'],'reply':{'ok':True,'value':{'id':'forged'}}},error=True)
    fixture=(HERE.parent/'docs/fixtures/annotation-base.jpg').read_bytes();(root/'content-import.tmp').write_bytes(fixture);entry=runtime.request(request);self.assertEqual(entry,runtime.request(request));self.assertNotIn('path',entry);self.assertNotIn('uri',entry)
    out=runtime.request({'action':'reply','session':token,'token':pending['token'],'reply':{'ok':True,'value':{'uri':'not allowed'}}});self.assertEqual(entry,out['frame']['state']['file'])
 def test_denial_and_revocation_block_both_device_services(self):
  with tempfile.TemporaryDirectory() as tmp,sdk.Runtime(tmp,self.binary) as runtime:
   _,token=self.start(runtime,[])
   for action,cap in [('capture','camera.capture'),('notice','notifications.post')]:
    out=self.event(runtime,token,action);self.assertNotIn('pending',out);self.assertEqual(cap,out['frame']['state']['sdk.permission.recovery']['capability']);self.event(runtime,token,'sdk.permission.dismiss')
   runtime.request({'action':'permissions','id':'org.example.devices','grants':['camera.capture','notifications.post']});token=runtime.request({'action':'launch','id':'org.example.devices'})['session'];pending=self.event(runtime,token,'capture')['pending'];runtime.request({'action':'permissions','id':'org.example.devices','grants':[]});runtime.request({'action':'camera_host','session':token,'token':pending['token']},error=True)
 def test_notifications_bind_native_identity_validate_arguments_and_recheck_grants(self):
  with tempfile.TemporaryDirectory() as tmp,sdk.Runtime(tmp,self.binary) as runtime:
   review,token=self.start(runtime,['notifications.post']);self.event(runtime,token,'bad',error=True);token=runtime.request({'action':'launch','id':'org.example.devices'})['session'];pending=self.event(runtime,token,'notice')['pending'];native=runtime.request({'action':'authorize','session':token,'token':pending['token']});self.assertEqual(review['manifest']['publisher'],native['owner']['publisher']);self.assertEqual('org.example.devices',native['owner']['id']);self.assertEqual('Devices',native['owner']['name'])
   runtime.request({'action':'reply','session':token,'token':pending['token'],'reply':{'ok':True,'value':{'path':'wrong reply'}}},error=True)
   runtime.request({'action':'permissions','id':'org.example.devices','grants':[]});runtime.request({'action':'authorize','session':token,'token':pending['token']},error=True)

 def test_scheduled_notifications_use_the_same_live_capability_and_strict_reply(self):
  with tempfile.TemporaryDirectory() as tmp,sdk.Runtime(tmp,self.binary) as runtime:
   review,token=self.start(runtime,['notifications.post']);self.event(runtime,token,'bad_delay',error=True)
   token=runtime.request({'action':'launch','id':'org.example.devices'})['session'];pending=self.event(runtime,token,'schedule')['pending']
   native=runtime.request({'action':'authorize','session':token,'token':pending['token']});self.assertEqual(60,native['args']['delay_seconds']);self.assertEqual(review['manifest']['publisher'],native['owner']['publisher'])
   runtime.request({'action':'reply','session':token,'token':pending['token'],'reply':{'ok':True,'value':{'posted':True}}},error=True)
   out=runtime.request({'action':'reply','session':token,'token':pending['token'],'reply':{'ok':True,'value':{'scheduled':True,'path':'discard'}}});self.assertNotIn('pending',out)
   pending=self.event(runtime,token,'schedule')['pending'];runtime.request({'action':'permissions','id':'org.example.devices','grants':[]});runtime.request({'action':'authorize','session':token,'token':pending['token']},error=True)

if __name__=='__main__' :unittest.main(verbosity=2)
