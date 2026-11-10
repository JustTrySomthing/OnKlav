from pathlib import Path
import http.server,threading,json
from playwright.sync_api import sync_playwright
w=Path('/workspace/scratch/b5e698131b7f');folder=w/'chrome-final-fixture-export'
class Handler(http.server.SimpleHTTPRequestHandler):
 def __init__(self,*a,**k):super().__init__(*a,directory=str(folder),**k)
 def log_message(self,*a):pass
server=http.server.ThreadingHTTPServer(('127.0.0.1',0),Handler);threading.Thread(target=server.serve_forever,daemon=True).start();records=[]
with sync_playwright()as p:
 b=p.chromium.launch(executable_path=str(w/'toolchain/chrome-headless-shell-linux64/chrome-headless-shell'),args=['--no-sandbox','--use-angle=swiftshader','--enable-unsafe-swiftshader','--disable-dev-shm-usage','--autoplay-policy=no-user-gesture-required'])
 for width,height in [(432,740),(320,560)]:
  c=b.new_context(viewport={'width':width,'height':height},has_touch=True);page=c.new_page();errors=[];messages=[]
  page.on('pageerror',lambda e:errors.append(str(e)));page.on('console',lambda m:messages.append({'type':m.type,'text':m.text}))
  page.goto(f'http://127.0.0.1:{server.server_port}/index.html?game=hockey');page.wait_for_function('typeof window.hockeyProbe === "function"')
  def probe(x):page.evaluate('(x)=>window.hockeyProbe(x)',x);return page.evaluate('window.hockeyState')
  def act(x):page.evaluate('(x)=>window.onklavGameAction(x)',x)
  page.screenshot(path=str(w/f'chrome-ready-{width}.png'))
  page.touchscreen.tap(width/2,height-30);page.wait_for_timeout(150);assert probe('state')['phase']==1
  act('audio');page.wait_for_timeout(150);assert probe('state')['paused'];page.screenshot(path=str(w/f'chrome-audio-{width}.png'))
  # Pause action dismisses audio and leaves the usable resume menu.
  act('pause');probe('resume');page.wait_for_timeout(450);assert not probe('state')['paused']
  act('return');assert probe('state')['paused'];page.wait_for_timeout(400)
  assert any(m['text']=='ONKLAV_GAME_RETURN'for m in messages)
  before=probe('state');page.reload();page.wait_for_function('typeof window.hockeyProbe === "function"');after=probe('state')
  assert after['paused'] and after['phase']==before['phase'] and after['scores']==before['scores']
  page.screenshot(path=str(w/f'chrome-restored-{width}.png'))
  probe('resume');page.wait_for_timeout(450);assert not probe('state')['paused']
  act('host_pause');assert probe('state')['paused'];act('host_resume');assert probe('state')['paused']
  probe('restart');page.wait_for_timeout(450);assert probe('state')['scores']==[0,0] and probe('state')['phase']==0
  page.touchscreen.tap(width/2,height-30);assert probe('state')['phase']==1
  probe('quit');page.wait_for_timeout(500)
  engine_errors=[m for m in messages if m['type']=='error' or 'ERROR:'in m['text']]
  rec={'viewport':[width,height],'audio_pause_return_reload_resume_new_game':True,'return_ack':True,'restored_scores':after['scores'],'javascript_errors':errors,'engine_errors':engine_errors};records.append(rec);assert not errors and not engine_errors,rec
  c.close()
 b.close()
server.shutdown();(w/'session-chrome/browser-final.json').write_text(json.dumps(records,indent=2));print('Both phone layouts passed audio/pause/return/actual persistent reload/resume/new-game/serve and clean exit')
