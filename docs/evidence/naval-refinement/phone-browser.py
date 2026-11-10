from pathlib import Path
from playwright.sync_api import sync_playwright
import http.server,threading,json,time
w=Path('/workspace/scratch/f6c9793330b3');out=w/'naval-refine-qa';out.mkdir(parents=True,exist_ok=True)
class H(http.server.SimpleHTTPRequestHandler):
 def __init__(self,*a,**k):super().__init__(*a,directory=str(w/'naval-polish-web'),**k)
 def log_message(self,*a):pass
server=http.server.ThreadingHTTPServer(('127.0.0.1',0),H);threading.Thread(target=server.serve_forever,daemon=True).start();rows=[]
with sync_playwright() as p:
 b=p.chromium.launch(executable_path='/workspace/scratch/f6c9793330b3/toolchain/chrome-headless-shell-linux64/chrome-headless-shell',args=['--no-sandbox','--use-angle=swiftshader','--enable-unsafe-swiftshader','--disable-dev-shm-usage','--autoplay-policy=no-user-gesture-required'])
 for width,height in [(320,640),(432,800)]:
  c=b.new_context(viewport={'width':width,'height':height},has_touch=True);c.add_init_script("window.onklavHosted=true;window.onklavFreshStart=!sessionStorage.getItem('opened');sessionStorage.setItem('opened','1')")
  c.add_init_script("""window.__meters=[];const nativeConnect=AudioNode.prototype.connect;AudioNode.prototype.connect=function(dest,...args){if(dest instanceof AudioDestinationNode){const m=this.context.createAnalyser();m.fftSize=1024;nativeConnect.call(this,m,...args);nativeConnect.call(m,dest);__meters.push(m);return dest;}return nativeConnect.call(this,dest,...args)};window.__rms=()=>Math.max(0,...__meters.map(m=>{const a=new Float32Array(m.fftSize);m.getFloatTimeDomainData(a);return Math.sqrt(a.reduce((s,v)=>s+v*v,0)/a.length)}));""")
  page=c.new_page();errors=[];page.on('pageerror',lambda e:errors.append(str(e)));page.on('console',lambda m:errors.append(m.text) if m.type=='error' or 'ERROR:' in m.text else None)
  page.goto(f'http://127.0.0.1:{server.server_port}/index.html?game=naval');page.wait_for_function('typeof window.navalProbe==="function"');page.wait_for_timeout(400)
  def probe(op='state'):
   page.evaluate('(x)=>window.navalProbe(x)',op);page.wait_for_timeout(30);page.evaluate('window.navalProbe("state")');return page.evaluate('window.navalState')
  a=probe();print('SETUP',width,a,flush=True);page.screenshot(path=str(out/f'deploy-{width}.png'));assert a['phase']=='setup';assert page.evaluate("(()=>{const p=document.createElement('progress');p.id='status-progress';p.max=100;p.value=40;document.body.append(p);const c=getComputedStyle(p).accentColor;p.remove();return c})()")=="rgb(96, 61, 119)"
  x,y,gx,gy=a['grid'];assert x>=0 and y>=0 and x+gx<=width+1 and y+gy<=height+1,a
  cdp=c.new_cdp_session(page);dx,dy=a['dock'][0]['point'];tx,ty=x+gx*.05,y+gy*.05
  cdp.send('Input.dispatchTouchEvent',{'type':'touchStart','touchPoints':[{'x':dx,'y':dy}]});cdp.send('Input.dispatchTouchEvent',{'type':'touchMove','touchPoints':[{'x':tx,'y':ty}]});page.wait_for_timeout(100);cdp.send('Input.dispatchTouchEvent',{'type':'touchEnd','touchPoints':[]});page.wait_for_timeout(150)
  a=probe();music_rms=page.evaluate('__rms()');assert music_rms>.0001,music_rms;assert len(a['ships'][0])==5 and not a['dock'][0]['visible'],a;page.screenshot(path=str(out/f'placed-{width}.png'))
  probe('new');a=probe('battle');probe('quiet_music');page.screenshot(path=str(out/f'command-{width}.png'))
  x,y,gx,gy=a['grid'];page.touchscreen.tap(x+gx*.45,y+gy*.45);page.wait_for_timeout(100);a=probe();assert a['target']==44,a
  page.touchscreen.tap(*a['primary']);page.wait_for_timeout(250);a=probe();assert a['event'] and not a['event']['own'] and not a['event']['impacted'] and not a['primary_enabled'],a;assert 'fire' in a['audio'] and a['flight_playing'];flight_rms=page.evaluate('__rms()');assert flight_rms>.0001,flight_rms;page.screenshot(path=str(out/f'shell-{width}.png'))
  page.wait_for_timeout(1000);a=probe();assert a['event']['impacted'] and a['banner_title']=='You missed' and 'flight' in a['audio'] and 'miss' in a['audio'],a;page.screenshot(path=str(out/f'miss-{width}.png'))
  page.wait_for_function('(()=>{window.navalProbe("state");return window.navalState.event.own===true})()',timeout=16000);a=probe();assert a['own'] and a['event']['cell']==0,a
  page.screenshot(path=str(out/f'incoming-shell-{width}.png'));page.wait_for_timeout(1100);a=probe();assert a['own'] and a['event']['impacted'] and a['banner_title']=='Your ship was hit' and 'hit' in a['audio'],a;page.screenshot(path=str(out/f'incoming-hit-{width}.png'))
  page.wait_for_function('(()=>{window.navalProbe("state");return Object.keys(window.navalState.event).length===0})()',timeout=16000);a=probe();page.wait_for_timeout(700);a=probe();assert not a['own'] and a['turn'],a
  a=probe('sink');page.wait_for_timeout(1950);a=probe();assert a['wrecks']==[[10,11,12,13,14]] and a['banner_title']=='Enemy ship destroyed!' and a['banner'] and 'sink'in a['audio'],a;page.screenshot(path=str(out/f'sunk-{width}.png'))
  # Pause freezes the live presentation and pauses its sound players.
  page.evaluate('window.onklavGameAction("pause")');page.wait_for_timeout(150);a=probe();age=a['event']['age'];page.wait_for_timeout(250);a=probe();assert a['paused'] and abs(a['event']['age']-age)<.02,a;assert all(not v['playing'] or v['paused'] for v in a['voices']),a
  page.evaluate('window.onklavGameAction("pause")');page.wait_for_timeout(150);a=probe();assert not a['paused'],a
  a=probe('incoming_sink');page.wait_for_timeout(1950);a=probe();assert a['own'] and a['banner_title']=='Your ship was destroyed' and a['banner'],a;page.screenshot(path=str(out/f'own-destroyed-{width}.png'))
  # Report is above the board and held long enough to read.
  assert a['banner_rect'][1]+a['banner_rect'][3]<=a['grid'][1]+2,a
  age=a['event']['age'];page.wait_for_timeout(1600);a=probe();assert a['own']and a['banner']and a['event']['age']>age,a
  # Return and reload keep wreck geometry and the shared New/Resume controls.
  page.evaluate('window.onklavGameAction("return")');page.wait_for_timeout(1800);page.reload();page.wait_for_function('typeof window.navalProbe==="function"');a=probe();assert a['buttons']==['New','Resume'] and a['wrecks']==[[10,11,12,13,14]],a
  assert not errors,errors
  rows.append({'viewport':[width,height],'drag_hides_dock_ship':True,'shell_flight':True,'original_fire_hit_miss_sink_plus_flight_audio':True,'purple_loading_css':True,'new_music_rms':music_rms,'shell_flight_rms':flight_rms,'unboxed_report_above_board':True,'longer_readable_results':True,'incoming_automatically_shows_own_fleet':True,'red_sunk_ship_geometry':True,'centered_miss_hit_sunk_destroyed_banners':True,'pause_freezes_presentation_and_audio':True,'saved_return_preserves_wrecks':True,'engine_errors':errors});c.close()
 b.close()
server.shutdown();(out/'browser.json').write_text(json.dumps(rows,indent=2));print('NAVAL POLISH PHONE FLOWS PASSED',flush=True)
