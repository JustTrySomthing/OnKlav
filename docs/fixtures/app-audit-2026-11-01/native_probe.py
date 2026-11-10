"""Exercise actual source-verified JNI without Android/carrier APIs."""
from pathlib import Path
import json, subprocess, tempfile, hashlib
ROOT=Path(__file__).resolve().parents[3]
proof=json.loads((ROOT/'docs/host-native-provenance.json').read_text())
for name,digest in proof['files_sha256'].items():assert hashlib.sha256((ROOT/name).read_bytes()).hexdigest()==digest,name
library=ROOT/'core/host-libs/linux-x86_64/libonklav_core.so'
assert hashlib.sha256(library.read_bytes()).hexdigest()==proof['host_library_sha256']
with tempfile.TemporaryDirectory(prefix='onklav-audit-jni-') as temp:
 folder=Path(temp);pkg=folder/'app/onklav/shared';pkg.mkdir(parents=True)
 source=pkg/'NativeCore.java'
 source.write_text('''package app.onklav.shared;
public class NativeCore {
 static {System.loadLibrary("onklav_core");}
 public static native String dispatch(String root,String request);
 public static void main(String[] args)throws Exception {
  var in=new java.io.BufferedReader(new java.io.InputStreamReader(System.in));
  String line;while((line=in.readLine())!=null){System.out.println(dispatch(args[0],line));System.out.flush();}
 }
}''')
 subprocess.run(['javac','-d',str(folder),str(source)],check=True,timeout=20)
 with subprocess.Popen(['java','-Djava.library.path='+str(library.parent),'-cp',str(folder),'app.onklav.shared.NativeCore',str(folder/'data')],stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True) as p:
  def call(action,**args):
   p.stdin.write(json.dumps(dict(op='chat',action=action,**args))+'\n');p.stdin.flush();r=json.loads(p.stdout.readline());assert r['ok'],r;return r['value']
  passed=0
  for i,initial in enumerate(['Inbox','Archive','Trash']):
   key=str(i+1);call('thread',key=key,folder=initial,pinned=True,muted=True)
   s=call('arrival',key=key,address='5551234567',at=2000)['threads'][key]
   assert s['folder']=='Inbox' and s['pinned'] and s['muted'] and s['unread'];passed+=1
  call('blocked',address='BANK',blocked=True)
  for i,initial in enumerate(['Inbox','Archive','Trash']):
   key=str(i+11);call('thread',key=key,folder=initial)
   s=call('arrival',key=key,address='BANK',at=2000)['threads'][key]
   assert s['folder']==('Archive' if initial=='Inbox' else initial);passed+=1
  call('thread',key='1',folder='Trash')
  assert call('arrival',key='1',address='5551234567',at=2000)['threads']['1']['folder']=='Trash';passed+=1
  call('blocked',address='DELIVERY',blocked=True);call('blocked',address='BANK',blocked=False)
  assert call('snapshot')['blocked']==['sender:delivery'];passed+=1
  call('draft',key='1',text='Retained draft 😀');assert call('snapshot')['drafts']['1']=='Retained draft 😀';passed+=1
  # Fresh distinct arrival after a backwards clock correction remains out of Inbox.
  call('thread',key='1',folder='Archive',unread=False,at=2001)
  s=call('arrival',key='1',address='5551234567',at=1000)['threads']['1']
  assert s['folder']=='Archive' and s['unread']==False
  print(json.dumps({'native_sha256':proof['host_library_sha256'],'functional_cases_passed':passed,'observed_B03':{'folder_after_fresh_arrival_with_clock_rollback':s['folder'],'unread':s['unread']},'carrier_calls':0}))
  p.stdin.close();p.wait(timeout=10)
