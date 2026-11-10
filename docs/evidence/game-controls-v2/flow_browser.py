from pathlib import Path
from playwright.sync_api import sync_playwright
import http.server,threading,json
w=Path('/workspace/scratch/b5e698131b7f')
class Handler(http.server.SimpleHTTPRequestHandler):
 def __init__(self,*a,**k):super().__init__(*a,directory=str(w/'web'),**k)
 def log_message(self,*a):pass
s=http.server.ThreadingHTTPServer(('127.0.0.1',0),Handler);threading.Thread(target=s.serve_forever,daemon=True).start();records=[]
with sync_playwright()as p:
 b=p.chromium.launch(executable_path=str(w/'toolchain/chrome-headless-shell-linux64/chrome-headless-shell'),args=['--no-sandbox','--use-angle=swiftshader','--enable-unsafe-swiftshader','--disable-dev-shm-usage'])
 for width,height in [(320,560),(432,740)]:
  c=b.new_context(viewport={'width':width,'height':height},has_touch=True);page=c.new_page();errors=[];msgs=[]
  page.on('pageerror',lambda e:errors.append(str(e)));page.on('console',lambda m:msgs.append({'type':m.type,'text':m.text}))
  page.goto(f'http://127.0.0.1:{s.server_port}/index.html?game=hockey');page.wait_for_function('typeof window.gameProbe === "function"')
  def probe(x='state'):
   page.evaluate('(x)=>window.gameProbe(x)',x);page.wait_for_timeout(60);page.evaluate('window.gameProbe("state")');return page.evaluate('window.gameState')
  def act(x):page.evaluate('(x)=>window.onklavGameAction(x)',x);page.wait_for_timeout(60);return probe()
  assert probe()['buttons']==['Single player','Return to OnKlav']
  probe('start');page.touchscreen.tap(width/2,height-30);assert probe()['phase']==1
  a=act('pause');assert a['paused']and a['play_icon'] and not a['modal']and not a['chrome_dialog']
  a=act('pause');assert not a['paused']and not a['play_icon']
  for mode in ['audio','settings']:
   a=act(mode);assert a['paused']and not a['modal']and a['chrome_dialog']
   a=probe('done');assert not a['paused']and not a['modal']and not a['chrome_dialog']
  a=act('close');assert a['buttons']==['Resume','Quit']and a['colors']==['7ee9c8ff','ff6c8dff'];assert a['texts']==['Are you sure you want to quit? You will lose your progress.']
  page.screenshot(path=str(w/f'quit-{width}.png'));probe('left');assert not probe()['paused']
  probe('point');probe('serve');a=act('return');assert a['paused']and not a['chrome_dialog']and a['saved_scores']==[1,0]
  page.wait_for_timeout(350);page.reload();page.wait_for_function('typeof window.gameProbe === "function"');a=probe();assert a['buttons']==['New','Resume']and a['scores']==[1,0]
  page.screenshot(path=str(w/f'entry-{width}.png'));a=probe('left');assert a['buttons']==['Single player','Back']and a['saved_scores']==[1,0]
  probe('left');assert probe()['scores']==[0,0]and not probe()['paused']
  act('close');page.evaluate('window.gameProbe("right")');page.wait_for_timeout(450);assert page.evaluate('window.quit_cleared')is True
  assert any(m['text']=='ONKLAV_GAME_EXIT'for m in msgs)
  engine_errors=[m for m in msgs if m['type']=='error'or'ERROR:'in m['text']]
  records.append({'viewport':[width,height],'all_requested_flows':True,'persistent_nonzero_score_restored':True,'javascript_errors':errors,'engine_errors':engine_errors});assert not errors and not engine_errors,records[-1]
  c.close()
 b.close()
s.shutdown();(w/'browser-flows.json').write_text(json.dumps(records,indent=2));print('Both viewports passed direct toggles, silent options/Done, exact quit row, saved return, reload, New/mode selection and Quit clearing')
