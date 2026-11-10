from pathlib import Path
from playwright.sync_api import sync_playwright
import http.server,threading,json,math
w=Path('/workspace/scratch/f6c9793330b3');out=w/'hockey-qa';out.mkdir(exist_ok=True)
class H(http.server.SimpleHTTPRequestHandler):
 def __init__(self,*a,**k):super().__init__(*a,directory=str(w/'hockey-web'),**k)
 def log_message(self,*a):pass
server=http.server.ThreadingHTTPServer(('127.0.0.1',0),H);threading.Thread(target=server.serve_forever,daemon=True).start()
AUDIO="""window.__meters=[];const original=AudioNode.prototype.connect;AudioNode.prototype.connect=function(dest,...args){if(dest instanceof AudioDestinationNode){const m=this.context.createAnalyser();m.fftSize=1024;original.call(this,m,...args);original.call(m,dest);__meters.push(m);return dest}return original.call(this,dest,...args)};window.__rms=()=>Math.max(0,...__meters.map(m=>{const a=new Float32Array(m.fftSize);m.getFloatTimeDomainData(a);return Math.sqrt(a.reduce((s,v)=>s+v*v,0)/a.length)}));"""
with sync_playwright()as p:
 browser=p.chromium.launch(executable_path=str(w/'toolchain/chrome-headless-shell-linux64/chrome-headless-shell'),args=['--no-sandbox','--use-angle=swiftshader','--enable-unsafe-swiftshader','--disable-dev-shm-usage','--autoplay-policy=no-user-gesture-required']);rows=[]
 for width,height in [(320,640),(432,800)]:
  c=browser.new_context(viewport={'width':width,'height':height},has_touch=True);c.add_init_script("window.onklavHosted=true;window.onklavFreshStart=!sessionStorage.getItem('opened');sessionStorage.setItem('opened','1')");c.add_init_script(AUDIO);page=c.new_page();errors=[];page.on('pageerror',lambda e:errors.append(str(e)));page.on('console',lambda m:errors.append(m.text)if m.type=='error'or'ERROR:'in m.text else None);page.goto(f'http://127.0.0.1:{server.server_port}/index.html?game=hockey');page.wait_for_function('typeof hockeyProbe==="function"')
  def state(op='state'):page.evaluate('(op)=>hockeyProbe(op)',op);page.wait_for_timeout(30);return page.evaluate('hockeyState')
  def done():
   a=state();button=next(b for b in a['buttons']if b['text']=='Done');page.touchscreen.tap(*button['point']);page.wait_for_timeout(180)
  a=state();page.touchscreen.tap(*a['serve']);page.wait_for_timeout(200);state('midfield')
  # Fast touch movement remains bounded, follows the primary finger and renders an interpolated pose.
  a=state();x,y,ww,hh=a['rink'];cdp=c.new_cdp_session(page);cdp.send('Input.dispatchTouchEvent',{'type':'touchStart','touchPoints':[{'x':x+ww*.85,'y':y+hh*.85}]});page.wait_for_timeout(150);a=state();assert abs(a['paddle']-510)<18 and abs(a['target']-.85)<.03,a;cdp.send('Input.dispatchTouchEvent',{'type':'touchEnd','touchPoints':[]})
  music=0
  for sample in range(10):
   music=max(music,page.evaluate('__rms()'));page.wait_for_timeout(100)
   if music>.0001:break
  assert music>.0001,(music,state())
  for menu in ['Audio','Settings','Audio']:
   state('midfield');page.wait_for_timeout(100);a=state();page.touchscreen.tap(*a['toolbar'][menu]);page.wait_for_timeout(120);held=state();assert held['paused']and held['music_paused']and math.hypot(*held['saved_velocity'])>.1,held
   tick=held['tick'];point=held['point'];page.wait_for_timeout(150);assert state()['tick']==tick
   page.evaluate('onklavGameAction("host_state")');done();a=state();assert not a['paused']and not a['buttons']and math.hypot(*a['velocity'])>100 and a['point']!=point,a
  # Opening Sound during an existing pause must not start a rally accidentally.
  page.evaluate('onklavGameAction("pause")');page.evaluate('onklavGameAction("audio")');page.wait_for_timeout(100);done();assert state()['paused'];page.evaluate('onklavGameAction("pause")');page.wait_for_timeout(100);assert not state()['paused']
  cdp.send('Input.dispatchTouchEvent',{'type':'touchStart','touchPoints':[{'x':x+ww*.5,'y':y+hh*.85}]});page.wait_for_timeout(180);cdp.send('Input.dispatchTouchEvent',{'type':'touchEnd','touchPoints':[]});state('collision');page.wait_for_timeout(180);a=state();assert a['velocity'][1]<0,a
  state('goal');page.wait_for_timeout(400);page.screenshot(path=str(out/f'goal-{width}.png'));a=state();assert a['goal_time']>0 and a['scores'][0]>=1,a
  state('serve');state('enemy_goal');page.wait_for_timeout(400);page.screenshot(path=str(out/f'rival-{width}.png'))
  state('serve');state('midfield');page.wait_for_timeout(100);page.evaluate('onklavGameAction("return")');page.wait_for_timeout(1800);page.reload();page.wait_for_function('typeof hockeyProbe==="function"');a=state();assert a['paused']and [b['text']for b in a['buttons']]==['New','Resume']and math.hypot(*a['saved_velocity'])>.1,a;button=next(b for b in a['buttons']if b['text']=='Resume');page.touchscreen.tap(*button['point']);page.wait_for_timeout(200);assert math.hypot(*state()['velocity'])>100
  state('finish');page.wait_for_timeout(450);a=state();assert a['result']and sum(a['scores'])==10;page.screenshot(path=str(out/f'final-{width}.png'))
  assert not errors,errors
  rows.append({'viewport':[width,height],'actual_audio_settings_resume_restores_motion':True,'host_probe_does_not_break_done':True,'existing_pause_preserved':True,'touch_responsiveness_and_primary_pointer':True,'real_collision_rebound':True,'unboxed_goal_rival_and_match_results':True,'saved_playing_rally_restores_velocity':True,'music_rms':music,'errors':errors});c.close()
 # The four other bundled games use shared tree pause. Exercise their live Sound and Settings flows.
 shared=[]
 for game in ['naval','four','blackjack','panda']:
  c=browser.new_context(viewport={'width':432,'height':800},has_touch=True);c.add_init_script('window.onklavHosted=true;window.onklavFreshStart=true');page=c.new_page();errors=[];page.on('pageerror',lambda e:errors.append(str(e)));page.on('console',lambda m:errors.append(m.text)if m.type=='error'or'ERROR:'in m.text else None);page.goto(f'http://127.0.0.1:{server.server_port}/index.html?game={game}');page.wait_for_function('typeof controlsProbe==="function"')
  def probe(op='state'):page.evaluate('(op)=>controlsProbe(op)',op);page.wait_for_timeout(25);return page.evaluate('controlsState')
  page.wait_for_timeout(250);probe('prepare')
  for action in ['audio','settings']:
   page.evaluate('(a)=>onklavGameAction(a)',action);page.wait_for_timeout(100);a=probe();assert a['paused']and a['temporary'],(game,a);at=a['snapshot'];progress=a['progress'];page.wait_for_timeout(180);a=probe();assert a['snapshot']==at and a['progress']==progress,(game,a)
   page.evaluate('onklavGameAction("host_state")');button=next(v for v in a['buttons']if v['text']=='Done');page.touchscreen.tap(*button['point']);page.wait_for_timeout(180);a=probe();assert not a['paused']and not a['buttons'],(game,a)
  assert not errors,(game,errors)
  shared.append({'game':game,'audio_settings_pause_snapshot_and_resume':True,'errors':errors});c.close()
 browser.close()
server.shutdown();(out/'phone-browser.json').write_text(json.dumps(rows,indent=2));(out/'shared-controls-browser.json').write_text(json.dumps(shared,indent=2));print('HOCKEY AND ALL BUNDLED GAME CONTROLS PASSED')
