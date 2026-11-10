from pathlib import Path
from playwright.sync_api import sync_playwright
import http.server,threading,json,math
w=Path('/workspace/scratch/b5e698131b7f')
class Handler(http.server.SimpleHTTPRequestHandler):
 def __init__(self,*a,**k):super().__init__(*a,directory=str(w/'web-controls4-checked'),**k)
 def log_message(self,*a):pass
s=http.server.ThreadingHTTPServer(('127.0.0.1',0),Handler);threading.Thread(target=s.serve_forever,daemon=True).start();records=[]
with sync_playwright()as p:
 b=p.chromium.launch(executable_path=str(w/'toolchain/chrome-headless-shell-linux64/chrome-headless-shell'),args=['--no-sandbox','--use-angle=swiftshader','--enable-unsafe-swiftshader','--disable-dev-shm-usage','--autoplay-policy=no-user-gesture-required'])
 for width,height in [(320,560),(432,740)]:
  c=b.new_context(viewport={'width':width,'height':height},has_touch=True)
  c.add_init_script('''window.onklavHosted=true;window.onklavFreshStart=!sessionStorage.getItem('started');sessionStorage.setItem('started','1');window.audioMeters=[];const original=AudioNode.prototype.connect;AudioNode.prototype.connect=function(target,...args){if(target instanceof AudioDestinationNode){const a=this.context.createAnalyser();original.call(this,a);original.call(a,target);window.audioMeters.push(a);return target;}return original.call(this,target,...args);};''')
  page=c.new_page();errors=[];msgs=[]
  page.on('pageerror',lambda e:errors.append(str(e)));page.on('console',lambda m:msgs.append({'type':m.type,'text':m.text}))
  def probe(x='state'):
   page.evaluate('(x)=>window.gameProbe(x)',x);page.wait_for_timeout(80);page.evaluate('window.gameProbe("state")');return page.evaluate('window.gameState')
  def act(x):page.evaluate('(x)=>window.onklavGameAction(x)',x);page.wait_for_timeout(80);return probe()
  def energy():return page.evaluate('''()=>Math.max(0,...window.audioMeters.map(a=>{const v=new Float32Array(a.fftSize);a.getFloatTimeDomainData(v);return Math.sqrt(v.reduce((s,x)=>s+x*x,0)/v.length)}))''')
  page.goto(f'http://127.0.0.1:{s.server_port}/index.html?game=hockey');page.wait_for_function('typeof window.gameProbe === "function"');a=probe();assert not a['paused']and a['buttons']==[],a
  page.touchscreen.tap(width/2,height-30);assert probe()['phase']==1
  page.wait_for_timeout(900);sound=energy();assert sound>0.00001,('No live WebAudio output',sound,probe())
  probe('mute');page.wait_for_timeout(150);assert energy()<0.000001;probe('unmute');probe('musicoff');page.wait_for_timeout(200);assert probe()['music_muted'];probe('effect');page.wait_for_timeout(10);probe('musicon');page.wait_for_timeout(250);assert energy()>0.000001
  a=act('pause');assert a['paused']and a['notice']and a['play_icon']and not a['chrome_dialog'];alpha=a['blink_alpha'];page.wait_for_timeout(700);assert abs(probe()['blink_alpha']-alpha)>.08
  page.screenshot(path=str(w/f'paused-controls4-{width}.png'))
  a=act('pause');assert not a['paused']and not a['notice']
  for mode in ['audio','settings']:
   a=act(mode);assert a['paused']and not a['notice']and a['chrome_dialog']and not a['modal'];a=probe('done');assert not a['paused']and not a['chrome_dialog']
  a=act('help');assert a['paused']and a['texts'][0]=='How to play'and a['buttons']==['Done']and not a['modal'];probe('left');assert not probe()['paused']
  probe('point');probe('serve');a=act('return');assert a['paused']and a['saved_scores']==[1,0];assert any(m['text']=='ONKLAV_GAME_RETURN'for m in msgs)
  page.wait_for_timeout(400);page.reload();page.wait_for_function('typeof window.gameProbe === "function"');a=probe();assert a['buttons']==['New','Resume']and a['scores']==[1,0],a
  probe('right');assert not probe()['paused']and probe()['scores']==[1,0]
  a=act('entry');assert a['buttons']==['New','Resume'];a=probe('left');assert a['saved_scores']==[]and a['buttons']==[],a
  assert any(m['text']=='ONKLAV_GAME_START_MENU'for m in msgs)
  assert not any('Choose how to play' in t for t in a['texts'])
  # Simulate selecting Single Player in the actual host start dialog: a fresh launch.
  page.evaluate("sessionStorage.removeItem('started')");page.reload();page.wait_for_function('typeof window.gameProbe === "function"');a=probe();assert a['scores']==[0,0]and not a['paused']and a['buttons']==[]
  a=act('close');assert a['buttons']==['Resume','Quit'];page.evaluate('window.gameProbe("right")');page.wait_for_timeout(400);assert page.evaluate('window.quit_cleared')is True
  engine=[m for m in msgs if m['type']=='error'or'ERROR:'in m['text']];assert not errors and not engine,(errors,engine)
  records.append({'viewport':[width,height],'saved_return_resume_new_discard_original_menu':True,'manual_pause_label_and_blink':True,'silent_options_and_centered_help':True,'live_audio_rms':sound,'javascript_errors':errors,'engine_errors':engine})
  c.close()
 b.close()
s.shutdown();(w/'flow-browser-results.json').write_text(json.dumps(records,indent=2));print('Both phone viewports passed lifecycle, native start-menu protocol, live audio, pause label/blink, options and Help')
