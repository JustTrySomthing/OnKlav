from pathlib import Path
from playwright.sync_api import sync_playwright
import http.server,threading,json
w=Path('/workspace/scratch/f6c9793330b3');out=w/'project/OnKlav/docs/evidence/cats-dogs-polish'
class H(http.server.SimpleHTTPRequestHandler):
 def __init__(self,*a,**k):super().__init__(*a,directory=str(w/'cats-web'),**k)
 def log_message(self,*a):pass
server=http.server.ThreadingHTTPServer(('127.0.0.1',0),H);threading.Thread(target=server.serve_forever,daemon=True).start()
with sync_playwright()as p:
 browser=p.chromium.launch(executable_path='/workspace/scratch/b5e698131b7f/toolchain/chrome-headless-shell-linux64/chrome-headless-shell',args=['--no-sandbox','--use-angle=swiftshader','--enable-unsafe-swiftshader','--disable-dev-shm-usage','--autoplay-policy=no-user-gesture-required']);pages=[];contexts=[];queue=[];errors=[];states=[]
 for actor in range(2):
  c=browser.new_context(viewport={'width':432,'height':740},has_touch=True);contexts.append(c);c.add_init_script('window.onklavHosted=true;window.onklavFreshStart=false;window.onklavFriend='+json.dumps({'id':'c'*32,'host':actor==0,'host_dog':True}))
  page=c.new_page();page.on('pageerror',lambda e:errors.append(str(e)));page.on('console',lambda m,i=actor:queue.append((i,m.text.split('|',1)[1]))if m.text.startswith('ONKLAV_TURN|')else errors.append(m.text)if m.type=='error'or'ERROR:'in m.text else None);page.goto(f'http://127.0.0.1:{server.server_port}/index.html?game=four');page.wait_for_function('typeof catsProbe==="function"');pages.append(page);states.append({'id':'c'*32,'status':'active','pending':False,'timeline':[],'own':[],'events':[]})
 def push(i):pages[i].evaluate('(s)=>{window.onklavTurnSnapshot=s;window.onklavTurnState(s)}',states[i])
 def probe(i,op='state'):
  pages[i].evaluate('(op)=>catsProbe(op)',op);pages[i].wait_for_timeout(20);pages[i].evaluate('catsProbe("state")');return pages[i].evaluate('catsState')
 def pump():
  for _ in range(20):
   for pg in pages:pg.wait_for_timeout(15)
   if not queue:continue
   i,data=queue.pop(0);j=1-i;seq=len(states[i]['own'])+1;own={'sequence':seq,'data':data,'own':True};remote={'sequence':seq,'data':data,'own':False}
   states[i]['own'].append(own);states[i]['timeline'].append(own);push(i);states[j]['events'].append(remote);states[j]['timeline'].append(remote);push(j)
 for i in range(2):push(i)
 a=probe(0);g=probe(1);assert a['me']==0 and g['me']==1 and a['host_dog']and g['host_dog']and a['ready']and not g['ready'],(a,g)
 for turn,col in enumerate([0,1,0,1,0,1,0]):
  actor=turn%2;receiver=1-actor
  if turn==0:
   pages[receiver].evaluate('onklavGameAction("pause")');pages[receiver].wait_for_timeout(70);assert probe(receiver)['paused']
  a=probe(actor);x,y,ww,hh=a['grid'];pages[actor].touchscreen.tap(x+(col+.5)*ww/7,y+hh*.5);pages[actor].wait_for_timeout(50);pump()
  if turn==0:
   held=probe(receiver);assert held['event']['age']==0 and not held['event']['launched'] and not held['audio'],held
   pages[receiver].evaluate('onklavGameAction("pause")');pages[receiver].wait_for_timeout(50)
  for pg in pages:pg.wait_for_function('(()=>{catsProbe("state");return Object.keys(catsState.event).length===0})()',timeout=4000)
  a=probe(0);g=probe(1);assert a['board']==g['board'] and len(a['tape'])==turn+1 and len(g['tape'])==turn+1 and not a['invalid']and not g['invalid'],(a,g)
  before=(len(a['audio']),len(g['audio']));push(0);push(1);assert (len(probe(0)['audio']),len(probe(1)['audio']))==before
 a=probe(0);g=probe(1);assert a['winner']==0 and g['winner']==0 and a['line']==[14,21,28,35] and a['report_title']=='Dogs win!'and g['report_title']=='Dogs win!',(a,g)
 pages[0].screenshot(path=str(out/'friend-dogs-win.png'))
 for i in range(2):
  a=probe(i,'legacy_friend');assert pages[i].evaluate('catsLegacyFriendOk') and a['finished']and len(a['tape'])==7,a
 pages[0].evaluate('onklavGameAction("return")');pages[0].wait_for_timeout(450);pages[0].reload();pages[0].wait_for_function('typeof catsProbe==="function"');a=probe(0);assert [b['text']for b in a['buttons']]==['New','Resume']and len(a['tape'])==7 and a['host_dog'] and not a['audio'],a
 push(0);assert len(probe(0)['tape'])==7 and not probe(0)['invalid']
 assert not errors,errors
 result={'two_isolated_peers':True,'seven_alternating_turns':True,'host_can_be_dogs_guest_is_cats':True,'same_modern_board_in_both_modes':True,'paused_arrival_silent_until_resume':True,'winning_geometry_and_reports_match':True,'duplicate_journal_does_not_replay_sound_or_moves':True,'legacy_friend_tape_json_restores':True,'saved_friend_return_preserves_team_and_result':True,'physical_carrier':False,'errors':errors};(out/'friend-browser.json').write_text(json.dumps(result,indent=2));print(result)
 browser.close()
server.shutdown()
