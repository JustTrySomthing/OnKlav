from pathlib import Path
from playwright.sync_api import sync_playwright
import http.server,threading,json
w=Path('/workspace/scratch/f6c9793330b3');out=w/'panda-qa'
class H(http.server.SimpleHTTPRequestHandler):
 def __init__(self,*a,**k):super().__init__(*a,directory=str(w/'panda-web'),**k)
 def log_message(self,*a):pass
server=http.server.ThreadingHTTPServer(('127.0.0.1',0),H);threading.Thread(target=server.serve_forever,daemon=True).start()
with sync_playwright()as p:
 browser=p.chromium.launch(executable_path='/workspace/scratch/b5e698131b7f/toolchain/chrome-headless-shell-linux64/chrome-headless-shell',args=['--no-sandbox','--use-angle=swiftshader','--enable-unsafe-swiftshader','--disable-dev-shm-usage','--autoplay-policy=no-user-gesture-required']);pages=[];contexts=[];states=[];queue=[];errors=[]
 for i in range(2):
  c=browser.new_context(viewport={'width':432,'height':800},has_touch=True);contexts.append(c);c.add_init_script('window.onklavHosted=true;window.onklavFreshStart=false;window.onklavFriend='+json.dumps({'id':'e'*32,'host':i==0}));page=c.new_page();page.on('pageerror',lambda e:errors.append(str(e)));page.on('console',lambda m,i=i:queue.append((i,m.text.split('|',1)[1]))if m.text.startswith('ONKLAV_TURN|')else errors.append(m.text)if m.type=='error'or'ERROR:'in m.text else None);page.goto(f'http://127.0.0.1:{server.server_port}/index.html?game=panda');page.wait_for_function('typeof pandaProbe==="function"');pages.append(page);states.append({'id':'e'*32,'status':'active','pending':False,'own':[],'events':[],'timeline':[]})
 def push(i):pages[i].evaluate('(s)=>{window.onklavTurnSnapshot=s;window.onklavTurnState(s)}',states[i])
 def probe(i,op='state'):
  pages[i].evaluate('(op)=>pandaProbe(op)',op);pages[i].wait_for_timeout(20);return pages[i].evaluate('pandaState')
 def pump():
  for _ in range(12):
   for pg in pages:pg.wait_for_timeout(20)
   if not queue:continue
   i,data=queue.pop(0);j=1-i;seq=len(states[i]['own'])+1;own={'sequence':seq,'data':data,'own':True};remote={'sequence':seq,'data':data,'own':False};states[i]['own'].append(own);states[i]['timeline'].append(own);push(i);states[j]['events'].append(remote);states[j]['timeline'].append(remote);push(j)
 def click(i,label):
  a=probe(i);assert a['actions'][label]['enabled'],a;pages[i].touchscreen.tap(*a['actions'][label]['point']);pages[i].wait_for_timeout(150)
 def wait_phase(i,phase):pages[i].wait_for_function('(phase)=>{pandaProbe("state");return pandaState.phase===phase&&!pandaState.report}',arg=phase,timeout=40000)
 def wait_ready(i):pages[i].wait_for_function('(()=>{pandaProbe("state");return pandaState.ready&&!pandaState.report})()',timeout=12000)
 for i in range(2):push(i)
 click(0,'Panda');pump();wait_phase(1,'build');wait_ready(1);click(1,'Random');click(1,'Ready');pump();wait_phase(0,'shoot');wait_ready(0)
 # First player launches, second pauses before delivery; a future N stays ordered behind flight.
 pages[1].evaluate('onklavGameAction("pause")');pages[1].wait_for_timeout(100);silent=len(probe(1)['audio']);click(0,'Shockwave');click(0,'Launch');pump();held=probe(1);assert held['paused']and held['phase']=='shoot'and held['incoming']==1 and len(held['audio'])==silent,held
 probe(1,'json_restore');assert pages[1].evaluate('pandaRestored')and probe(1)['incoming']==1
 pages[0].wait_for_function('(()=>{pandaProbe("state");return pandaState.phase!=="flight"&&!pandaState.report})()',timeout=40000)
 if probe(0)['phase']=='shoot':probe(0,'timeout');pages[0].wait_for_timeout(100);pump();wait_phase(0,'review')
 wait_ready(0);click(0,'Continue');pump();assert probe(1)['incoming']>=2
 pages[1].evaluate('onklavGameAction("pause")');pages[1].wait_for_timeout(100);wait_phase(1,'build');assert probe(1)['leg']==1 and not probe(1)['invalid'],probe(1)
 a=probe(0);b=probe(1);assert a['total']==b['total']and a['turn']==b['turn']and a['leg']==b['leg'],(a,b)
 wait_ready(0);click(0,'Random');click(0,'Ready');pump();wait_phase(1,'shoot');wait_ready(1);click(1,'Heavy');click(1,'Launch');pump()
 for i in range(2):pages[i].wait_for_function('(()=>{pandaProbe("state");return pandaState.phase!=="flight"&&!pandaState.report})()',timeout=40000)
 a=probe(0);b=probe(1);assert a['blocks']==b['blocks']and a['total']==b['total'],(a,b)
 if b['phase']=='shoot':probe(1,'timeout');pages[1].wait_for_timeout(100);pump()
 wait_phase(0,'review');wait_phase(1,'review');a=probe(0);b=probe(1);assert a['total']==b['total']and a['round_wins']==b['round_wins']and a['leg']==1,(a,b)
 pages[0].screenshot(path=str(out/'friend-round.png'))
 audio=(len(a['audio']),len(b['audio']));push(0);push(1);assert (len(probe(0)['audio']),len(probe(1)['audio']))==audio
 wait_ready(0);click(0,'Continue');pump();wait_phase(1,'build');assert probe(1)['round']==1
 # A real saved friend build adopts native sequence counters and does not replay cues.
 wait_ready(1);click(1,'Random');pages[1].wait_for_function('(()=>{pandaProbe("state");return pandaState.placed===6})()');pages[1].evaluate('onklavGameAction("return")');pages[1].wait_for_timeout(500);pages[1].reload();pages[1].wait_for_function('typeof pandaProbe==="function"');a=probe(1);assert [v['text']for v in a['dialogs']]==['New','Resume']and a['placed']==6 and not a['audio'],a;push(1);assert not probe(1)['invalid']
 assert not errors,errors
 result={'isolated_players':True,'paired_round_and_reversed_builders':True,'same_fixed_step_final_blocks_and_scores':True,'paused_arrival_silent':True,'future_next_queued_behind_flight':True,'powers_and_timeout_turns':True,'duplicate_delivery_no_cues':True,'native_sequence_saved_return':True,'real_carrier_tested':False,'errors':errors};(out/'friend-browser.json').write_text(json.dumps(result,indent=2));print(result);browser.close()
server.shutdown()
