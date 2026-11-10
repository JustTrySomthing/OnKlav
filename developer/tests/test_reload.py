"""One bounded real update session: no watcher, phone, release key or native replies."""
import importlib.util,io,json,tempfile,unittest
from pathlib import Path
from unittest.mock import patch
HERE=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('reload_sdk',HERE/'onklav.py');sdk=importlib.util.module_from_spec(spec);spec.loader.exec_module(sdk)

class Reload(unittest.TestCase):
 def test_reload_keeps_saved_data_skips_unchanged_and_preserves_session_on_build_errors(self):
  with tempfile.TemporaryDirectory(prefix='onklav-reload-') as tmp:
   project=Path(tmp)/'app';sdk.new_project(project,'org.example.reload','Reload','form');source=project/'src/lib.rs';schema=project/'form.json';manifest=project/'manifest.json';original_manifest=manifest.read_text();out=io.StringIO()
   def commands():
    yield 'form.field.name Saved item\n';yield 'form.save\n';yield 'form.field.name Unsaved draft\n'
    # Identical compiled output must not invalidate an unsaved local session.
    yield 'reload saved\n';yield 'state\n'
    definition=json.loads(schema.read_text());definition['fields'].append({'id':'extra','label':'Extra','default':'New field'});schema.write_text(json.dumps(definition))
    yield 'reload saved\n';yield 'state\n';yield 'form.field.name Retained through errors\n'
    good=source.read_text();source.write_text('this is deliberately invalid Rust')
    yield 'reload saved\n';yield 'state\n';source.write_text(good)
    # Adding a capability to a local update never grants it automatically.
    author=json.loads(manifest.read_text());author['capabilities'].append('share.text');manifest.write_text(json.dumps(author))
    yield 'form.save\n';yield 'reload saved\n';yield 'state\n'
    author['id']='org.example.different';manifest.write_text(json.dumps(author));yield 'reload saved\n';yield 'state\n';manifest.write_text(original_manifest)
    yield 'quit\n'
   result=sdk.run_project(project,commands(),output=out);frames=[json.loads(line) for line in out.getvalue().splitlines()];states=[f['state'] for f in frames if 'state' in f]
   self.assertEqual('Unsaved draft',states[0]['values']['name']);self.assertEqual('Saved item',states[1]['values']['name']);self.assertEqual('New field',states[1]['values']['extra']);self.assertEqual('Retained through errors',states[2]['values']['name'])
   self.assertEqual('Retained through errors',states[3]['values']['name']);self.assertEqual(states[3],states[4]);updates=[f for f in frames if f.get('reload')=='updated'];self.assertEqual([2,3],[f['local_version'] for f in updates]);self.assertIn('share.text',updates[-1]['ungranted_capabilities']);self.assertNotIn('share.text',updates[-1]['local_test_grants'])
   errors=[f for f in frames if f.get('reload')=='failed'];self.assertEqual(2,len(errors));self.assertTrue(all(f['active_session_kept'] for f in errors));self.assertIn('App identity changed',errors[-1]['error']);self.assertEqual(2,result['reloads']);self.assertEqual(1,result['unchanged_reloads']);self.assertFalse(result['native_ui_rendered']);self.assertEqual(original_manifest,manifest.read_text());self.assertFalse((project/'dist').exists())
 def test_invalid_grants_stop_before_build_or_signing(self):
  with tempfile.TemporaryDirectory() as tmp:
   project=Path(tmp)/'app';sdk.new_project(project,'org.example.grants','Grants')
   with patch.object(sdk,'packer',side_effect=AssertionError('signing started')):
    for grants in [['contacts.pick'],['android.context'],'storage.private']:
     with self.assertRaises(ValueError):sdk.run_project(project,io.StringIO('quit\n'),grants,io.StringIO())
 def test_failed_startup_recovers_through_a_new_update_and_keeps_saved_documents(self):
  with tempfile.TemporaryDirectory(prefix='onklav-startup-') as tmp:
   project=Path(tmp)/'app';sdk.new_project(project,'org.example.startup','Startup');source=project/'src/lib.rs';good=source.read_text();out=io.StringIO()
   def commands():
    yield 'body Saved before crash\n';yield 'save\n'
    source.write_text('use onklav_ui::serde_json::Value;fn step(_:Value)->Value{panic!("startup fixture")}onklav_ui::app!(step);')
    yield 'reload saved\n';yield 'state\n';yield 'reload saved\n'
    source.write_text(good);yield 'reload saved\n';yield 'state\n';yield 'quit\n'
   result=sdk.run_project(project,commands(),output=out);frames=[json.loads(line) for line in out.getvalue().splitlines()];failed=next(f for f in frames if f.get('reload')=='failed');self.assertFalse(failed['active_session_kept']);self.assertIn('did not open',failed['notice']);unchanged=next(f for f in frames if f.get('reload')=='unchanged');self.assertFalse(unchanged['active_session_kept']);self.assertIn('reopen',unchanged['notice']);state=next(f['state'] for f in frames if 'state' in f);self.assertEqual('Saved before crash',state['doc']['text']);self.assertEqual(1,result['reloads']);self.assertEqual(1,result['unchanged_reloads'])

if __name__=='__main__':unittest.main(verbosity=2)
