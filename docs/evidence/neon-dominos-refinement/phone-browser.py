from pathlib import Path
from playwright.sync_api import sync_playwright
import http.server,threading,json,math
w=Path('/workspace/scratch/f6c9793330b3');out=w/'dominos-refine-qa';out.mkdir(exist_ok=True)
class H(http.server.SimpleHTTPRequestHandler):
 def __init__(self,*a,**k):super().__init__(*a,directory=str(w/'dominos-refine-web'),**k)
 def log_message(self,*a):pass
server=http.server.ThreadingHTTPServer(('127.0.0.1',0),H);threading.Thread(target=server.serve_forever,daemon=True).start()
with sync_playwright()as p:
 browser=p.chromium.launch(executable_path=str(w/'toolchain/chrome-headless-shell-linux64/chrome-headless-shell'),args=['--no-sandbox','--use-angle=swiftshader','--enable-unsafe-swiftshader','--disable-dev-shm-usage','--autoplay-policy=no-user-gesture-required']);rows=[]
 for width,height in [(320,640),(432,800)]:
  c=browser.new_context(viewport={'width':width,'height':height},has_touch=True);c.add_init_script("window.onklavHosted=true;window.onklavFreshStart=!sessionStorage.getItem('opened');sessionStorage.setItem('opened','1')");page=c.new_page();errors=[];page.on('pageerror',lambda e:errors.append(str(e)));page.on('console',lambda m:errors.append(m.text)if m.type=='error'or'ERROR:'in m.text else None);page.goto(f'http://127.0.0.1:{server.server_port}/index.html?game=dominos');page.wait_for_function('typeof dominosProbe==="function"')
  def state(op='state'):
   page.evaluate('(op)=>dominosProbe(op)',op);page.wait_for_timeout(35);return page.evaluate('dominosState')
  def button(identity):return next(v for v in state()['controls']if v['id']==identity and v['visible'])
  def click(identity):
   b=button(identity);assert b['enabled'],b;page.touchscreen.tap(*b['point']);page.wait_for_timeout(60)
  def done():
   a=state();b=next(v for v in a['dialogs']if v['text']=='Done');page.touchscreen.tap(*b['point']);page.wait_for_timeout(120)
  def visible(a):
   for t in a['tiles']:
    x,y,ww,hh=t['rect'];assert x>=8 and x+ww<=width-8 and y>0 and y+hh<=height-4,(a,t)
  def choice():
   state('choice');page.wait_for_timeout(350);a=state();target=next(t for t in a['tiles']if len(t['ends'])==2);return a,target
  state('opening');page.wait_for_timeout(180);a=state();visible(a);page.screenshot(path=str(out/f'hand-{width}.png'));tile=next(t for t in a['tiles']if t['id']==a['model']['required']);page.touchscreen.tap(*tile['point']);page.wait_for_timeout(80);a=state();assert len(a['model']['chain'])==1 and a['flying'],a
  page.evaluate('onklavGameAction("audio")');page.wait_for_timeout(100);a=state();held=a['snapshot'];age=a['flying']['age'];assert a['paused']and a['music_paused'];page.wait_for_timeout(150);assert state()['snapshot']==held and state()['flying']['age']==age;page.evaluate('onklavGameAction("host_state")');done();page.wait_for_timeout(600);assert not state()['flying']
  state('draw');page.wait_for_timeout(150);a=state();previous=len(a['tiles']);click('draw');page.wait_for_timeout(600);a=state();visible(a);assert len(a['tiles'])==previous+1 and sum(t['fresh']for t in a['tiles'])==1 and not any(t['concealed']for t in a['tiles']),a;assert '|'in a['detail'],a;page.screenshot(path=str(out/f'draw-{width}.png'))
  state('large');page.wait_for_timeout(150);a=state();visible(a);assert len(a['tiles'])==21;page.screenshot(path=str(out/f'full-hand-{width}.png'))
  a,t=choice();before=a['snapshot'];page.touchscreen.tap(*t['point']);page.wait_for_timeout(80);a=state();assert a['selected']==t['id'] and a['snapshot']==before,a;page.screenshot(path=str(out/f'choose-{width}.png'));click('right');page.wait_for_timeout(100);a=state();assert len(a['model']['chain'])==len(before['model']['chain'])+1
  a,t=choice();before=a['snapshot'];cdp=c.new_cdp_session(page);x,y=t['point'];target=next(v for v in a['controls']if v['id']=='left')['point']
  cdp.send('Input.dispatchTouchEvent',{'type':'touchStart','touchPoints':[{'x':x,'y':y}]})
  for k in range(1,7):
   px=x+(target[0]-x)*k/6;py=y+(target[1]-y)*k/6;cdp.send('Input.dispatchTouchEvent',{'type':'touchMove','touchPoints':[{'x':px,'y':py}]});page.wait_for_timeout(35)
  a=state();assert a['drag'],a;page.screenshot(path=str(out/f'drag-{width}.png'));cdp.send('Input.dispatchTouchEvent',{'type':'touchEnd','touchPoints':[]});page.wait_for_timeout(120);a=state();assert len(a['model']['chain'])==len(before['model']['chain'])+1 and not a['drag'],a
  a,t=choice();before=a['snapshot'];x,y=t['point'];cdp.send('Input.dispatchTouchEvent',{'type':'touchStart','touchPoints':[{'x':x,'y':y}]});cdp.send('Input.dispatchTouchEvent',{'type':'touchMove','touchPoints':[{'x':10,'y':height-3}]});page.wait_for_timeout(80);cdp.send('Input.dispatchTouchEvent',{'type':'touchEnd','touchPoints':[]});page.wait_for_timeout(150);a=state();assert a['snapshot']==before and not a['drag'],a
  state('long');page.wait_for_timeout(150);a=state();assert len(a['chain'])>=19 and min(v['rect'][2]for v in a['chain'])>=50,(width,a);visible(a);page.screenshot(path=str(out/f'long-chain-{width}.png'))
  page.evaluate('onklavGameAction("pause")');page.evaluate('onklavGameAction("settings")');page.wait_for_timeout(100);done();assert state()['paused'];page.evaluate('onklavGameAction("pause")')
  a=state();page.evaluate('onklavGameAction("return")');page.wait_for_timeout(1500);saved=state()['snapshot'];page.reload();page.wait_for_function('typeof dominosProbe==="function"');a=state();assert a['paused'] and a['snapshot']==saved,a;b=next(x for x in a['dialogs']if x['text']=='Resume');page.touchscreen.tap(*b['point']);page.wait_for_timeout(120);assert not state()['paused']
  page.evaluate('onklavGameAction("settings")');page.wait_for_timeout(100);a=state();toggle=next(v for v in a['dialogs']if v['text']=='Reduce motion');page.touchscreen.tap(*toggle['point']);done();a=state();assert a['reduced'];state('opening');page.wait_for_timeout(100);a=state();t=next(v for v in a['tiles']if v['id']==a['model']['required']);page.touchscreen.tap(*t['point']);page.wait_for_timeout(100);a=state();assert len(a['model']['chain'])==1 and a['feedback_scale']==1 and a['particle_count']==0,a
  state('round');page.wait_for_timeout(150);page.screenshot(path=str(out/f'round-{width}.png'));a=state();click('next');page.wait_for_timeout(1200);assert state()['model']['round']==a['model']['round']+1
  state('finish');page.wait_for_timeout(250);a=state();assert a['model']['phase']=='match_over';visible(a);page.screenshot(path=str(out/f'finish-{width}.png'));assert not errors,errors
  rows.append({'viewport':[width,height],'no_hand_scrolling_all21_visible':True,'one_tap_opening':True,'distinct_end_choice':True,'real_drag_drop_and_cancel':True,'new_draw_highlight_and_face_feedback':True,'readable_long_chain':True,'sound_pause_and_prior_pause_intent':True,'exact_saved_resume':True,'reduced_motion':True,'round_match_results':True,'errors':errors});c.close()
 browser.close()
server.shutdown();(out/'browser-results.json').write_text(json.dumps(rows,indent=2));print('DOMINOS WRAPPING HAND, DIRECT PLAY, DRAG AND SAVES PASSED')
