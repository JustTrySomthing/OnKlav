from pathlib import Path
import shutil,zipfile,threading,http.server,json
from playwright.sync_api import sync_playwright
w=Path('/workspace/scratch/b5e698131b7f');r=w/'recovered/OnKlav';folder=w/'chrome-all-games-smoke';folder.mkdir(exist_ok=True);shared=r/'android/games/src/main/assets/godot-web';bundle=r/'android/games/src/main/assets/godot-builtin'
shutil.copytree(bundle,folder/'builtin',dirs_exist_ok=True)
for template in ['touch2d','scene3d','turn']:
 d=folder/template;d.mkdir(exist_ok=True)
 with zipfile.ZipFile(w/('chrome-sdk-'+template+'.onklav-g'))as z:
  for n in z.namelist():
   if n.startswith('web/'):(d/n[4:]).write_bytes(z.read(n))
for d in folder.iterdir():
 for p in shared.iterdir():shutil.copy2(p,d/p.name)
class Handler(http.server.SimpleHTTPRequestHandler):
 def __init__(self,*a,**k):super().__init__(*a,directory=str(folder),**k)
 def log_message(self,*a):pass
server=http.server.ThreadingHTTPServer(('127.0.0.1',0),Handler);threading.Thread(target=server.serve_forever,daemon=True).start();records=[]
with sync_playwright()as p:
 b=p.chromium.launch(executable_path=str(w/'toolchain/chrome-headless-shell-linux64/chrome-headless-shell'),args=['--no-sandbox','--use-angle=swiftshader','--enable-unsafe-swiftshader','--disable-dev-shm-usage'])
 for path in ['builtin/index.html?game='+n for n in ['hockey','four','naval','blackjack']]+[n+'/index.html'for n in ['touch2d','scene3d','turn']]:
  c=b.new_context(viewport={'width':432,'height':740});page=c.new_page();errors=[];messages=[]
  page.on('pageerror',lambda e:errors.append(str(e)));page.on('console',lambda m:messages.append({'type':m.type,'text':m.text}))
  page.goto(f'http://127.0.0.1:{server.server_port}/'+path);page.wait_for_function('document.getElementById("status") === null',timeout=30000);page.wait_for_timeout(500)
  assert page.evaluate('typeof window.hockeyProbe')=='undefined'
  versions=[m['text']for m in messages if m['text'].startswith('Godot Engine')]
  engine_errors=[m for m in messages if m['type']=='error' or 'ERROR:'in m['text']]
  rec={'game':path,'version':versions,'javascript_errors':errors,'engine_errors':engine_errors,'private_probe_absent':True};records.append(rec)
  assert versions and '.onklav1.'in versions[0] and not errors and not engine_errors,rec
  c.close()
 b.close()
server.shutdown();(w/'session-chrome/all-games-smoke.json').write_text(json.dumps(records,indent=2));print('All four production games and three signed SDK templates launched with the same corrected engine; no private probe or JS/engine errors')
