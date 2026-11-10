"""The text helpers must reach the actual permission and revision-checked broker."""
import importlib.util,json,tempfile,unittest
from pathlib import Path
HERE=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('files_sdk',HERE/'onklav.py');sdk=importlib.util.module_from_spec(spec);spec.loader.exec_module(sdk)
class FilesApi(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.tmp=tempfile.TemporaryDirectory(prefix='onklav-text-app-');cls.root=Path(cls.tmp.name);cls.project=cls.root/'app';cls.key=cls.root/'author.key'
  sdk.new_project(cls.project,'org.example.textapi','Text API');m=json.loads((cls.project/'manifest.json').read_text());m['capabilities']=['files.export','files.manage'];(cls.project/'manifest.json').write_text(json.dumps(m))
  cargo=cls.project/'Cargo.toml';cargo.write_text(cargo.read_text().replace('required_capabilities = ["storage.private"]','required_capabilities = ["files.export", "files.manage"]'))
  (cls.project/'src/lib.rs').write_text('''use onklav_ui::{Screen,button,label,files,services,serde_json::{json,Value}};
fn step(input:Value)->Value{let mut s=input["state"].clone();if !s.is_object(){s=json!({"file":{},"opened":{},"text":"","status":"Ready"});}let e=&input["event"];let a=e["action"].as_str().unwrap_or("");let mut effect=Value::Null;match a{
 "export"=>effect=services::export("Fixture","one\\r\\n","exported"),
 "exported" if e["reply"]["ok"]==true=>s["file"]=e["reply"]["value"].clone(),
 "read"=>effect=files::read_text(s["file"]["id"].as_str().unwrap_or(""),"loaded"),
 "loaded" if e["reply"]["ok"]==true=>{s["file"]=e["reply"]["value"].clone();s["opened"]=s["file"].clone();s["text"]=s["file"]["text"].clone();s["status"]=json!("Read");},
 "write"=>effect=files::write_text(&s["file"],"two\\n","written"),
 "stale"=>effect=files::write_text(&s["opened"],"stale\\n","written"),
 "written" if e["reply"]["ok"]==true=>{s["file"]=e["reply"]["value"].clone();s["text"]=s["file"]["text"].clone();s["status"]=json!("Saved");},_=>{}}
 if e["reply"]["ok"]==false{s["status"]=e["reply"]["error"].clone();}
 Screen::new("Text API",s.clone()).body(vec![button("export","Create text"),button("read","Read selected text"),button("write","Save text"),button("stale","Try stale save"),label("status",s["status"].as_str().unwrap_or(""))]).effect(effect).finish()}
onklav_ui::app!(step);
''')
  sdk.packer('keygen',cls.key);cls.package=Path(sdk.build_project(cls.project,cls.key,None)['package']);cls.binary=sdk.build_host()
 @classmethod
 def tearDownClass(cls):cls.tmp.cleanup()
 def start(self,rt,grants):
  review=rt.request({'action':'stage','path':str(self.package)});rt.request({'action':'install','hash':review['hash'],'grants':grants});return rt.request({'action':'launch','id':'org.example.textapi'})['session']
 def event(self,rt,token,action):return rt.request({'action':'event','session':token,'event':{'action':action}})
 def seed_file(self,rt,token):
  out=self.event(rt,token,'export');pending=out['pending'];self.assertEqual('files.export',pending['service'])
  saved=rt.request({'action':'library_host','session':token,'token':pending['token'],'operation':'save_text'})
  rt.request({'action':'reply','session':token,'token':pending['token'],'reply':{'ok':True,'value':saved}})
 def test_real_file_read_write_preserves_line_endings_and_rejects_stale_data(self):
  with tempfile.TemporaryDirectory() as root,sdk.Runtime(root,self.binary) as rt:
   token=self.start(rt,['files.export','files.manage']);self.seed_file(rt,token);out=self.event(rt,token,'read');self.assertEqual('one\r\n',out['frame']['state']['text']);self.assertNotIn('uri',out['frame']['state']['file']);self.assertNotIn('path',out['frame']['state']['file'])
   out=self.event(rt,token,'write');self.assertEqual('two\r\n',out['frame']['state']['text']);out=self.event(rt,token,'stale');self.assertIn('changed after',out['frame']['state']['status']);out=self.event(rt,token,'read');self.assertEqual('two\r\n',out['frame']['state']['text'])
   rt.request({'action':'permissions','id':'org.example.textapi','grants':['files.export']});rt.request({'action':'event','session':token,'event':{'action':'read'}},error=True)
 def test_missing_file_grant_prevents_read_and_write(self):
  with tempfile.TemporaryDirectory() as root,sdk.Runtime(root,self.binary) as rt:
   token=self.start(rt,['files.export']);self.seed_file(rt,token);out=self.event(rt,token,'read');self.assertIn('access is off',out['frame']['state']['status']);out=self.event(rt,token,'write');self.assertIn('access is off',out['frame']['state']['status'])
if __name__=='__main__':unittest.main(verbosity=2)
