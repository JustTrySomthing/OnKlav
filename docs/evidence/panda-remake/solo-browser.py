from pathlib import Path
from playwright.sync_api import sync_playwright
import http.server,threading,json
w=Path('/workspace/scratch/f6c9793330b3');out=w/'panda-qa';out.mkdir(exist_ok=True)
class H(http.server.SimpleHTTPRequestHandler):
 def __init__(self,*a,**k):super().__init__(*a,directory=str(w/'panda-web'),**k)
 def log_message(self,*a):pass
server=http.server.ThreadingHTTPServer(('127.0.0.1',0),H);threading.Thread(target=server.serve_forever,daemon=True).start()
AUDIO="""window.__meters=[];const connect=AudioNode.prototype.connect;AudioNode.prototype.connect=function(dest,...args){if(dest instanceof AudioDestinationNode){const m=this.context.createAnalyser();m.fftSize=1024;connect.call(this,m,...args);connect.call(m,dest);__meters.push(m);return dest;}return connect.call(this,dest,...args)};window.__rms=()=>Math.max(0,...__meters.map(m=>{const a=new Float32Array(m.fftSize);m.getFloatTimeDomainData(a);return Math.sqrt(a.reduce((s,v)=>s+v*v,0)/a.length)}));"""
with sync_playwright()as p:
 b=p.chromium.launch(executable_path='/workspace/scratch/b5e698131b7f/toolchain/chrome-headless-shell-linux64/chrome-headless-shell',args=['--no-sandbox','--use-angle=swiftshader','--enable-unsafe-swiftshader','--disable-dev-shm-usage','--autoplay-policy=no-user-gesture-required'])
 results=[]
 for width,height in [(432,800),(320,560)]:
  c=b.new_context(viewport={'width':width,'height':height},has_touch=True);c.add_init_script("window.onklavHosted=true;window.onklavFreshStart=!sessionStorage.getItem('opened');sessionStorage.setItem('opened','1')");c.add_init_script(AUDIO);page=c.new_page();errors=[]
  page.on('pageerror',lambda e:errors.append(str(e)));page.on('console',lambda m:errors.append(m.text)if m.type=='error'or'ERROR:'in m.text else None)
  page.goto(f'http://127.0.0.1:{server.server_port}/index.html?game=panda');page.wait_for_function('typeof pandaProbe==="function"')
  def probe(op='state'):
   page.evaluate('(op)=>pandaProbe(op)',op);page.wait_for_timeout(20);return page.evaluate('pandaState')
  def click(label):
   a=probe();assert a['actions'][label]['enabled'],a;page.touchscreen.tap(*a['actions'][label]['point']);page.wait_for_timeout(150)
  def wait_ready():page.wait_for_function('(()=>{pandaProbe("state");return pandaState.ready&&!pandaState.report})()',timeout=15000)
  page.wait_for_timeout(400);a=probe();assert a['phase']=='choose',a;page.screenshot(path=str(out/f'choose-{width}.png'))
  music=0
  click('Red Panda');page.wait_for_function('(()=>{pandaProbe("state");return pandaState.phase==="shoot"&&pandaState.ready})()',timeout=12000);a=probe();music=page.evaluate('__rms()');assert music>.0001,music;assert a['leg']==0 and len(a['blocks'])==6,a;page.screenshot(path=str(out/f'launch-{width}.png'))
  # Touch-driven pull, aim and launch. Pausing freezes simulation and music.
  click('Wave'if width<390 else'Shockwave');a=probe();x,y=a['cannon'];page.mouse.move(x,y);page.mouse.down();page.mouse.move(x-4,y+min(55,height-y-55),steps=8);a=probe();assert a['aiming']and a['power']>=15,a;page.screenshot(path=str(out/f'aim-{width}.png'));page.mouse.up()
  page.wait_for_function('(()=>{pandaProbe("state");return pandaState.phase==="flight"})()',timeout=3000);page.wait_for_timeout(250);page.evaluate('onklavGameAction("pause")');page.wait_for_timeout(100);a=probe();ticks=a['ticks'];page.wait_for_timeout(250);a=probe();assert a['paused']and a['ticks']==ticks and a['music_paused'],a
  page.evaluate('onklavGameAction("pause")');page.wait_for_timeout(600);a=probe();page.screenshot(path=str(out/f'impact-{width}.png'))
  page.wait_for_function('(()=>{pandaProbe("state");return pandaState.phase!=="flight"})()',timeout=20000);a=probe();assert a['total'][0]==sum(v['out']for v in a['blocks'])and not a['charges'][2],a
  # Midflight JSON state survives with no repeated launch cue.
  probe('shoot_setup');click('Heavy');click('Launch');page.wait_for_timeout(150);before=probe();restored=probe('json_restore');assert page.evaluate('pandaRestored')and restored['shots']==1 and len(restored['audio'])>=len(before['audio']),restored
  page.wait_for_function('(()=>{pandaProbe("state");return pandaState.phase!=="flight"})()',timeout=20000)
  # Build using drag-and-drop, then rotate/reposition and fill the remaining inventory.
  probe('build_setup');a=probe();sx,sy=a['pieces'][0];dx,dy=a['deck'];page.mouse.move(sx,sy);page.mouse.down();page.mouse.move(dx,dy,steps=12);page.mouse.up();a=probe();assert a['placed']==1,a;page.screenshot(path=str(out/f'build-{width}.png'))
  px,py=a['pieces'][0];page.touchscreen.tap(px,py);assert probe()['placed']==1
  click('Random');a=probe();assert a['placed']==6,a;page.screenshot(path=str(out/f'defense-{width}.png'));click('Reset');assert probe()['placed']==0;click('Ready');page.wait_for_timeout(200);assert probe()['leg']==1 and probe()['phase']in['shoot','flight'],probe()
  # Same SDK entry flow and saved game, including JSON-normalized draft/selection.
  probe('build_setup');click('Random');page.wait_for_function('(()=>{pandaProbe("state");return pandaState.phase==="build"&&pandaState.placed===6})()');expected=probe()['draft'];page.evaluate('onklavGameAction("return")');page.wait_for_function('''async (expected)=>{for(const info of await indexedDB.databases()){const db=await new Promise((resolve,reject)=>{const r=indexedDB.open(info.name);r.onsuccess=()=>resolve(r.result);r.onerror=reject});if(db.objectStoreNames.contains('FILE_DATA')){const values=await new Promise((resolve,reject)=>{const t=db.transaction('FILE_DATA','readonly');const r=t.objectStore('FILE_DATA').getAll();r.onsuccess=()=>resolve(r.result);r.onerror=reject});for(const row of values){try{const data=JSON.parse(new TextDecoder().decode(row.contents));if(data.state?.game==='panda'&&data.state?.draft===expected&&data.state?.model?.phase==='build'){db.close();return true}}catch(e){}}}db.close()}return false}''',arg=expected,timeout=8000);page.reload();page.wait_for_function('typeof pandaProbe==="function"');a=probe();assert [v['text']for v in a['dialogs']]==['New','Resume']and a['placed']==6,a;button=next(v for v in a['dialogs']if v['text']=='Resume');page.touchscreen.tap(*button['point']);page.wait_for_timeout(100);assert not probe()['paused']
  probe('shoot_setup');probe('mute');click('Rocket');click('Launch');page.wait_for_timeout(200);assert page.evaluate('__rms()')<.0001;probe('unmute')
  probe('final_setup');click('Continue');page.wait_for_timeout(350);a=probe();assert a['winner']==0 and a['report']and a['report_title']=='Champion!',a;page.screenshot(path=str(out/f'champion-{width}.png'))
  count=len(a['audio']);a=probe('json_restore');assert page.evaluate('pandaRestored')and a['winner']==0 and len(a['audio'])==count,a
  assert not errors,errors
  results.append({'viewport':[width,height],'original_music_rms':music,'touch_pull_aim_launch':True,'pause_freezes_physics_music':True,'shockwave_consumes_charge_and_real_score':True,'midflight_json_restore':True,'drag_rotate_reposition_reserve':True,'random_reset_fill':True,'saved_draft_new_resume':True,'master_mute':True,'errors':errors});c.close()
 b.close()
server.shutdown();(out/'solo-browser.json').write_text(json.dumps(results,indent=2));print('PANDA SOLO PHONE FLOWS PASSED')
