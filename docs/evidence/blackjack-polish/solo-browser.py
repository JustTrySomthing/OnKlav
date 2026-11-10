from pathlib import Path
from playwright.sync_api import sync_playwright
import http.server,threading,json
w=Path('/workspace/scratch/f6c9793330b3');out=w/'project/OnKlav/docs/evidence/blackjack-polish';out.mkdir(parents=True,exist_ok=True)
class H(http.server.SimpleHTTPRequestHandler):
 def __init__(self,*a,**k):super().__init__(*a,directory=str(w/'blackjack-web'),**k)
 def log_message(self,*a):pass
server=http.server.ThreadingHTTPServer(('127.0.0.1',0),H);threading.Thread(target=server.serve_forever,daemon=True).start();results=[]
AUDIO="""window.__bjMeters=[];const nativeConnect=AudioNode.prototype.connect;AudioNode.prototype.connect=function(destination,...args){if(destination instanceof AudioDestinationNode){let meter=this.context.createAnalyser();meter.fftSize=1024;nativeConnect.call(this,meter,...args);nativeConnect.call(meter,destination);window.__bjMeters.push(meter);return destination;}return nativeConnect.call(this,destination,...args)};window.__bjRms=()=>Math.max(0,...window.__bjMeters.map(m=>{let a=new Float32Array(m.fftSize);m.getFloatTimeDomainData(a);return Math.sqrt(a.reduce((s,v)=>s+v*v,0)/a.length)}));"""
with sync_playwright()as p:
 browser=p.chromium.launch(executable_path='/workspace/scratch/b5e698131b7f/toolchain/chrome-headless-shell-linux64/chrome-headless-shell',args=['--no-sandbox','--use-angle=swiftshader','--enable-unsafe-swiftshader','--disable-dev-shm-usage','--autoplay-policy=no-user-gesture-required'])
 for width,height in [(320,560),(432,740)]:
  c=browser.new_context(viewport={'width':width,'height':height},has_touch=True);c.add_init_script("window.onklavHosted=true;window.onklavFreshStart=!sessionStorage.getItem('opened');sessionStorage.setItem('opened','1')");c.add_init_script(AUDIO)
  page=c.new_page();errors=[];page.on('pageerror',lambda e:errors.append(str(e)));page.on('console',lambda m:errors.append(m.text)if m.type=='error'or'ERROR:'in m.text else None)
  page.goto(f'http://127.0.0.1:{server.server_port}/index.html?game=blackjack');page.wait_for_function('typeof blackjackProbe==="function"')
  def probe(op='state'):
   page.evaluate('(op)=>blackjackProbe(op)',op);page.wait_for_timeout(25);page.evaluate('blackjackProbe("state")');return page.evaluate('blackjackState')
  def idle():page.wait_for_function('(()=>{blackjackProbe("state");return !blackjackState.busy})()',timeout=7000)
  def click(label):
   a=probe();assert a['actions'][label]['enabled'],a;page.touchscreen.tap(*a['actions'][label]['point'])
  probe('quiet_music');a=probe('player_setup');page.wait_for_timeout(150);a=probe();assert a['busy'] and a['event']['kind']=='deal',a
  rms=page.evaluate('__bjRms()');assert rms>.0001,rms
  page.screenshot(path=str(out/f'dealing-{width}.png'))
  page.evaluate('onklavGameAction("pause")');page.wait_for_timeout(70);a=probe();age=a['event']['age'];page.wait_for_timeout(200);a=probe();a['buttons']=[b for b in a['buttons'] if b['text']=='Done'];assert a['paused']and abs(a['event']['age']-age)<.02 and all(not v['playing']or v['paused']for v in a['voices']),a
  page.evaluate('onklavGameAction("pause")');idle();a=probe();assert a['ready']and a['hidden'][1]==[False,True]and a['hidden'][0]==[False,False],a
  x,y,ww,hh=a['rect'];assert x>=0 and y>56 and x+ww<=width+1 and y+hh<=height,a
  page.screenshot(path=str(out/f'table-{width}.png'));rect_before=a['rect']
  click('Hit');idle();a=probe();assert len(a['hands'][0])==3 and a['hands'][0][-1]==5 and a['ready']and a['hidden'][1]==[False,True],a
  page.screenshot(path=str(out/f'hit-{width}.png'))
  click('Stand');page.wait_for_timeout(350);a=probe();assert a['event']['kind']=='flip',a;page.screenshot(path=str(out/f'reveal-{width}.png'))
  page.wait_for_function('(()=>{blackjackProbe("state");return blackjackState.report&&blackjackState.report_title==="You win!"})()',timeout=6000);a=probe();assert a['winner']==0 and a['revealed']and a['hands'][1][-1]==51,a
  page.screenshot(path=str(out/f'win-{width}.png'));idle();a=probe();assert a['finished']and not any(a['hidden'][1])and a['rect']==rect_before,a
  # Save/reload preserves result and emits no repeated deals or result cues.
  page.evaluate('onklavGameAction("return")');page.wait_for_timeout(500);page.reload();page.wait_for_function('typeof blackjackProbe==="function"');a=probe();assert [b['text']for b in a['buttons']]==['New','Resume'] and a['finished']and not a['audio'],a
  button=next(b for b in a['buttons']if b['text']=='Resume');page.touchscreen.tap(*button['point']);page.wait_for_timeout(150);assert not probe()['paused']
  # The same SDK Help/Audio/Settings/close surfaces stay in control.
  for action in ['help','audio','settings']:
   page.evaluate('(a)=>onklavGameAction(a)',action);page.wait_for_timeout(150);a=probe();assert a['paused']and any(b['text']=='Done' for b in a['buttons']),a
   a['buttons']=[b for b in a['buttons'] if b['text']=='Done']
   if a['buttons'][0]['point'][1]>height-20:page.mouse.move(width*.5,height*.5);page.mouse.wheel(0,2000);page.wait_for_timeout(200);a=probe();a['buttons']=[b for b in a['buttons'] if b['text']=='Done']
   page.touchscreen.tap(*a['buttons'][0]['point']);page.wait_for_timeout(150);assert not probe()['paused']
  page.evaluate('onklavGameAction("close")');page.wait_for_timeout(100);a=probe();assert [b['text']for b in a['buttons']]==['Resume','Quit'],a;page.touchscreen.tap(*a['buttons'][0]['point']);page.wait_for_timeout(100)
  # Natural and bust feedback, with real Master mute silence.
  probe('quiet_music');probe('mute');probe('natural_setup');page.wait_for_timeout(180);assert page.evaluate('__bjRms()')<.0001
  page.wait_for_function('(()=>{blackjackProbe("state");return blackjackState.report})()',timeout=6000);a=probe();assert a['winner']==0 and a['report_title']=='You win!' and 'natural 21'in a['report_detail'],a;page.screenshot(path=str(out/f'blackjack-{width}.png'));idle();probe('unmute')
  probe('bust_setup');idle();click('Hit');page.wait_for_function('(()=>{blackjackProbe("state");return blackjackState.report})()',timeout=5000);a=probe();assert a['winner']==1 and 'bust'in a['report_detail'] and a['revealed'],a;page.screenshot(path=str(out/f'bust-{width}.png'));idle()
  # Adopt the original solo snapshot without a fresh deal or repeated animations.
  a=probe('legacy');assert page.evaluate('blackjackLegacyOk') and a['phase']=='player'and a['shown']==[[8,4],[9,3]]and not a['busy'],a
  probe('resume');click('Hit');idle();a=probe();assert a['hands'][0]==[8,4,5] and a['ready'],a
  assert not errors,errors
  results.append({'viewport':[width,height],'four_card_animated_deal':True,'drop_audio_rms':rms,'pause_freezes_deal_and_audio':True,'hidden_hole_card':True,'hit_then_stand_then_dealer':True,'animated_reveal_and_totals':True,'stable_table_allocation':True,'natural_and_bust_reports':True,'master_mute_silence':True,'saved_result_no_replayed_sounds':True,'legacy_solo_save_adopted':True,'shared_help_audio_settings_quit_new_resume':True,'errors':errors});c.close()
 browser.close()
server.shutdown();(out/'solo-browser.json').write_text(json.dumps(results,indent=2));print('BLACKJACK SOLO PHONE FLOWS PASSED')
