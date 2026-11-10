from pathlib import Path
from playwright.sync_api import sync_playwright
import http.server,threading,json
w=Path('/workspace/scratch/f6c9793330b3');out=w/'naval-refine-qa'
class H(http.server.SimpleHTTPRequestHandler):
 def __init__(self,*a,**k):super().__init__(*a,directory=str(w/'naval-polish-web'),**k)
 def log_message(self,*a):pass
server=http.server.ThreadingHTTPServer(('127.0.0.1',0),H);threading.Thread(target=server.serve_forever,daemon=True).start()
with sync_playwright()as p:
 b=p.chromium.launch(executable_path='/workspace/scratch/f6c9793330b3/toolchain/chrome-headless-shell-linux64/chrome-headless-shell',args=['--no-sandbox','--use-angle=swiftshader','--enable-unsafe-swiftshader','--disable-dev-shm-usage','--autoplay-policy=no-user-gesture-required']);pages=[];contexts=[];queue=[];errors=[];states=[]
 for i in range(2):
  c=b.new_context(viewport={'width':432,'height':740},has_touch=True);contexts.append(c);c.add_init_script('window.onklavHosted=true;window.onklavFriend='+json.dumps({'id':'b'*32,'host':i==0})+';window.onklavFreshStart=false;')
  pg=c.new_page();pg.on('pageerror',lambda e:errors.append(str(e)));pg.on('console',lambda m,i=i:queue.append((i,m.text.split('|',1)[1]))if m.text.startswith('ONKLAV_TURN|')else errors.append(m.text)if 'ERROR:'in m.text or m.type=='error'else None);pg.goto(f'http://127.0.0.1:{server.server_port}/index.html?game=naval');pg.wait_for_function('typeof navalProbe==="function"');pages.append(pg);states.append({'id':'b'*32,'status':'active','pending':False,'own':[],'events':[],'timeline':[]})
 def push(i):pages[i].evaluate('(s)=>{window.onklavTurnSnapshot=s;window.onklavTurnState(s)}',states[i])
 def pump():
  quiet=0
  for t in range(100):
   for pg in pages:pg.wait_for_timeout(15)
   if not queue:
    quiet+=1
    if quiet>=8:break
    continue
   quiet=0;i,data=queue.pop(0);j=1-i;seq=len(states[i]['own'])+1
   own={'sequence':seq,'data':data,'own':True};other={'sequence':seq,'data':data,'own':False}
   states[i]['own'].append(own);states[i]['timeline'].append(own);push(i)
   states[j]['events'].append(other);states[j]['timeline'].append(other);push(j)
 def probe(i,op='state'):
  pages[i].evaluate('(op)=>window.navalProbe(op)',op);pages[i].wait_for_timeout(20);pages[i].evaluate('window.navalProbe("state")');return pages[i].evaluate('window.navalState')
 for i in range(2):
  pages[i].evaluate('window.onklavGameAction("start")');pages[i].wait_for_timeout(30)
  # Shared start menu on a new friend match: click its actual first action.
  pages[i].evaluate('window.onklavGameAction("play")')
  push(i);probe(i,'random');probe(i,'primary');pump()
 a=probe(0);g=probe(1)
 # If the standard new-game dialog is still present, activate its Start action in the fixture only.
 assert a['phase']=='battle'and g['phase']=='battle'and a['turn']and not g['turn'],(a,g)
 targets=list(g['ships'][0]);safe=[n for n in range(100)if all(n not in ship for ship in a['ships'])]
 for index,cell in enumerate(targets):
  actor=0;a=probe(actor);x,y,sx,sy=a['grid'];pages[actor].touchscreen.tap(x+sx*((cell%10+.5)/10),y+sy*((cell//10+.5)/10));pages[actor].wait_for_timeout(50);probe(actor,'primary');pump()
  a=probe(0);g=probe(1);assert a['pending']==-1 and not a['turn']and g['turn'],(a,g)
  assert a['event'] and not a['event']['own'] and g['event']['own'] and g['own'],(a,g)
  assert a['queue']<=1 and g['queue']<=1
  for pg in pages:pg.wait_for_function('(()=>{navalProbe("state");return Object.keys(navalState.event).length===0&&navalState.view_delay===0})()',timeout=18000)
  if index==4:break
  # Opponent shoots open water, once while the receiving game is paused.
  if index==0:
   pages[0].evaluate('window.onklavGameAction("pause")');pages[0].wait_for_timeout(100);before=probe(0);assert before['paused'],before;count=len(before['audio'])
  a=probe(1);x,y,sx,sy=a['grid'];cell=safe[index];pages[1].touchscreen.tap(x+sx*((cell%10+.5)/10),y+sy*((cell//10+.5)/10));pages[1].wait_for_timeout(50);probe(1,'primary');pump()
  if index==0:
   paused=probe(0);assert paused['event']['age']==0 and not paused['event']['launched'] and len(paused['audio'])==count,paused
   pages[0].evaluate('window.onklavGameAction("pause")');pages[0].wait_for_timeout(100);assert probe(0)['event']['launched'];pump()
  for pg in pages:pg.wait_for_function('(()=>{navalProbe("state");return Object.keys(navalState.event).length===0&&navalState.view_delay===0})()',timeout=18000)
  assert probe(0)['turn'] and not probe(1)['turn']
 a=probe(0);g=probe(1);assert a['wrecks']==[sorted(targets)],a
 assert len(a['shots'])==5 and len(g['incoming'])==5
 tx=a['tx'];rx=g['rx'];push(0);push(1);assert probe(0)['tx']==tx and probe(1)['rx']==rx
 assert not errors,errors
 result={'two_isolated_peers':True,'nine_alternating_sms_turns':True,'confirmed_enemy_wreck_geometry':True,'incoming_view_switch':True,'paused_incoming_has_no_flight_or_audio_until_resumed':True,'duplicate_journal_does_not_replay_combat':True,'real_carrier_delivery':False,'errors':errors};(out/'friend-browser.json').write_text(json.dumps(result,indent=2));print(result)
 b.close()
server.shutdown()
