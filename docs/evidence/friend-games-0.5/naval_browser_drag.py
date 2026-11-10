from pathlib import Path
from playwright.sync_api import sync_playwright
import http.server,threading,json
w=Path('/workspace/scratch/b5e698131b7f')
class H(http.server.SimpleHTTPRequestHandler):
 def __init__(self,*a,**k):super().__init__(*a,directory=str(w/'naval-web-final'),**k)
 def log_message(self,*a):pass
server=http.server.ThreadingHTTPServer(('127.0.0.1',0),H);threading.Thread(target=server.serve_forever,daemon=True).start();rows=[]
with sync_playwright()as p:
 b=p.chromium.launch(executable_path=str(w/'toolchain/chrome-headless-shell-linux64/chrome-headless-shell'),args=['--no-sandbox','--use-angle=swiftshader','--enable-unsafe-swiftshader','--disable-dev-shm-usage','--autoplay-policy=no-user-gesture-required'])
 for width,height in [(320,560),(432,740)]:
  c=b.new_context(viewport={'width':width,'height':height},has_touch=True);c.add_init_script("window.onklavHosted=true;window.onklavFreshStart=!sessionStorage.getItem('opened');sessionStorage.setItem('opened','1')")
  page=c.new_page();errors=[];messages=[];page.on('pageerror',lambda e:errors.append(str(e)));page.on('console',lambda m:messages.append({'type':m.type,'text':m.text}))
  page.goto(f'http://127.0.0.1:{server.server_port}/index.html?game=naval');page.wait_for_function('typeof window.navalProbe==="function"');page.wait_for_timeout(200)
  def probe(op='state'):
   page.evaluate('(x)=>window.navalProbe(x)',op);page.wait_for_timeout(70);page.evaluate('window.navalProbe("state")');return page.evaluate('window.navalState')
  a=probe();assert a['phase']=='setup',a;print('SETUP',width,a,flush=True)
  page.screenshot(path=str(w/f'naval-setup-{width}.png'))
  # Place the carrier with a real touch, then use a random legal full fleet.
  x,y,gx,gy=a['grid'];cdp=c.new_cdp_session(page);dx,dy=a['dock'];tx,ty=x+gx*.05,y+gy*.05
  cdp.send('Input.dispatchTouchEvent',{'type':'touchStart','touchPoints':[{'x':dx,'y':dy}]})
  cdp.send('Input.dispatchTouchEvent',{'type':'touchMove','touchPoints':[{'x':tx,'y':ty}]});page.wait_for_timeout(80)
  cdp.send('Input.dispatchTouchEvent',{'type':'touchEnd','touchPoints':[]});page.wait_for_timeout(100);assert len(probe()['ships'][0])==5,probe()
  page.screenshot(path=str(w/f'naval-placed-{width}.png'))
  probe('random');a=probe();assert a['ready'];page.touchscreen.tap(*a['primary']);page.wait_for_timeout(150);a=probe();assert a['phase']=='battle',a
  x,y,gx,gy=a['grid'];page.touchscreen.tap(x+gx*.45,y+gy*.45);page.wait_for_timeout(100);a=probe();assert a['target']==44,a
  page.touchscreen.tap(*a['primary']);page.wait_for_timeout(1600);a=probe();assert len(a['shots'])==1 and a['turn'],a
  page.evaluate('window.onklavGameAction("pause")');page.wait_for_timeout(200);assert probe()['paused'];page.screenshot(path=str(w/f'naval-battle-{width}.png'))
  page.evaluate('window.onklavGameAction("return")');page.wait_for_timeout(400);page.reload();page.wait_for_function('typeof window.navalProbe==="function"');a=probe();assert a['buttons']==['New','Resume']and len(a['shots'])==1,a
  engine=[m for m in messages if m['type']=='error'or'ERROR:'in m['text']];assert not errors and not engine,(errors,engine)
  rows.append({'viewport':[width,height],'solo_fleet_target_fire_ai_pause_saved_return':True,'engine_errors':engine,'js_errors':errors});c.close()
 b.close()
server.shutdown();(w/'naval-browser-results.json').write_text(json.dumps(rows,indent=2));print('Naval phone flows passed')
