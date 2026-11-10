"""One bounded run: exact APK installation, UI regression and reference captures."""
from pathlib import Path
import hashlib,json,os,signal,subprocess,time,xml.etree.ElementTree as ET,re
b=Path('/workspace/scratch/39f240a7e5bd'); sdk=b/'android-sdk'; root=b/'project/OnKlav-Rewrite'
out=b/'tools/desktop-084';out.mkdir(exist_ok=True)
a=str(sdk/'platform-tools/adb'); serial='emulator-5586'; host=root/'android/app/build/outputs/apk/debug/app-debug.apk';test=root/'android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
result={'apk_sha256':hashlib.sha256(host.read_bytes()).hexdigest(),'device':'Android 10 x86_64 software emulator, 2 GiB, four cores; no KVM','ui_regression':'not_run','phone_acceptance':False,'captures':[],'reference_captures':[]}
def adb(*args,timeout=30):return subprocess.run([a,'-s',serial,*args],capture_output=True,timeout=timeout)
def screen(name):
 p=adb('exec-out','screencap','-p',timeout=30);assert p.stdout.startswith(b'\x89PNG');(out/(name+'.png')).write_bytes(p.stdout)
def xml(name):
 adb('shell','uiautomator','dump','/sdcard/qa.xml',timeout=20)
 p=adb('shell','cat','/sdcard/qa.xml');(out/(name+'.xml')).write_bytes(p.stdout);return ET.fromstring(p.stdout)
def taplabel(label,tree):
 nodes=[n for n in tree.iter('node') if n.get('text')==label or n.get('content-desc')==label]
 assert nodes,label
 box=list(map(int,re.findall(r'\d+',nodes[-1].get('bounds'))));adb('shell','input','tap',str((box[0]+box[2])//2),str((box[1]+box[3])//2))
def deadline(*_):raise TimeoutError('Bounded desktop runtime check exceeded twelve minutes; no automatic rerun')
signal.signal(signal.SIGALRM,deadline);signal.alarm(720)
env=os.environ.copy();env['ANDROID_SDK_ROOT']=str(sdk)
f=(out/'emulator.log').open('w');logcat=None;log=None
e=subprocess.Popen([str(sdk/'emulator/emulator'),'-avd','OnKlavRewriteCheck2','-read-only','-port','5586','-no-window','-gpu','swiftshader_indirect','-no-audio','-no-boot-anim','-no-accel','-memory','2048','-cores','4','-no-snapshot'],env=env,stdout=f,stderr=subprocess.STDOUT)
try:
 subprocess.run([a,'start-server'],check=True,timeout=20)
 for i in range(75):
  assert e.poll() is None,'Emulator exited'
  try:
   if adb('shell','getprop','sys.boot_completed',timeout=5).stdout.strip()==b'1':break
  except subprocess.TimeoutExpired:pass
  if i%10==0:print('Booting test Android',i,flush=True)
  time.sleep(3)
 else:raise TimeoutError('Android boot timed out')
 print('Android booted; installing host and UI tests',flush=True)
 for apk in [host,test]:
  p=adb('install','--no-streaming','-r',str(apk),timeout=180);print(p.stdout.decode(),flush=True);assert p.returncode==0 and b'Success' in p.stdout
 adb('shell','settings','put','global','window_animation_scale','0');adb('shell','settings','put','global','transition_animation_scale','0');adb('shell','settings','put','global','animator_duration_scale','0')
 # Bounded optional unlock; it must never abort the functional check.
 try:adb('shell','input','keyevent','82',timeout=5)
 except subprocess.TimeoutExpired:pass
 adb('logcat','-b','crash','-c')
 log=(out/'logcat.log').open('w');logcat=subprocess.Popen([a,'-s',serial,'logcat','-v','threadtime'],stdout=log,stderr=subprocess.STDOUT)
 path=adb('shell','pm','path','app.onklav.home.prototype.rewrite').stdout.decode().strip().removeprefix('package:');assert path.startswith('/data/app/') and path.endswith('/base.apk') and '\n' not in path
 result['installed_sha256']=adb('shell','sha256sum',path).stdout.decode().split()[0];result['exact_apk_installed']=result['installed_sha256']==result['apk_sha256']
 print('Testing sparse drag, edge paging, recreation, original controls and package manager routes',flush=True)
 p=adb('shell','am','instrument','-w','-r','-e','class','app.onklav.home.DesktopChromeDeviceTest','app.onklav.home.prototype.rewrite.test/androidx.test.runner.AndroidJUnitRunner',timeout=240)
 text=p.stdout.decode(errors='replace');(out/'instrumentation.log').write_text(text);print(text,flush=True)
 result['ui_regression']='pass' if p.returncode==0 and 'OK (1 test)' in text else 'fail'
 path=adb('shell','pm','path','app.onklav.home.prototype.rewrite').stdout.decode().strip().removeprefix('package:');assert path.startswith('/data/app/') and path.endswith('/base.apk') and '\n' not in path
 result['installed_sha256']=adb('shell','sha256sum',path).stdout.decode().split()[0];result['exact_apk_installed']=result['installed_sha256']==result['apk_sha256']
 for name in ['messages-header','apps-original-grid','apps-second-page','games-artwork','settings-original-icons','settings-games-manager','settings-sounds-menu']:
  p=adb('exec-out','run-as','app.onklav.home.prototype.rewrite','cat','cache/qa-'+name+'.png')
  if p.stdout.startswith(b'\x89PNG'):(out/(name+'.png')).write_bytes(p.stdout);result['captures'].append(name)
 (out/'crashes.log').write_bytes(adb('logcat','-d','-b','crash').stdout)
 screen('post-test-system')
 # The separately marked inspection copy retains the original DEX/resources/assets,
 # strips incompatible ARM libraries, and is never part of the product deliverables.
 print('Capturing the reference Apps, Games and Settings screens',flush=True)
 p=adb('install','--no-streaming','-r',str(b/'tools/original-appearance/ui-inspection-only.apk'),timeout=180)
 if p.returncode==0:
  for route in ['apps','games','settings','circles']:
   adb('shell','am','start','-n','com.charlessoude.basicmessages/.MainActivity','--es','navigate',route)
   time.sleep(2)
   name='original-'+route
   screen(name);result['reference_captures'].append(name)
   try:tree=xml(name)
   except Exception:continue
   if route=='settings':
    for label in ['Apps','Games','Sounds','Security','Storage','Quiet Hours','Walkie Talkie']:
     # Return to a fresh reference Settings page for each independent inspection.
     adb('shell','am','start','-n','com.charlessoude.basicmessages/.MainActivity','--es','navigate','settings');time.sleep(.5)
     tree=xml('original-settings-current')
     try:taplabel(label,tree)
     except AssertionError:continue
     time.sleep(.5);name='original-settings-'+label.lower().replace(' ','-');screen(name);result['reference_captures'].append(name)
     try:xml(name)
     except Exception:pass
except Exception as ex:
 result['error']=str(ex);print(type(ex).__name__,str(ex),flush=True)
finally:
 signal.alarm(0)
 if logcat:
  logcat.terminate()
  try:logcat.wait(timeout=5)
  except subprocess.TimeoutExpired:logcat.kill()
 if log:log.close()
 (out/'result.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result),flush=True)
 try:adb('emu','kill',timeout=5)
 except Exception:e.terminate()
 try:e.wait(timeout=10)
 except subprocess.TimeoutExpired:e.kill()
 f.close()
