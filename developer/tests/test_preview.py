"""Native preview preflight and fresh broker snapshots; Android PNG rendering is tested separately."""
import importlib.util,json,tempfile,unittest
from pathlib import Path
from unittest.mock import patch
HERE=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('preview_sdk',HERE/'onklav.py');sdk=importlib.util.module_from_spec(spec);spec.loader.exec_module(sdk)
class Preview(unittest.TestCase):
 def test_real_form_events_produce_a_validated_snapshot_without_private_runtime_tokens(self):
  with tempfile.TemporaryDirectory()as tmp:
   project=Path(tmp)/'app';sdk.new_project(project,'org.example.preview','Preview','form')
   events=Path(tmp)/'events.json';events.write_text(json.dumps({'grants':['storage.private'],'steps':[{'action':'form.field.name','value':'Preview draft'},{'action':'form.save','expect':{'status':'Saved'}}]}))
   result=sdk.preview_snapshot(project,events);self.assertEqual(['storage.private'],result['local_test_grants']);self.assertIsNone(result['pending_service']);self.assertNotIn('state',result['frame']);self.assertNotIn('effect',result['frame']);self.assertNotIn('session',result)
   self.assertTrue(any(n.get('value')=='Preview draft'for n in result['frame']['body']));self.assertFalse((project/'dist').exists())
 def test_bad_preview_options_and_sensitive_apps_fail_before_compiling(self):
  with tempfile.TemporaryDirectory()as tmp:
   project=Path(tmp)/'app';sdk.new_project(project,'org.example.sensitivepreview','Preview','form',capabilities=['storage.secrets']);out=Path(tmp)/'preview.png'
   with patch.object(sdk,'packer',side_effect=AssertionError('signing started')):
    for options in [{'width':1},{'font_scale':float('nan')},{'font_scale':True},{'palette':100},{'dark':0},{}]:
     with self.assertRaises(ValueError):sdk.native_preview(project,out,**options)
    with self.assertRaisesRegex(ValueError,'sensitive'):sdk.preview_snapshot(project)
   out.write_text('Keep existing output')
   with self.assertRaisesRegex(ValueError,'never overwrite'):sdk.native_preview(project,out)
   self.assertEqual('Keep existing output',out.read_text())
 def test_preview_never_forges_a_pending_android_reply(self):
  with tempfile.TemporaryDirectory()as tmp:
   project=Path(tmp)/'app';sdk.new_project(project,'org.example.devicepreview','Preview','document',capabilities=['camera.capture']);source=project/'src/lib.rs'
   source.write_text('use onklav_ui::{Screen,button,camera,serde_json::{json,Value}};fn step(v:Value)->Value{let effect=if v["event"]["action"]=="capture"{camera::capture("done")}else{Value::Null};Screen::new("Capture",json!({})).body(vec![button("capture","Capture")]).effect(effect).finish()}onklav_ui::app!(step);')
   events=Path(tmp)/'events.json';events.write_text(json.dumps({'grants':['camera.capture'],'steps':[{'action':'capture'}]}));result=sdk.preview_snapshot(project,events);self.assertEqual('camera.capture',result['pending_service'])
   events.write_text(json.dumps({'grants':['camera.capture'],'steps':[{'action':'capture'},{'action':'capture'}]}))
   with self.assertRaisesRegex(ValueError,'No OS reply is simulated'):sdk.preview_snapshot(project,events)
if __name__=='__main__':unittest.main(verbosity=2)
