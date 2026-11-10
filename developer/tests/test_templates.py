"""One bounded compilation/startup pass for every public controller template."""
import importlib.util,json,tempfile,unittest
from pathlib import Path
HERE=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('sdk',HERE/'onklav.py');sdk=importlib.util.module_from_spec(spec);spec.loader.exec_module(sdk)
class Templates(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.tmp=tempfile.TemporaryDirectory(prefix='onklav-components-');cls.root=Path(cls.tmp.name);cls.key=cls.root/'author.key';sdk.packer('keygen',cls.key)
 @classmethod
 def tearDownClass(cls):cls.tmp.cleanup()
 def test_each_public_template_compiles_launches_and_runs_its_declared_scenario(self):
  for name in sdk.TEMPLATES:
   with self.subTest(template=name):
    p=self.root/name;sdk.new_project(p,'org.example.'+name,'Independent '+name,name);package=Path(sdk.build_project(p,self.key,None)['package']);sdk.test_project(p,package)
    if name in ('document','settings'):
     with tempfile.TemporaryDirectory() as data,sdk.Runtime(data) as rt:
      review=rt.request({'action':'stage','path':str(package)});rt.request({'action':'install','hash':review['hash'],'grants':['storage.private']});app=rt.request({'action':'launch','id':review['manifest']['id']});token=app['session']
      action='body' if name=='document' else 'show_details';value='draft' if name=='document' else 'false'
      out=rt.request({'action':'event','session':token,'event':{'action':action,'value':value}});self.assertTrue(out['frame']['state']['dirty'])
      out=rt.request({'action':'event','session':token,'event':{'action':'app.back'}});self.assertEqual('Discard changes?',out['frame']['confirm']['title'])
      out=rt.request({'action':'event','session':token,'event':{'action':'discard.cancel'}});self.assertTrue(out['frame']['state']['dirty']);self.assertFalse(out['frame'].get('close_requested',False))
    if name=='form':
     with tempfile.TemporaryDirectory() as data,sdk.Runtime(data) as rt:
      review=rt.request({'action':'stage','path':str(package)});rt.request({'action':'install','hash':review['hash'],'grants':['storage.private']});token=rt.request({'action':'launch','id':review['manifest']['id']})['session']
      out=rt.request({'action':'event','session':token,'event':{'action':'form.save'}});self.assertEqual('Name is required.',out['frame']['state']['status'])
      rt.request({'action':'event','session':token,'event':{'action':'form.field.name','value':'Kept draft'}})
      out=rt.request({'action':'event','session':token,'event':{'action':'form.back'}});self.assertEqual('Discard changes?',out['frame']['confirm']['title'])
      out=rt.request({'action':'event','session':token,'event':{'action':'form.cancel'}});self.assertEqual('Kept draft',out['frame']['state']['values']['name'])
    if name=='list':
     with tempfile.TemporaryDirectory() as data,sdk.Runtime(data) as rt:
      review=rt.request({'action':'stage','path':str(package)});rt.request({'action':'install','hash':review['hash'],'grants':['storage.private']});token=rt.request({'action':'launch','id':review['manifest']['id']})['session']
      def event(action,value=''):return rt.request({'action':'event','session':token,'event':{'action':action,'value':value}})
      event('new');event('title','Saved list item');event('save');event('delete');out=event('confirm.cancel');self.assertEqual('Saved list item',out['frame']['state']['doc']['title'])
      event('delete');out=event('confirm.accept');self.assertEqual(0,out['frame']['state']['total']);self.assertEqual('list',out['frame']['state']['route'])
    if name=='viewer':
     with tempfile.TemporaryDirectory() as data,sdk.Runtime(data) as rt:
      review=rt.request({'action':'stage','path':str(package)});rt.request({'action':'install','hash':review['hash'],'grants':['files.manage']});token=rt.request({'action':'launch','id':review['manifest']['id']})['session']
      out=rt.request({'action':'event','session':token,'event':{'action':'sdk.files.open'}});self.assertTrue(out['frame']['drawer_open'])
      items=out['frame']['state']['picker']['items'];self.assertTrue(any(i.get('folder') for i in items));self.assertFalse(any('uri' in i or 'path' in i for i in items))
      folder=next(i for i in items if i.get('folder') and i['id'] not in ('system','trash','external'))
      out=rt.request({'action':'event','session':token,'event':{'action':'sdk.files.entry.'+folder['id']}});self.assertEqual(folder['id'],out['frame']['state']['picker']['folder'])
      out=rt.request({'action':'event','session':token,'event':{'action':'sdk.files.cancel'}});self.assertIsNone(out['frame']['state']['picker'])
if __name__=='__main__':unittest.main(verbosity=2)
