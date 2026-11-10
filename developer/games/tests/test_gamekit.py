import importlib.util,json,os,tempfile,unittest,zipfile
from pathlib import Path
SPEC=importlib.util.spec_from_file_location('gamekit',Path(__file__).resolve().parents[1]/'gamekit.py');g=importlib.util.module_from_spec(SPEC);SPEC.loader.exec_module(g)
class GameKitTests(unittest.TestCase):
 def setUp(self):
  self.temp=tempfile.TemporaryDirectory();self.root=Path(self.temp.name);self.key=self.root/'publisher.key';g.keygen(self.key)
  self.project=self.root/'game';g.create(self.project,'app.example.game','Example','touch2d');self.m=json.loads((self.project/'manifest.json').read_text());self.files={'web/index.html':b'<canvas/>','web/index.pck':b'GDPC-fixture'}
 def tearDown(self):self.temp.cleanup()
 def pack(self,name='game.onklav-g',files=None):
  out=self.root/name;g.package_files(self.m,self.files if files is None else files,out,self.key);return out
 def test_developer_can_sign_share_and_verify_without_owner_approval(self):
  r=g.verify(self.pack());self.assertTrue(r['signature_verified']);self.assertFalse(r['owner_approval_required'])
 def test_verifier_checks_target_host_without_changing_package(self):
  package=self.pack()
  self.assertTrue(g.verify(package,host_code=self.m['min_host'])['signature_verified'])
  with self.assertRaisesRegex(ValueError,'requires OnKlav build'):
   g.verify(package,host_code=self.m['min_host']-1)
 def test_host_path_limit_leaves_no_invalid_output(self):
  output=self.root/'too-long.onklav-g'
  with self.assertRaises(ValueError):
   self.pack(output.name,{**self.files,'web/'+('x'*197):b'content'})
  self.assertFalse(output.exists())
 def test_verify_against_retained_engine_after_sdk_moves_forward(self):
  from unittest.mock import patch
  package=self.pack()
  old=g.contract()
  future={**old,'version':'4.8'}
  with patch.object(g,'contract',return_value=future):
   with self.assertRaisesRegex(ValueError,'Unsupported shared engine'):
    g.verify(package)
   self.assertTrue(g.verify(package,engine_contract=old)['signature_verified'])
   before=(self.project/'manifest.json').read_bytes()
   with self.assertRaisesRegex(ValueError,'Engine fingerprint differs'):
    g.upgrade(self.project)
   self.assertEqual(before,(self.project/'manifest.json').read_bytes())
 def test_verify_engine_checks_bundled_bytes(self):
  directory=self.root/'bundle'
  directory.mkdir()
  payload=directory/'index.js'
  payload.write_bytes(b'engine')
  (directory/'engine.json').write_text(json.dumps({'runtime':'godot.web.v1','version':'4.7.2','files_sha256':{'index.js':g.digest(b'engine')}}))
  self.assertEqual(g.verify_engine(directory)['files_verified'],1)
  payload.write_bytes(b'different')
  with self.assertRaisesRegex(ValueError,'hash mismatch'):
   g.verify_engine(directory)
 def test_diagnose_checks_packed_resource_directory(self):
  import struct
  header=bytearray(40)
  header[:4]=b'GDPC'
  struct.pack_into('<I',header,4,4)
  struct.pack_into('<I',header,20,2)
  struct.pack_into('<Q',header,32,40)
  name=b'levels.json'
  data=bytes(header)+struct.pack('<II',1,len(name))+name+bytes(36)
  package=self.pack(files={**self.files,'web/index.pck':data})
  report=g.diagnose(package,host_code=174,required=['res://levels.json'])
  self.assertEqual(report['pck_resources'],1)
  with self.assertRaisesRegex(ValueError,'Missing exported resources'):
   g.diagnose(package,required=['res://missing.json'])
  with self.assertRaisesRegex(ValueError,'Invalid PCK resource entry'):
   g.packed_resource_names(data[:-1])
 def test_publisher_pinning_rejects_changed_identity(self):
  out=self.pack();r=g.verify(out);self.assertEqual(r,g.verify(out,r['publisher_sha256']))
  with self.assertRaises(ValueError):g.verify(out,'0'*64)
 def test_tampered_content_and_unsigned_member_are_rejected(self):
  p=self.pack()
  for member in ['web/index.pck','web/extra.txt']:
   target=self.root/('bad'+str(len(member))+'.zip')
   with zipfile.ZipFile(p)as old,zipfile.ZipFile(target,'w')as new:
    for n in old.namelist():new.writestr(n,b'changed'if n==member else old.read(n))
    if member not in old.namelist():new.writestr(member,b'unsigned')
   with self.assertRaises(ValueError):g.verify(target)
 def test_traversal_and_shared_engine_replacement_fail(self):
  for n in ['web/../secret','web/index.wasm']:
   with self.assertRaises(ValueError):self.pack(n.replace('/','_')+'.zip',{**self.files,n:b'bad'})
 def test_duplicate_archive_entries_fail(self):
  p=self.pack()
  with zipfile.ZipFile(p,'a')as z:z.writestr('web/index.pck',b'bad')
  with self.assertRaises(ValueError):g.verify(p)
 def test_invalid_manifest_fails_before_project_creation(self):
  p=self.root/'invalid'
  with self.assertRaises(ValueError):g.create(p,'../game','Bad','touch2d')
  self.assertFalse(p.exists())
 def test_never_overwrites_key_project_or_package(self):
  with self.assertRaises(FileExistsError):g.keygen(self.key)
  with self.assertRaises(ValueError):g.create(self.project,'app.example.other','Other','turn')
  p=self.pack();before=p.read_bytes()
  with self.assertRaises(ValueError):g.package_files(self.m,self.files,p,self.key)
  self.assertEqual(before,p.read_bytes())
 def test_all_templates_have_executable_projects_and_lifecycle_tests(self):
  for name in g.TEMPLATES:
   p=self.root/name;g.create(p,'app.example.'+name,'Example',name)
   self.assertTrue((p/'tests.gd').is_file());self.assertIn('variant/thread_support=false',(p/'export_presets.cfg').read_text())
 def test_engine_contract_is_required_and_no_native_permissions_allowed(self):
  for field,value in [('permissions',['camera']),('engine','0.0'),('engine_assets',{})]:
   m={**self.m,field:value}
   with self.assertRaises(ValueError):g.metadata(m)
 def test_upgrade_preserves_author_files_and_recovers_after_failed_write(self):
  from unittest.mock import patch
  authored=self.project/'main.gd';before=authored.read_bytes()
  preset=self.project/'export_presets.cfg';manifest=self.project/'manifest.json'
  old={manifest:manifest.read_bytes(),preset:preset.read_bytes(),self.project/g.TEMPLATE:b'old template'}
  (self.project/g.TEMPLATE).write_bytes(old[self.project/g.TEMPLATE])
  write=Path.write_bytes;failed=False
  def fault(p,data):
   nonlocal failed
   if p==manifest and not failed:
    failed=True;raise OSError('simulated failed commit')
   return write(p,data)
  with patch.object(Path,'write_bytes',fault):
   with self.assertRaises(OSError):g.upgrade(self.project)
  for p,data in old.items():self.assertEqual(data,p.read_bytes())
  self.assertEqual(before,authored.read_bytes())
  with self.assertRaises(ValueError):g.upgrade(self.project)
  # Successful independent project upgrade preserves identity/version/source/key.
  other=self.root/'other';g.create(other,'app.example.other','Other','turn')
  original=(other/'main.gd').read_bytes();identity=json.loads((other/'manifest.json').read_text())
  key_before=self.key.read_bytes();g.upgrade(other)
  self.assertEqual(identity,json.loads((other/'manifest.json').read_text()))
  self.assertEqual(original,(other/'main.gd').read_bytes());self.assertEqual(key_before,self.key.read_bytes())
  self.assertEqual(g.template_bytes(),(other/g.TEMPLATE).read_bytes())
  with self.assertRaises(ValueError):g.upgrade(other)

 def test_upgrade_adds_new_shared_components_and_keeps_custom_edits(self):
  helper=self.project/'save_migration.gd'
  helper.unlink()
  preset=self.project/'export_presets.cfg'
  before=preset.read_bytes()
  g.upgrade(self.project)
  self.assertEqual(helper.read_bytes(),(g.ROOT/'templates/shared'/helper.name).read_bytes())
  self.assertEqual(before,preset.read_bytes())
  other=self.root/'custom'
  g.create(other,'app.example.custom','Example','touch2d')
  custom=other/'mobile_layout.gd'
  custom.write_text('# studio layout')
  with self.assertRaisesRegex(ValueError,'Custom helper retained.*mobile_layout'):
   g.upgrade(other)
  self.assertEqual(custom.read_text(),'# studio layout')
  self.assertFalse((other/'.onklav-engine-backup').exists())
 def test_upgrade_keeps_other_export_presets_byte_for_byte(self):
  preset=self.project/'export_presets.cfg'
  other='\n[preset.1]\nname="Studio Desktop"\nexclude_filter="studio/*"\n\n[preset.1.options]\ncustom_template/release="studio.bin"\n'
  preset.write_text(preset.read_text()+other)
  g.upgrade(self.project)
  self.assertTrue(preset.read_text().endswith(other))

 def test_create_preserves_unicode_and_tokens_in_game_name(self):
  other=self.root/'localized'
  g.create(other,'app.example.local','Café GAME_ID TITLE','touch2d')
  self.assertIn('config/name="Café GAME_ID TITLE"',(other/'project.godot').read_text(encoding='utf-8'))

 def test_sms_modes_are_signed_and_require_turn_profile(self):
  p=self.root/'sms';g.create(p,'app.example.sms','SMS','turn',play_mode='turn_sms');m=json.loads((p/'manifest.json').read_text());self.assertEqual(m['play_modes'],['single','turn_sms']);self.assertEqual(m['min_host'],166)
  with self.assertRaises(ValueError):g.create(self.root/'badmode','app.example.bad','Bad','touch2d',play_mode='turn_sms')
  self.assertEqual(self.m['play_modes'],['single'])

 def test_action_templates_landscape_controls_and_safe_upgrade(self):
  for template in ['platformer','firstperson','topdown','arena']:
   path=self.root/template;g.create(path,'app.example.'+template,template,template)
   self.assertEqual(json.loads((path/'manifest.json').read_text())['orientation'],'landscape')
   self.assertIn('viewport_width=1000',(path/'project.godot').read_text())
   self.assertTrue((path/'touch_controls.gd').is_file())
   self.assertTrue((path/'gameplay_state.gd').is_file());self.assertTrue((path/'enemy_steering.gd').is_file())
  helper=self.project/'touch_controls.gd';helper.write_text('# custom authored controls')
  before=(self.project/'manifest.json').read_bytes()
  with self.assertRaises(ValueError):g.upgrade(self.project)
  self.assertEqual(helper.read_text(),'# custom authored controls');self.assertEqual(before,(self.project/'manifest.json').read_bytes())
 def test_new_template_rejects_unsupported_orientation_before_creation(self):
  path=self.root/'bad_orientation'
  with self.assertRaises(ValueError):g.create(path,'app.example.invalid','Invalid','platformer',orientation='diagonal')
  self.assertFalse(path.exists())

if __name__=='__main__':unittest.main()
