"""Real-broker denials and capability-guided generated projects."""
import importlib.util,json,tempfile,unittest
from pathlib import Path
from unittest.mock import patch
HERE=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('permission_sdk',HERE/'onklav.py');sdk=importlib.util.module_from_spec(spec);spec.loader.exec_module(sdk)

class PermissionRecovery(unittest.TestCase):
 def test_generated_feature_adds_live_file_viewer_and_rejects_missing_capability(self):
  with tempfile.TemporaryDirectory() as tmp:
   project=Path(tmp)/'app';sdk.new_project(project,'org.example.feature','Feature','form',features=['file-viewer'],capabilities=['share.text'])
   manifest=json.loads((project/'manifest.json').read_text());self.assertEqual(['files.manage','share.text','storage.private'],manifest['capabilities']);self.assertIn('files::with_viewer',(project/'src/lib.rs').read_text())
   manifest['capabilities'].remove('files.manage');(project/'manifest.json').write_text(json.dumps(manifest))
   with patch.object(sdk,'Runtime',side_effect=AssertionError('runtime started')):
    with self.assertRaisesRegex(ValueError,'files.manage'):sdk.validate_project(project)
   manifest['capabilities'].append('files.manage');(project/'manifest.json').write_text(json.dumps(manifest))
   self.assertEqual(6,sdk.verify_project(project)['scenario']['passed'])
 def test_unknown_feature_does_not_create_a_partial_project(self):
  with tempfile.TemporaryDirectory() as tmp:
   project=Path(tmp)/'app'
   with self.assertRaisesRegex(ValueError,'Unknown feature'):sdk.new_project(project,'org.example.invalidfeature','Invalid',features=['unknown'])
   self.assertFalse(project.exists())
 def test_existing_viewer_feature_is_not_wrapped_twice(self):
  with tempfile.TemporaryDirectory() as tmp:
   project=Path(tmp)/'app';sdk.new_project(project,'org.example.viewer','Viewer','viewer',features=['file-viewer'])
   self.assertEqual(1,(project/'src/lib.rs').read_text().count('files::with_viewer'))
 def test_denied_and_undeclared_services_keep_drafts_and_never_create_pending_work(self):
  with tempfile.TemporaryDirectory(prefix='onklav-permissions-') as tmp:
   root=Path(tmp);project=root/'app';sdk.new_project(project,'org.example.permissions','Permission test','form',features=['file-viewer'])
   # A custom app deliberately omits sharing to verify undeclared-service recovery.
   (project/'src/lib.rs').write_text('''use onklav_ui::{Screen,button,services,serde_json::{json,Value}};
fn step(input:Value)->Value{let mut state=input["state"].clone();if !state.is_object(){state=json!({"draft":"Keep this work"});}let action=input["event"]["action"].as_str().unwrap_or("");let effect=match action{"copy"=>services::clipboard_write(state["draft"].as_str().unwrap(),"copied"),"share"=>services::share("Draft",state["draft"].as_str().unwrap(),"shared"),_=>Value::Null};Screen::new("Permission test",state).body(vec![button("copy","Copy"),button("share","Share")]).effect(effect).finish()}
onklav_ui::app!(step);
''')
   manifest=json.loads((project/'manifest.json').read_text());manifest['capabilities'].append('clipboard.write');(project/'manifest.json').write_text(json.dumps(manifest))
   key=root/'author.key';sdk.packer('keygen',key);package=Path(sdk.build_project(project,key,None)['package']);binary=sdk.build_host()
   with sdk.Runtime(root/'data',binary) as runtime:
    review=runtime.request({'action':'stage','path':str(package)});runtime.request({'action':'install','hash':review['hash'],'grants':['storage.private','files.manage']});app=runtime.request({'action':'launch','id':'org.example.permissions'});token=app['session']
    def event(action):return runtime.request({'action':'event','session':token,'event':{'action':action}})
    denied=event('copy');self.assertNotIn('pending',denied);self.assertEqual('Keep this work',denied['frame']['state']['draft']);self.assertTrue(denied['frame']['menu_open']);self.assertIn('Settings > Apps',denied['frame']['menu'][0]['text']);self.assertEqual('clipboard.write',denied['frame']['state']['sdk.permission.recovery']['capability'])
    done=event('sdk.permission.dismiss');self.assertNotIn('sdk.permission.recovery',done['frame']['state']);self.assertNotIn('pending',done)
    undeclared=event('share');self.assertIn('app developer',undeclared['frame']['menu'][0]['text']);self.assertFalse(undeclared['frame']['state']['sdk.permission.recovery']['declared'])
    runtime.request({'action':'permissions','id':'org.example.permissions','grants':['storage.private','files.manage','clipboard.write']});runtime.request({'action':'event','session':token,'event':{'action':'copy'}},error=True)
    new=runtime.request({'action':'launch','id':'org.example.permissions'});allowed=runtime.request({'action':'event','session':new['session'],'event':{'action':'copy'}});self.assertEqual('clipboard.write',allowed['pending']['service'])

if __name__=='__main__':unittest.main(verbosity=2)
