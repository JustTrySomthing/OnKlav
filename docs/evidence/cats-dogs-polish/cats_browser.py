from pathlib import Path
from playwright.sync_api import sync_playwright
import http.server,threading,json,math
w=Path('/workspace/scratch/f6c9793330b3');out=w/'project/OnKlav/docs/evidence/cats-dogs-polish';out.mkdir(parents=True,exist_ok=True)
class H(http.server.SimpleHTTPRequestHandler):
 def __init__(self,*a,**k):super().__init__(*a,directory=str(w/'cats-web'),**k)
 def log_message(self,*a):pass
server=http.server.ThreadingHTTPServer(('127.0.0.1',0),H);threading.Thread(target=server.serve_forever,daemon=True).start();results=[]
AUDIO="""window.__catMeters=[];const __nativeConnect=AudioNode.prototype.connect;AudioNode.prototype.connect=function(destination,...args){if(destination instanceof AudioDestinationNode){let meter=this.context.createAnalyser();meter.fftSize=1024;__nativeConnect.call(this,meter,...args);__nativeConnect.call(meter,destination);window.__catMeters.push(meter);return destination;}return __nativeConnect.call(this,destination,...args)};window.__catRms=()=>Math.max(0,...window.__catMeters.map(m=>{let a=new Float32Array(m.fftSize);m.getFloatTimeDomainData(a);return Math.sqrt(a.reduce((s,v)=>s+v*v,0)/a.length)}));"""
with sync_playwright()as p:
 browser=p.chromium.launch(executable_path='/workspace/scratch/b5e698131b7f/toolchain/chrome-headless-shell-linux64/chrome-headless-shell',args=['--no-sandbox','--use-angle=swiftshader','--enable-unsafe-swiftshader','--disable-dev-shm-usage','--autoplay-policy=no-user-gesture-required'])
 for width,height in [(320,560),(432,740)]:
  c=browser.new_context(viewport={'width':width,'height':height},has_touch=True);c.add_init_script("window.onklavHosted=true;window.onklavFreshStart=!sessionStorage.getItem('opened');sessionStorage.setItem('opened','1')");c.add_init_script(AUDIO)
  page=c.new_page();errors=[];page.on('pageerror',lambda e:errors.append(str(e)));page.on('console',lambda m:errors.append(m.text)if m.type=='error'or'ERROR:'in m.text else None)
  page.goto(f'http://127.0.0.1:{server.server_port}/index.html?game=four');page.wait_for_function('typeof catsProbe==="function"');page.wait_for_timeout(400)
  def probe(op='state'):
   page.evaluate('(op)=>catsProbe(op)',op);page.wait_for_timeout(25);page.evaluate('catsProbe("state")');return page.evaluate('catsState')
  def idle():page.wait_for_function('(()=>{catsProbe("state");return catsState.ready||catsState.finished})()',timeout=6000)
  def tap(col):
   a=probe();x,y,ww,hh=a['grid'];page.touchscreen.tap(x+(col+.5)*ww/7,y+hh*.5)
  a=probe();print('INITIAL',width,a,flush=True);assert a['ready']and len(a['board'])==42 and a['board'].count(0)==42
  x,y,ww,hh=a['grid'];assert x>=0 and y>=56 and x+ww<=width+1 and y+hh<=height,a
  page.screenshot(path=str(out/f'ready-{width}.png'));probe('quiet_music');page.wait_for_timeout(150);baseline=page.evaluate('__catRms()')
  # Actual touch preview can slide columns before committing exactly once.
  session=c.new_cdp_session(page);session.send('Input.dispatchTouchEvent',{'type':'touchStart','touchPoints':[{'x':x+ww*.5/7,'y':y+hh*.3}]});session.send('Input.dispatchTouchEvent',{'type':'touchMove','touchPoints':[{'x':x+ww*3.5/7,'y':y+hh*.35}]});page.wait_for_timeout(100);a=probe();assert a['hover']==3 and a['board'].count(0)==42,a;page.screenshot(path=str(out/f'preview-{width}.png'))
  session.send('Input.dispatchTouchEvent',{'type':'touchEnd','touchPoints':[]});page.wait_for_timeout(140);a=probe();assert a['event'] and a['event']['cell']==38 and a['shown'][38]==0 and a['board'][38]==1,a
  rms=page.evaluate('__catRms()');assert rms>baseline+.0001,(baseline,rms);page.screenshot(path=str(out/f'drop-{width}.png'))
  # Pause freezes the pending drop; no second move is created by resume.
  page.evaluate('onklavGameAction("pause")');page.wait_for_timeout(100);a=probe();age=a['event']['age'];tape=len(a['tape']);page.wait_for_timeout(200);a=probe();assert a['paused'] and abs(a['event']['age']-age)<.02 and all(not v['playing']or v['paused']for v in a['voices']),a
  page.evaluate('onklavGameAction("pause")');page.wait_for_timeout(100);idle();a=probe();assert len(a['tape'])==tape+1 and a['ready'] and 'land'in a['audio'],a
  page.screenshot(path=str(out/f'after-ai-{width}.png'))
  # Invalid full-column touch gives feedback without consuming or sending a turn.
  a=probe('full_setup');tap(0);page.wait_for_timeout(120);a=probe();assert len(a['tape'])==6 and a['report_title']=='This column is full' and a['ready'],a
  # Win, line, confetti and stable board allocation; muted bus really silences playback.
  a=probe('win_setup');grid_before=a['grid'];probe('mute');tap(3);page.wait_for_timeout(170);assert page.evaluate('__catRms()')<.0001
  page.wait_for_function('(()=>{catsProbe("state");return catsState.finished&&catsState.new_visible})()',timeout=4000);a=probe();assert a['winner']==0 and a['line']==[35,36,37,38]and a['report_title']=='Cats win!'and a['new_visible'],a
  assert abs(a['grid'][1]-grid_before[1])<1 and abs(a['grid'][3]-grid_before[3])<1,(grid_before,a['grid'])
  page.screenshot(path=str(out/f'victory-{width}.png'));probe('unmute')
  # Save/return/reload uses SDK New/Resume, preserves final state and does not replay sounds.
  page.evaluate('onklavGameAction("return")');page.wait_for_timeout(700);page.reload();page.wait_for_function('typeof catsProbe==="function"');a=probe();assert [b['text']for b in a['buttons']]==['New','Resume'] and len(a['tape'])==7 and a['finished']and not a['audio'],a
  button=next(b for b in a['buttons']if b['text']=='Resume');page.touchscreen.tap(*button['point']);page.wait_for_timeout(180);a=probe();assert not a['paused']and a['finished']and len(a['tape'])==7,a
  # Standard Help and Quit dialog controls remain shared, with no extra toolbar.
  page.evaluate('onklavGameAction("help")');page.wait_for_timeout(180);a=probe();assert a['paused']and [b['text']for b in a['buttons']]==['Done'],a
  page.touchscreen.tap(*a['buttons'][0]['point']);page.wait_for_timeout(150)
  page.evaluate('onklavGameAction("close")');page.wait_for_timeout(150);a=probe();assert [b['text']for b in a['buttons']]==['Resume','Quit'],a
  page.touchscreen.tap(*a['buttons'][0]['point']);page.wait_for_timeout(100)
  # Previous solo save: one pending AI move resumes once, then survives a pause cycle.
  a=probe('legacy');assert page.evaluate('catsLegacyOk') and a['turn']==1 and a['base'].count(1)==3,a
  probe('resume');idle();a=probe();assert len(a['tape'])==1 and a['turn']==0 and a['board'].count(0)==36,a
  page.evaluate('onklavGameAction("pause")');page.wait_for_timeout(50);page.evaluate('onklavGameAction("pause")');page.wait_for_timeout(500);assert len(probe()['tape'])==1
  assert not errors,errors
  results.append({'viewport':[width,height],'touch_preview_then_single_drop':True,'gravity_landing_and_audio':True,'rms_baseline':baseline,'rms_during_drop':rms,'master_mute_actual_silence':True,'pause_freezes_drop_and_audio':True,'full_column_feedback':True,'winning_line_and_report':True,'stable_board_on_result':True,'saved_game_no_replayed_moves_or_sounds':True,'shared_help_quit_new_resume_dialogs':True,'legacy_pending_ai_resumes_once':True,'errors':errors});c.close()
 browser.close()
server.shutdown();(out/'solo-browser.json').write_text(json.dumps(results,indent=2));print('CATS SOLO PHONE FLOWS PASSED')
