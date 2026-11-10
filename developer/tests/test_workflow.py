"""Bounded acceptance of an independent author through the production Rust broker."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

HERE=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('onklav_tools',HERE/'onklav.py')
sdk=importlib.util.module_from_spec(spec);spec.loader.exec_module(sdk)

class DeveloperWorkflow(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp=tempfile.TemporaryDirectory(prefix='onklav-author-')
        cls.root=Path(cls.temp.name);cls.project=cls.root/'project';cls.key=cls.root/'private/publisher.key'
        cls.key.parent.mkdir();sdk.packer('keygen',cls.key)
        cls.other=cls.root/'private/other.key';sdk.packer('keygen',cls.other)
        sdk.new_project(cls.project,'org.example.notebook','Independent Notebook')
        cls.package=Path(sdk.build_project(cls.project,cls.key,None)['package'])
        cls.binary=sdk.build_host()
        cls.v2=cls.variant('org.example.notebook',2,cls.key,'v2')
        cls.bad_publisher=cls.variant('org.example.notebook',2,cls.other,'other-publisher')
        cls.isolated=cls.variant('org.example.otherapp',1,cls.key,'other-app')
    @classmethod
    def tearDownClass(cls):cls.temp.cleanup()
    @classmethod
    def variant(cls,app_id,version,key,label):
        m=json.loads((cls.project/'manifest.json').read_text());m.update(id=app_id,version=version)
        manifest=cls.root/(label+'.json');manifest.write_text(json.dumps(m))
        package=cls.root/(label+'.onklav-a')
        sdk.packer('pack',manifest,cls.project/'target/wasm32-unknown-unknown/release/onklav_starter_app.wasm',key,package)
        return package
    def setUp(self):
        self.data=tempfile.TemporaryDirectory(prefix='onklav-runtime-')
        self.runtime=sdk.Runtime(self.data.name,self.binary)
    def tearDown(self):self.runtime.close();self.data.cleanup()
    def install(self,package=None,grants=None):
        review=self.runtime.request({'action':'stage','path':str(package or self.package)})
        return self.runtime.request({'action':'install','hash':review['hash'],'grants':['storage.private'] if grants is None else grants})
    def launch(self,app_id='org.example.notebook'):return self.runtime.request({'action':'launch','id':app_id})
    def event(self,session,action,value='',error=False):return self.runtime.request({'action':'event','session':session,'event':{'action':action,'value':value}},error=error)
    def save(self,text):
        app=self.launch();session=app['session'];self.event(session,'body',text);out=self.event(session,'save');self.assertEqual('Saved',out['frame']['state']['status']);return session
    def test_01_generated_project_builds_signed_package_and_runs_its_scenario(self):
        result=sdk.test_project(self.project,self.package)
        self.assertEqual(4,result['passed']);self.assertFalse(result['android_ui_tested'])
    def test_02_denial_and_grant_revocation_are_live(self):
        self.install(grants=[]);app=self.launch();self.assertIn('access is off',app['frame']['state']['status'])
        self.runtime.request({'action':'permissions','id':'org.example.notebook','grants':['storage.private']})
        self.event(app['session'],'save',error=True)
        session=self.save('private')
        self.runtime.request({'action':'permissions','id':'org.example.notebook','grants':[]})
        self.event(session,'save',error=True)
        self.assertIn('access is off',self.launch()['frame']['state']['status'])
    def test_03_signed_update_preserves_data_invalidates_session_and_rejects_downgrade(self):
        self.install();session=self.save('survives update');self.install(self.v2)
        self.event(session,'save',error=True)
        self.assertEqual('survives update',self.launch()['frame']['state']['doc']['text'])
        self.runtime.request({'action':'stage','path':str(self.package)},error=True)
        rows=self.runtime.request({'action':'list'});self.assertEqual(2,rows[0]['manifest']['version'])
    def test_04_different_publisher_cannot_take_over_identity(self):
        self.install();self.runtime.request({'action':'stage','path':str(self.bad_publisher)},error=True)
        self.assertEqual(1,self.runtime.request({'action':'list'})[0]['manifest']['version'])
        self.runtime.request({'action':'remove','id':'org.example.notebook','delete_data':True})
        self.runtime.request({'action':'stage','path':str(self.bad_publisher)},error=True)
    def test_05_same_author_other_id_cannot_read_private_documents(self):
        self.install();self.save('first app secret');self.install(self.isolated)
        self.assertEqual('',self.launch('org.example.otherapp')['frame']['state']['doc']['text'])
        self.assertEqual('first app secret',self.launch()['frame']['state']['doc']['text'])
    def test_06_remove_reinstall_retains_or_deletes_only_as_requested(self):
        self.install();session=self.save('keep me')
        self.runtime.request({'action':'remove','id':'org.example.notebook','delete_data':False})
        self.event(session,'save',error=True);self.install()
        self.assertEqual('keep me',self.launch()['frame']['state']['doc']['text'])
        self.runtime.request({'action':'remove','id':'org.example.notebook','delete_data':True});self.install()
        self.assertEqual('',self.launch()['frame']['state']['doc']['text'])
    def test_07_tampered_wasm_and_stale_review_do_not_install(self):
        damaged=Path(self.data.name)/'tampered.onklav-a'
        with zipfile.ZipFile(self.package) as src,zipfile.ZipFile(damaged,'w') as dest:
            for name in src.namelist():dest.writestr(name,src.read(name)+(b'X' if name=='main.wasm' else b''))
        self.runtime.request({'action':'stage','path':str(damaged)},error=True)
        self.assertEqual([],self.runtime.request({'action':'list'}))
        review=self.runtime.request({'action':'stage','path':str(self.package)})
        self.runtime.request({'action':'stage','path':str(self.isolated)})
        self.runtime.request({'action':'install','hash':review['hash'],'grants':[]},error=True)
        self.assertEqual([],self.runtime.request({'action':'list'}))
    def test_08_tools_preserve_projects_outputs_and_private_keys(self):
        with self.assertRaises(ValueError):sdk.new_project(self.project,'org.example.new','Do not replace')
        before=self.package.read_bytes()
        with self.assertRaises(ValueError):sdk.build_project(self.project,self.key,self.package)
        self.assertEqual(before,self.package.read_bytes())
        with self.assertRaises(ValueError):sdk.validate_identity('invalid-id','Bad')
        with self.assertRaises(ValueError):sdk.validate_identity('org.example.good','Bad\nName')
    def test_09_saved_data_survives_a_fresh_runtime_process(self):
        self.install();self.save('survives process restart');self.runtime.close()
        self.runtime=sdk.Runtime(self.data.name,self.binary)
        self.assertEqual('survives process restart',self.launch()['frame']['state']['doc']['text'])

if __name__=='__main__':unittest.main(verbosity=2)
