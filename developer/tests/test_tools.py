"""Developer conveniences must use the real broker and preserve projects/keys."""
import importlib.util,json,shutil,tempfile,tomllib,unittest
from unittest.mock import patch
from pathlib import Path
HERE=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('sdk',HERE/'onklav.py');sdk=importlib.util.module_from_spec(spec);spec.loader.exec_module(sdk)
class Tools(unittest.TestCase):
 def test_portable_project_moves_and_verifies_without_author_key_setup(self):
  with tempfile.TemporaryDirectory(prefix='onklav-portable-') as root:
   root=Path(root);p=root/'original';sdk.new_project(p,'org.example.portable','Portable form','form');moved=root/'moved project 🐈';p.rename(moved)
   cargo=tomllib.loads((moved/'Cargo.toml').read_text());self.assertEqual('vendor/developer/components',cargo['dependencies']['onklav-ui']['path'])
   result=sdk.verify_project(moved);self.assertEqual(4,result['scenario']['passed']);self.assertFalse(result['shipping_package_created']);self.assertFalse((moved/'dist').exists());self.assertFalse(list(moved.rglob('*.key')))
 def test_catalog_covers_every_native_widget_and_shared_controller(self):
  items=sdk.catalog();widgets={i['id'] for i in items if i['kind']=='widget'};contract=json.loads((HERE/'contract.json').read_text());self.assertEqual(set(contract['widgets']),widgets)
  self.assertIn('media-viewer',{i['id'] for i in sdk.catalog('audio')});self.assertIn('form',{i['id'] for i in sdk.catalog('validation')})
 def test_invalid_author_identity_has_a_clear_error(self):
  for app_id,name in [(None,'App'),('org.example.valid',None),('app.onklav.bad','App')]:
   with self.assertRaises(ValueError):sdk.validate_identity(app_id,name)
 def test_scenario_paths_handle_arrays_and_missing_fields(self):
  self.assertEqual('x',sdk.path_value({'items':[{'id':'x'}]},'items.0.id'))
  with self.assertRaisesRegex(AssertionError,'Missing scenario path'):sdk.path_value({'items':[]},'items.0.id')
 def test_signed_minimum_and_icon_preflight_are_consistent(self):
  import struct,zlib
  def chunk(kind,data):return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data))
  png=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',1,1,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(b'\0\0\0\xff\xff'))+chunk(b'IEND',b'')
  self.assertEqual(png,sdk.validate_png(png))
  with self.assertRaises(ValueError):sdk.validate_png(png[:-1]+b'x')
  with self.assertRaises(ValueError):sdk.validate_png(png[:33]+chunk(b'iCCP',b'x')+png[33:])
  with tempfile.TemporaryDirectory() as root:
   p=Path(root)/'project';sdk.new_project(p,'org.example.metadata','Metadata');m=sdk.manifest(p);self.assertEqual(sdk.HOST_BUILD,m['min_host']);self.assertIn(m['icon']['symbol'],sdk.desktop_symbols());m['min_host']+=1;(p/'manifest.json').write_text(json.dumps(m))
   with self.assertRaisesRegex(ValueError,'Minimum host'):sdk.manifest(p)
 def test_portable_upgrade_keeps_a_backup_and_updates_the_lock(self):
  with tempfile.TemporaryDirectory() as root:
   p=Path(root)/'project';sdk.new_project(p,'org.example.upgrade','Upgrade')
   vendor=p/'vendor/developer/components/Cargo.toml';vendor.write_text(vendor.read_text().replace('0.4.0','0.3.0'))
   lock=p/'Cargo.lock';lock.write_text(lock.read_text().replace('name = "onklav-ui"\nversion = "0.4.0"','name = "onklav-ui"\nversion = "0.3.0"'))
   result=sdk.upgrade_project(p);self.assertIn('0.3.0',(Path(result['backup'])/'vendor/developer/components/Cargo.toml').read_text());self.assertIn('0.4.0',vendor.read_text());self.assertIn('name = "onklav-ui"\nversion = "0.4.0"',lock.read_text())
 def test_failed_upgrade_rolls_back_developer_files(self):
  with tempfile.TemporaryDirectory() as root:
   p=Path(root)/'project';sdk.new_project(p,'org.example.rollback','Rollback');vendor=p/'vendor/developer/components/src/lib.rs';vendor.write_text(vendor.read_text()+'\n// Developer change to preserve\n');before=vendor.read_bytes();lock=(p/'Cargo.lock').read_bytes()
   with patch.object(sdk,'execute',side_effect=ValueError('dependency unavailable')):
    with self.assertRaisesRegex(ValueError,'unavailable'):sdk.upgrade_project(p)
   self.assertEqual(before,vendor.read_bytes());self.assertEqual(lock,(p/'Cargo.lock').read_bytes())
if __name__=='__main__':unittest.main(verbosity=2)
