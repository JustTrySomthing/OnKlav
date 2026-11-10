from pathlib import Path
from playwright.sync_api import sync_playwright
import http.server,threading,json,math
w=Path('/workspace/scratch/f6c9793330b3');out=w/'final-games-qa';out.mkdir(exist_ok=True)
class H(http.server.SimpleHTTPRequestHandler):
 def __init__(self,*a,**k):super().__init__(*a,directory=str(w/'hockey-web'),**k)
 def log_message(self,*a):pass
server=http.server.ThreadingHTTPServer(('127.0.0.1',0),H);threading.Thread(target=server.serve_forever,daemon=True).start()
with sync_playwright()as p:
 browser=p.chromium.launch(executable_path=str(w/'toolchain/chrome-headless-shell-linux64/chrome-headless-shell'),args=['--no-sandbox','--use-angle=swiftshader','--enable-unsafe-swiftshader','--disable-dev-shm-usage','--autoplay-policy=no-user-gesture-required']);rows=[]
 for width,height in [(320,640),(432,800)]:
  for game in ['four','panda']:
   c=browser.new_context(viewport={'width':width,'height':height},has_touch=True);c.add_init_script('window.onklavHosted=true;window.onklavFreshStart=true');page=c.new_page();errors=[];page.on('pageerror',lambda e:errors.append(str(e)));page.on('console',lambda m:errors.append(m.text)if m.type=='error'or'ERROR:'in m.text else None);page.goto(f'http://127.0.0.1:{server.server_port}/index.html?game={game}');page.wait_for_function('typeof controlsProbe==="function"')
   def state(op='state'):
    page.evaluate('(op)=>controlsProbe(op)',op);page.wait_for_timeout(30);return page.evaluate('controlsState')
   page.wait_for_timeout(200)
   if game=='four':
    a=state();e=a['extra'];assert abs(e['feedback_y']-(e['gap_top']+e['gap_bottom'])/2)<1,e;assert e['title_size']==30 and e['report_box']=='StyleBoxEmpty',e
    state('notice');page.wait_for_timeout(350);page.screenshot(path=str(out/f'cats-{width}.png'))
   else:
    state('build');page.wait_for_timeout(200);a=state();assert a['extra']['blocks']==8,a
    assert [x['text']for x in a['extra']['build_buttons']]==['Reset','Ready','Random']
    page.screenshot(path=str(out/f'panda-build-{width}.png'))
    for action in ['audio','settings']:
     page.evaluate('(a)=>onklavGameAction(a)',action);page.wait_for_timeout(130);a=state();seconds=a['extra']['seconds'];page.wait_for_timeout(160);assert state()['extra']['seconds']==seconds
     page.evaluate('onklavGameAction("host_state")');b=next(x for x in a['buttons']if x['text']=='Done');page.touchscreen.tap(*b['point']);page.wait_for_timeout(150);assert not state()['paused']
    state('timeout');page.wait_for_timeout(200);a=state();assert a['extra']['phase']=='shoot' and a['snapshot']['model']['blocks'],a
    state('shoot');page.wait_for_timeout(160);a=state();assert a['extra']['load']<1 and not a['extra']['local'] and a['extra']['seconds']==30,a
    page.screenshot(path=str(out/f'panda-load-{width}.png'));page.wait_for_timeout(900);a=state();assert a['extra']['load']==1 and a['extra']['local'] and a['extra']['seconds']<30,a
    assert all(not x['visible']for x in a['extra']['build_buttons']);page.screenshot(path=str(out/f'panda-aim-{width}.png'))
    x,y=a['extra']['cannon'];cdp=c.new_cdp_session(page);cdp.send('Input.dispatchTouchEvent',{'type':'touchStart','touchPoints':[{'x':x,'y':y}]});cdp.send('Input.dispatchTouchEvent',{'type':'touchMove','touchPoints':[{'x':x+18,'y':min(height-62,y+60)}]});page.wait_for_timeout(100);cdp.send('Input.dispatchTouchEvent',{'type':'touchEnd','touchPoints':[]});page.wait_for_timeout(200);a=state();assert a['extra']['phase']=='flight' and a['snapshot']['model']['shots']==1,a
    state('duck');page.wait_for_timeout(100);page.screenshot(path=str(out/f'panda-duck-{width}.png'))
    state('notice');page.wait_for_timeout(300);page.screenshot(path=str(out/f'panda-feedback-{width}.png'));assert state()['extra']['report_box']=='StyleBoxEmpty'
   assert not errors,errors;rows.append({'game':game,'viewport':[width,height],'passed':True,'errors':errors});c.close()
 browser.close()
server.shutdown();(out/'browser-results.json').write_text(json.dumps(rows,indent=2));print('CATS AND PANDA BROWSER CHECKS PASSED')
