"""Upgrade an installed form through the real signed-package/storage broker."""
import importlib.util,json,tempfile,unittest
from pathlib import Path
HERE=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('migration_sdk',HERE/'onklav.py');sdk=importlib.util.module_from_spec(spec);spec.loader.exec_module(sdk)

class MigrationUpdate(unittest.TestCase):
 def test_signed_update_migrates_in_memory_and_preserves_saved_data_until_save(self):
  with tempfile.TemporaryDirectory(prefix='onklav-migration-') as tmp:
   root=Path(tmp);project=root/'form';key=root/'author.key';sdk.new_project(project,'org.example.migration','Migration','form')
   schema=project/'form.json';schema.write_text(json.dumps({'fields':[{'id':'old_name','label':'Name','required':True}]}))
   sdk.packer('keygen',key);old=Path(sdk.build_project(project,key,root/'old.onklav-a')['package']);binary=sdk.build_host()
   with sdk.Runtime(root/'data',binary) as runtime:
    def install(package):
     review=runtime.request({'action':'stage','path':str(package)});runtime.request({'action':'install','hash':review['hash'],'grants':['storage.private']})
    def launch():return runtime.request({'action':'launch','id':'org.example.migration'})
    def event(session,action,value=''):return runtime.request({'action':'event','session':session,'event':{'action':action,'value':value}})
    install(old);app=launch();event(app['session'],'form.field.old_name','User data');saved=event(app['session'],'form.save')['frame']['state']['doc'];self.assertIn('old_name',saved['text'])
    schema.write_text(json.dumps({'fields':[{'id':'name','label':'Name','required':True}]}));manifest=json.loads((project/'manifest.json').read_text());manifest['version']=2;(project/'manifest.json').write_text(json.dumps(manifest))
    (project/'src/lib.rs').write_text('''use onklav_ui::{forms::{form_with_migration,FormField},serde_json::Value};
fn upgrade(mut data:Value)->Result<Value,String>{let values=data.as_object_mut().ok_or("Expected a field map")?;if let Some(name)=values.remove("old_name"){values.insert("name".into(),name);}Ok(data)}
fn step(input:Value)->Value{form_with_migration("Migration",input,&[FormField::text("name","Name").required()],upgrade)}
onklav_ui::app!(step);
''')
    new=Path(sdk.build_project(project,key,root/'new.onklav-a')['package']);install(new);app=launch();state=app['frame']['state'];self.assertEqual('User data',state['values']['name']);self.assertTrue(state['dirty']);self.assertEqual(saved,state['doc'])
    app=launch();self.assertEqual(saved,app['frame']['state']['doc']);result=event(app['session'],'form.save');self.assertEqual('Saved',result['frame']['state']['status']);app=launch();self.assertFalse(app['frame']['state']['dirty']);self.assertEqual('User data',app['frame']['state']['values']['name']);self.assertNotIn('old_name',json.loads(app['frame']['state']['doc']['text']))

if __name__=='__main__':unittest.main(verbosity=2)
