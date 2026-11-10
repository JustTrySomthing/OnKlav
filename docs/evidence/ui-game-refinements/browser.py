from pathlib import Path
from playwright.sync_api import sync_playwright
import http.server,threading,json
w=Path.cwd();web=w/'refinements-web';qa=w/'refinements-qa';qa.mkdir(exist_ok=True)
class H(http.server.SimpleHTTPRequestHandler):
 def __init__(self,*a,**k):super().__init__(*a,directory=str(web),**k)
 def log_message(self,*a):pass
server=http.server.ThreadingHTTPServer(('127.0.0.1',0),H);threading.Thread(target=server.serve_forever,daemon=True).start();errors=[];results={}
with sync_playwright()as p:
 b=p.chromium.launch(executable_path=str(w/'toolchain/chrome-headless-shell-linux64/chrome-headless-shell'),args=['--no-sandbox','--use-angle=swiftshader','--enable-unsafe-swiftshader','--disable-dev-shm-usage','--autoplay-policy=no-user-gesture-required'])
 c=b.new_context(viewport={'width':432,'height':800},has_touch=True);c.add_init_script('window.onklavHosted=true;window.onklavFreshStart=true;');page=c.new_page();page.on('pageerror',lambda e:errors.append(str(e)));page.on('console',lambda m:errors.append(m.text)if 'SCRIPT ERROR:'in m.text or 'ERROR:'in m.text else None)
 def load(game):
  page.goto(f'http://127.0.0.1:{server.server_port}/index.html?game={game}');page.wait_for_function('typeof refinementProbe==="function"&&typeof onklavGameAction==="function"',timeout=30000);page.wait_for_function('!document.getElementById("status")');return probe('freeze')
 def probe(op='read',dt=0):return page.evaluate('([op,dt])=>{refinementProbe(op,dt);return refinementState}',[op,dt])
 load('four');probe('start');s=probe('cat_drop');assert s['active']['flight']>=.7,s
 s=probe('step',.8);assert s['active']['landed']and not s['can_play'],s
 s=probe('step',.5);assert s['active']and s['active']['actor']==0,s
 s=probe('step',.1);assert not s['active']and s['think']>=.9,s
 s=probe('step',.5);assert not s['active'],s
 s=probe('step',.5);assert s['active']['actor']==1,s
 results['cats']={'bottom_row_flight':.725,'landing_hold':.65,'ai_think_at_least':.9,'checks':'fall, hold, delay, rival move'}
 load('naval');probe('start');s=probe('naval_attack');assert s['own']and s['board_own']and s['disabled']and s['color']=='7ee9c8ff',s
 s=probe('step',1.2);assert s['active']['impacted']and s['own'],s
 s=probe('step',5);assert s['delay']>.5 and s['own']and s['color']=='7ee9c8ff',s
 s=probe('step',.4);assert s['own']and s['disabled'],s
 s=probe('step',.2);assert not s['own']and not s['board_own']and s['color']=='294c60ff',s
 results['naval']={'selected_disabled_tab_through_impact_and_hold':True,'selection_changes_with_board_only':True}
 load('dominos');probe('start');page.wait_for_timeout(500);page.screenshot(path=str(qa/'dominos432.png'))
 s=probe('dominos_play');assert s['flying'],s
 page.wait_for_timeout(100);page.screenshot(path=str(qa/'dominos-flight.png'))
 s=probe('step',.6);assert not s['flying'],s
 probe('burst');s=probe();assert s['rings']>0 and s['particles']>0,s
 before=s['model'];page.wait_for_timeout(300);assert probe()['model']==before
 for i in range(10):probe('burst')
 s=probe();assert s['rings']<=5 and s['particles']<=120,s
 probe('reduce');page.wait_for_timeout(850);s=probe();n=s['rings'];probe('burst');assert probe()['rings']==n
 page.set_viewport_size({'width':320,'height':640});page.wait_for_timeout(400);page.screenshot(path=str(qa/'dominos320.png'))
 results['dominos']={'flight_and_impact':True,'bounded_effects':True,'decoration_does_not_mutate_model':True,'reduced_motion_suppresses_burst':True,'viewport_widths':[432,320]}
 assert not errors,errors
 c.close();b.close()
server.shutdown();(w/'refinements-browser-results.json').write_text(json.dumps({'results':results,'errors':errors},indent=2));print('PACING, FLEET TAB LIFECYCLE, NEON EFFECTS AND REDUCED MOTION PASS')
