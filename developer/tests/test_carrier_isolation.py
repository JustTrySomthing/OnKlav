"""Fresh core routing checks. No carrier sends or acceptance are simulated."""
import importlib.util,tempfile,unittest
from pathlib import Path
HERE=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('carrier_sdk',HERE/'onklav.py');sdk=importlib.util.module_from_spec(spec);spec.loader.exec_module(sdk)
CAPS=['storage.private','audio.record','audio.playback','contacts.pick','communications.walkie']
class CarrierIsolation(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.tmp=tempfile.TemporaryDirectory();cls.root=Path(cls.tmp.name);project=cls.root/'app';sdk.new_project(project,'org.example.privatevoice','Private voice','walkie');key=cls.root/'author.key';sdk.packer('keygen',key);cls.package=Path(sdk.build_project(project,key,cls.root/'external.onklav-a')['package']);cls.binary=sdk.build_host()
  import json
  manifest=project/'manifest.json';m=json.loads(manifest.read_text());m['id']='org.example.secondvoice';manifest.write_text(json.dumps(m));cls.package2=Path(sdk.build_project(project,key,cls.root/'second.onklav-a')['package'])
 @classmethod
 def tearDownClass(cls):cls.tmp.cleanup()
 def start(self,runtime,package,app_id,peer):
  review=runtime.request({'action':'stage','path':str(package)});runtime.request({'action':'install','hash':review['hash'],'grants':CAPS});token=runtime.request({'action':'launch','id':app_id})['session']
  self.event(runtime,token,'peer',peer);self.event(runtime,token,'prepare');return review,token
 def event(self,runtime,token,action,value=''):return runtime.request({'action':'event','session':token,'event':{'action':action,'value':value}})
 def test_external_private_voice_stays_local_and_cannot_use_builtin_carrier_routes(self):
  with tempfile.TemporaryDirectory()as tmp,sdk.Runtime(tmp,self.binary)as runtime:
   _,builtin=self.start(runtime,HERE.parent/'sdk/packages/WalkieTalkie.onklav-a','app.onklav.walkie','+15550101111')
   _,external=self.start(runtime,self.package,'org.example.privatevoice','+15550102222');before=runtime.request({'action':'walkie_carrier','args':{'operation':'state'}})
   self.event(runtime,external,'record.toggle');voice=runtime.request({'action':'host_voice','session':external,'widget':'capture'});self.assertEqual('+15550102222',voice['peer']);self.assertFalse(voice['live'])
   failure=runtime.request({'action':'voice_floor_request','session':external,'widget':'capture'},error=True);self.assertNotIn('not available to this app',str(failure))
   pending=self.event(runtime,external,'connect')['pending'];authorized=runtime.request({'action':'authorize','session':external,'token':pending['token']});self.assertEqual('org.example.privatevoice',authorized['owner']['id']);self.assertEqual('+15550102222',authorized['args']['peer']);self.assertEqual(before,runtime.request({'action':'walkie_carrier','args':{'operation':'state'}}))

 def test_builtin_authorization_supplies_signed_owner_and_canonical_recipient(self):
  with tempfile.TemporaryDirectory()as tmp,sdk.Runtime(tmp,self.binary)as runtime:
   review,token=self.start(runtime,HERE.parent/'sdk/packages/WalkieTalkie.onklav-a','app.onklav.walkie','+15550101111');pending=self.event(runtime,token,'connect')['pending']
   native=runtime.request({'action':'authorize','session':token,'token':pending['token'],'owner':{'id':'org.example.spoof'},'args':{'peer':'+15550999999'}})
   self.assertEqual('app.onklav.walkie',native['owner']['id']);self.assertEqual(review['manifest']['publisher'],native['owner']['publisher']);self.assertEqual('+15550101111',native['args']['peer']);self.assertEqual('begin',native['args']['operation'])
 def test_revocation_and_reinstall_never_reactivate_an_old_confirmation(self):
  with tempfile.TemporaryDirectory()as tmp,sdk.Runtime(tmp,self.binary)as runtime:
   package=HERE.parent/'sdk/packages/WalkieTalkie.onklav-a';_,token=self.start(runtime,package,'app.onklav.walkie','+15550101111');pending=self.event(runtime,token,'connect')['pending'];request={'action':'authorize','session':token,'token':pending['token']}
   runtime.request({'action':'permissions','id':'app.onklav.walkie','grants':['storage.private']});runtime.request(request,error=True)
   runtime.request({'action':'permissions','id':'app.onklav.walkie','grants':CAPS});runtime.request(request,error=True)
   runtime.request({'action':'remove','id':'app.onklav.walkie','delete_data':False});review=runtime.request({'action':'stage','path':str(package)});runtime.request({'action':'install','hash':review['hash'],'grants':CAPS});runtime.request(request,error=True)
 def test_two_apps_on_the_same_peer_have_separate_sessions_and_receipts(self):
  import hashlib
  with tempfile.TemporaryDirectory()as first,tempfile.TemporaryDirectory()as second,sdk.Runtime(first,self.binary)as a,sdk.Runtime(second,self.binary)as b:
   owners=[]
   for package,app in [(self.package,'org.example.privatevoice'),(self.package2,'org.example.secondvoice')]:
    review,_=self.start(a,package,app,'+15550100002');self.start(b,package,app,'+15550100001');owners.append({'id':app,'publisher':review['manifest']['publisher']})
   routes=a.request({'action':'walkie_routes'});self.assertEqual(2,len(routes));self.assertEqual(2,len({r['route']for r in routes}))
   for route in routes:self.assertEqual(hashlib.sha256(('onklav.carrier.v2\0'+route['id']+'\0'+route['publisher']).encode()).hexdigest()[:32],route['route'])
   def call(runtime,owner,op,checked_peer,sub,**extra):return runtime.request({'action':'walkie_carrier','owner':owner,'args':dict(extra,operation=op,facts={'mms_verified':True,'default_role':True,'send_sms':True,'receive_sms':True,'allowed':True,'peer':checked_peer,'subscription':sub})})
   def deliver(source,target,owner,sender,peer,send_sub,recv_sub):
    packet=call(source,owner,'next',peer,send_sub);self.assertIsInstance(packet,dict);call(target,owner,'receive',sender,recv_sub,peer=sender,wire=packet['wire'])
    callback=dict(packet['callback']);op=callback.pop('operation');callback.update(attempt=packet['attempt'],outcome='accepted');call(source,owner,op,peer,send_sub,**callback)
   own=owners[0];call(a,own,'begin','+15550100002',2)
   deliver(a,b,own,'+15550100001','+15550100002',2,3);deliver(b,a,own,'+15550100002','+15550100001',3,2);deliver(a,b,own,'+15550100001','+15550100002',2,3)
   invitation=call(b,own,'state','+15550100001',3);call(b,own,'accept','+15550100001',3,id=invitation['id'],peer='+15550100001');deliver(b,a,own,'+15550100002','+15550100001',3,2)
   self.assertEqual('connected',call(a,own,'state','+15550100002',2)['phase']);self.assertNotEqual('connected',call(a,owners[1],'state','+15550100002',2)['phase'])
   forged=dict(own,publisher='0'*64);a.request({'action':'walkie_carrier','owner':forged,'args':{'operation':'state'}},error=True)
   a.request({'action':'permissions','id':own['id'],'grants':['storage.private']});a.request({'action':'walkie_carrier','owner':own,'args':{'operation':'state'}},error=True);self.assertNotEqual('connected',call(a,owners[1],'state','+15550100002',2)['phase'])

 def test_voice_receipts_reconnect_and_removal_use_the_owning_app_scope(self):
  with tempfile.TemporaryDirectory()as first,tempfile.TemporaryDirectory()as second,sdk.Runtime(first,self.binary)as a,sdk.Runtime(second,self.binary)as b:
   review,token=self.start(a,self.package,'org.example.privatevoice','+15550100002');self.start(b,self.package,'org.example.privatevoice','+15550100001');owner={'id':'org.example.privatevoice','publisher':review['manifest']['publisher']}
   def facts(peer,sub):return {'mms_verified':True,'default_role':True,'send_sms':True,'receive_sms':True,'allowed':True,'peer':peer,'subscription':sub}
   def call(runtime,op,checked_peer,sub,**extra):return runtime.request({'action':'walkie_carrier','owner':owner,'args':dict(extra,operation=op,facts=facts(checked_peer,sub))})
   def deliver(source,target,sender,peer,send_sub,recv_sub):
    packet=call(source,'next',peer,send_sub);self.assertIsInstance(packet,dict);call(target,'receive',sender,recv_sub,peer=sender,wire=packet['wire']);callback=dict(packet['callback']);op=callback.pop('operation');callback.update(attempt=packet['attempt'],outcome='accepted');call(source,op,peer,send_sub,**callback)
   def connect():
    call(a,'begin','+15550100002',2);deliver(a,b,'+15550100001','+15550100002',2,3);deliver(b,a,'+15550100002','+15550100001',3,2);deliver(a,b,'+15550100001','+15550100002',2,3)
    incoming=call(b,'state','+15550100001',3);call(b,'accept','+15550100001',3,id=incoming['id'],peer='+15550100001');deliver(b,a,'+15550100002','+15550100001',3,2);return call(a,'state','+15550100002',2)['id']
   original=connect();a.request({'action':'walkie_refresh','session':token});a.request({'action':'voice_floor_request','session':token,'widget':'capture','facts':facts('+15550100002',2)})
   auth=a.request({'action':'host_voice','session':token,'widget':'capture'});audio=list(b'#!AMR\n'+(bytes([60])+bytes(31))*20);clip='e'*32
   a.request({'action':'voice_commit','session':token,'widget':'capture','key':auth['key'],'capture':clip,'mime':'audio/amr','bytes':audio,'send':True,'facts':facts('+15550100002',2)})
   packet=call(a,'clip.send','+15550100002',2,clip=clip);incoming={'peer':'+15550100001','text':packet['text'],'bytes':packet['bytes']}
   self.assertFalse(call(b,'clip.receive','+15550100001',3,**incoming)['duplicate']);self.assertTrue(call(b,'clip.receive','+15550100001',3,**incoming)['duplicate'])
   deliver(b,a,'+15550100002','+15550100001',3,2)
   rows=a.request({'action':'walkie_archive_list','owner':owner});self.assertEqual('delivered',next(row for row in rows if row['id']==clip)['state'])
   call(a,'end','+15550100002',2);call(b,'end','+15550100001',3)
   # Honor the production2-second automatic-reply throttle once; no retry loop.
   import time;time.sleep(2.05)
   replacement=connect();self.assertNotEqual(original,replacement)
   a.request({'action':'remove','id':owner['id'],'delete_data':True});self.assertFalse(any(row['id']==owner['id']for row in a.request({'action':'walkie_routes'})))
   a.request({'action':'walkie_carrier','owner':owner,'args':{'operation':'state'}},error=True)

if __name__=='__main__':unittest.main(verbosity=2)
