from pathlib import Path
from playwright.sync_api import sync_playwright
import http.server,threading,json,math
w=Path('/workspace/scratch/f6c9793330b3');out=w/'dominos-qa';out.mkdir(exist_ok=True)
class H(http.server.SimpleHTTPRequestHandler):
 def __init__(self,*a,**k):super().__init__(*a,directory=str(w/'dominos-web'),**k)
 def log_message(self,*a):pass
server=http.server.ThreadingHTTPServer(('127.0.0.1',0),H);threading.Thread(target=server.serve_forever,daemon=True).start()
AUDIO="""window.__meters=[];const original=AudioNode.prototype.connect;AudioNode.prototype.connect=function(dest,...args){if(dest instanceof AudioDestinationNode){const m=this.context.createAnalyser();m.fftSize=1024;original.call(this,m,...args);original.call(m,dest);__meters.push(m);return dest}return original.call(this,dest,...args)};window.__rms=()=>Math.max(0,...__meters.map(m=>{const a=new Float32Array(m.fftSize);m.getFloatTimeDomainData(a);return Math.sqrt(a.reduce((s,v)=>s+v*v,0)/a.length)}));"""
with sync_playwright()as p:
 browser=p.chromium.launch(executable_path=str(w/'toolchain/chrome-headless-shell-linux64/chrome-headless-shell'),args=['--no-sandbox','--use-angle=swiftshader','--enable-unsafe-swiftshader','--disable-dev-shm-usage','--autoplay-policy=no-user-gesture-required']);rows=[]
 for width,height in [(320,640),(432,800)]:
  c=browser.new_context(viewport={'width':width,'height':height},has_touch=True);c.add_init_script("window.onklavHosted=true;window.onklavFreshStart=!sessionStorage.getItem('opened');sessionStorage.setItem('opened','1')");c.add_init_script(AUDIO);page=c.new_page();errors=[];page.on('pageerror',lambda e:errors.append(str(e)));page.on('console',lambda m:errors.append(m.text)if m.type=='error'or'ERROR:'in m.text else None);page.goto(f'http://127.0.0.1:{server.server_port}/index.html?game=dominos');page.wait_for_function('typeof dominosProbe==="function"')
  def state(op='state'):
   page.evaluate('(op)=>dominosProbe(op)',op);page.wait_for_timeout(35);return page.evaluate('dominosState')
  def click(label):
   a=state();b=next(v for v in a['controls']if v['text']==label and v['visible']);assert b['enabled'],(label,a);page.touchscreen.tap(*b['point']);page.wait_for_timeout(60)
  def done():
   a=state();b=next(v for v in a['dialogs']if v['text']=='Done');page.touchscreen.tap(*b['point']);page.wait_for_timeout(120)
  page.wait_for_timeout(350);a=state('opening');page.wait_for_timeout(150);a=state();page.screenshot(path=str(out/f'opening-{width}.png'))
  assert a['model']['phase']=='playing'and len(a['tiles'])==7,a
  required=a['model']['required'];tile=next(t for t in a['tiles']if t['id']==required)
  # Scroll the real rack when the required tile is outside the viewport.
  if not 24<tile['point'][0]<width-24:
   cdp=c.new_cdp_session(page);cdp.send('Input.dispatchTouchEvent',{'type':'touchStart','touchPoints':[{'x':width-35,'y':tile['point'][1]}]});cdp.send('Input.dispatchTouchEvent',{'type':'touchMove','touchPoints':[{'x':30,'y':tile['point'][1]}]});cdp.send('Input.dispatchTouchEvent',{'type':'touchEnd','touchPoints':[]});page.wait_for_timeout(450);a=state();tile=next(t for t in a['tiles']if t['id']==required)
  assert 0<tile['point'][0]<width,tile
  page.touchscreen.tap(*tile['point']);page.wait_for_timeout(120);a=state();assert a['selected']==required,a
  page.touchscreen.tap(*a['endpoints'][0]);page.wait_for_timeout(100);a=state();assert len(a['model']['chain'])==1 and a['flying'],a
  page.evaluate('onklavGameAction("audio")');page.wait_for_timeout(100);a=state();held=a['snapshot'];age=a['flying']['age'];assert a['paused']and a['music_paused'];page.wait_for_timeout(160);assert state()['snapshot']==held and state()['flying']['age']==age
  page.evaluate('onklavGameAction("host_state")');done();assert not state()['paused'];page.wait_for_timeout(650);assert not state()['flying'];page.screenshot(path=str(out/f'connection-{width}.png'))
  page.evaluate('onklavGameAction("pause")');page.evaluate('onklavGameAction("settings")');page.wait_for_timeout(100);done();assert state()['paused'];page.evaluate('onklavGameAction("pause")')
  state('draw');a=state();assert a['model']['turn']==0 and not any(t['playable']for t in a['tiles']);click(next(v['text']for v in a['controls']if v['text'].startswith('Draw')));page.wait_for_timeout(120);a=state();assert a['flying']['kind']=='draw';page.wait_for_timeout(650);assert not any(t['concealed']for t in state()['tiles'])
  state('long');page.wait_for_timeout(150);click('Zoom');page.wait_for_timeout(180);a=state();assert a['zoomed'];page.screenshot(path=str(out/f'zoom-{width}.png'));click('Back to hand');assert not state()['zoomed']
  state('round');page.wait_for_timeout(400);a=state();assert a['model']['phase']=='round_over';page.screenshot(path=str(out/f'round-{width}.png'));click('Next round');page.wait_for_timeout(1400);assert state()['model']['round']==a['model']['round']+1
  a=state();page.evaluate('onklavGameAction("return")');page.wait_for_timeout(1500);saved=state()['snapshot'];page.reload();page.wait_for_function('typeof dominosProbe==="function"');a=state();assert a['paused'] and a['snapshot']==saved,a;b=next(x for x in a['dialogs']if x['text']=='Resume');page.touchscreen.tap(*b['point']);page.wait_for_timeout(120);assert not state()['paused']
  music=0
  for sample in range(10):music=max(music,page.evaluate('__rms()'));page.wait_for_timeout(100)
  assert music>.0001,music
  state('finish');page.wait_for_timeout(600);a=state();assert a['model']['phase']=='match_over'and max(a['model']['scores'])>=100;page.screenshot(path=str(out/f'finish-{width}.png'));assert not errors,errors
  rows.append({'viewport':[width,height],'real_touch_rack_scroll_select_and_play':True,'sound_pause_freezes_committed_motion':True,'prior_pause_preserved_in_settings':True,'draw_reveals_one_new_tile':True,'zoom_and_round_advance':True,'saved_resume_exact':True,'race_to100_result':True,'music_rms':music,'errors':errors});c.close()
 browser.close()
server.shutdown();(out/'browser-results.json').write_text(json.dumps(rows,indent=2));print('NEON DOMINOS PHONE-SIZE BROWSER CHECKS PASSED')
