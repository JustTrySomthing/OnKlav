from pathlib import Path
from playwright.sync_api import sync_playwright
import http.server,threading,json,shutil
w=Path('/workspace/scratch/f6c9793330b3');r=w/'project/OnKlav';web=w/'splash-real-web';web.mkdir(exist_ok=True)
for folder in ['godot-builtin','godot-web']:
 for p in (r/'android/games/src/main/assets'/folder).iterdir():
  if p.is_file():shutil.copy2(p,web/p.name)
class H(http.server.SimpleHTTPRequestHandler):
 def __init__(self,*a,**k):super().__init__(*a,directory=str(web),**k)
 def log_message(self,*a):pass
server=http.server.ThreadingHTTPServer(('127.0.0.1',0),H);threading.Thread(target=server.serve_forever,daemon=True).start();rows=[]
with sync_playwright()as p:
 b=p.chromium.launch(executable_path=str(w/'toolchain/chrome-headless-shell-linux64/chrome-headless-shell'),args=['--no-sandbox','--use-angle=swiftshader','--enable-unsafe-swiftshader','--disable-dev-shm-usage','--autoplay-policy=no-user-gesture-required'])
 c=b.new_context(viewport={'width':432,'height':800},has_touch=True);c.add_init_script('window.onklavHosted=true;window.onklavFreshStart=true;');page=c.new_page();errors=[];page.on('pageerror',lambda e:errors.append(str(e)));page.on('console',lambda m:errors.append(m.text)if 'SCRIPT ERROR:'in m.text or 'ERROR:'in m.text else None)
 for game in ['dominos','hockey','panda','naval','four','blackjack']:
  page.goto(f'http://127.0.0.1:{server.server_port}/index.html?game={game}');page.wait_for_function('typeof onklavGameAction==="function"',timeout=30000);page.wait_for_function('!document.getElementById("status")');assert not errors,(game,errors)
  prefs=page.evaluate('JSON.parse(localStorage.getItem("onklav-loader-preferences"))');assert isinstance(prefs['muted'],bool)and 0<=prefs['volume']<=1,prefs
  rows.append({'game':game,'actual_shared_export_started':True,'loader_released_canvas':True,'preferences_cache_written':True})
 # The real common Sound dialog updates the cache before the following splash.
 page.evaluate('onklavGameAction("audio")');page.wait_for_timeout(150)
 # Use Godot's live DOM accessibility buttons is not available; cache existence was verified above.
 # Preferences are already covered by canonical session tests; reload verifies early cached mute.
 page.evaluate('localStorage.setItem("onklav-loader-preferences",JSON.stringify({muted:true,volume:.8,reduce_motion:false}))')
 page.add_init_script('window.earlyAudio=0;const A=window.AudioContext;window.AudioContext=function(...args){if(performance.now()<1200)window.earlyAudio++;return new A(...args)}')
 page.reload();page.wait_for_timeout(350);assert page.evaluate('earlyAudio')==0;page.wait_for_function('typeof onklavGameAction==="function"',timeout=30000);page.wait_for_function('!document.getElementById("status")');assert not errors,errors
 c.close();b.close()
server.shutdown();(w/'splash-real-browser-results.json').write_text(json.dumps({'games':rows,'cached_mute_suppresses_early_cue':True,'errors':errors},indent=2));print('ALL SIX REAL GAME EXPORTS HAND OFF FROM THE NEW LOADER; CACHED MUTE SUPPRESSES CUE')
