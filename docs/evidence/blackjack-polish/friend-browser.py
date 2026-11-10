from pathlib import Path
from playwright.sync_api import sync_playwright
import http.server,threading,json
w=Path('/workspace/scratch/f6c9793330b3');out=w/'project/OnKlav/docs/evidence/blackjack-polish'
class H(http.server.SimpleHTTPRequestHandler):
 def __init__(self,*a,**k):super().__init__(*a,directory=str(w/'blackjack-web'),**k)
 def log_message(self,*a):pass
server=http.server.ThreadingHTTPServer(('127.0.0.1',0),H);threading.Thread(target=server.serve_forever,daemon=True).start()
with sync_playwright()as p:
 b=p.chromium.launch(executable_path='/workspace/scratch/b5e698131b7f/toolchain/chrome-headless-shell-linux64/chrome-headless-shell',args=['--no-sandbox','--use-angle=swiftshader','--enable-unsafe-swiftshader','--disable-dev-shm-usage','--autoplay-policy=no-user-gesture-required']);pages=[];contexts=[];states=[];queue=[];errors=[]
 for i in range(2):
  c=b.new_context(viewport={'width':432,'height':740},has_touch=True);contexts.append(c);c.add_init_script('window.onklavHosted=true;window.onklavFreshStart=false;window.onklavFriend='+json.dumps({'id':'d'*32,'host':i==0}))
  page=c.new_page();page.on('pageerror',lambda e:errors.append(str(e)));page.on('console',lambda m,i=i:queue.append((i,m.text.split('|',1)[1]))if m.text.startswith('ONKLAV_TURN|')else errors.append(m.text)if m.type=='error'or'ERROR:'in m.text else None);page.goto(f'http://127.0.0.1:{server.server_port}/index.html?game=blackjack');page.wait_for_function('typeof blackjackProbe==="function"');pages.append(page);states.append({'id':'d'*32,'status':'active','pending':False,'own':[],'events':[],'timeline':[]})
 def push(i):pages[i].evaluate('(s)=>{window.onklavTurnSnapshot=s;window.onklavTurnState(s)}',states[i])
 def probe(i,op='state'):
  pages[i].evaluate('(op)=>blackjackProbe(op)',op);pages[i].wait_for_timeout(15);pages[i].evaluate('blackjackProbe("state")');return pages[i].evaluate('blackjackState')
 def pump():
  for _ in range(8):
   for pg in pages:pg.wait_for_timeout(15)
   if not queue:continue
   i,data=queue.pop(0);j=1-i;seq=len(states[i]['own'])+1;own={'sequence':seq,'data':data,'own':True};remote={'sequence':seq,'data':data,'own':False};states[i]['own'].append(own);states[i]['timeline'].append(own);push(i);states[j]['events'].append(remote);states[j]['timeline'].append(remote);push(j)
 def idle():
  for pg in pages:pg.wait_for_function('(()=>{blackjackProbe("state");return !blackjackState.busy})()',timeout=7000)
 for i in range(2):push(i)
 idle();a=probe(0);g=probe(1);assert a['hands']==g['hands'] and a['ready']and not g['ready'] and a['hidden'][1]==[True,False]and g['hidden'][0]==[True,False],(a,g)
 pages[0].screenshot(path=str(out/'friend-table.png'))
 for hand in range(1,11):
  a=probe(0);assert a['round']==hand and a['turn']==(hand-1)%2,a
  while not a['hand_over']:
   actor=a['turn'];receiver=1-actor;before_audio=0
   if hand==1 and actor==0:
    pages[receiver].evaluate('onklavGameAction("pause")');pages[receiver].wait_for_timeout(80);before_audio=len(probe(receiver)['audio'])
   own=probe(actor);assert own['actions']['Stand']['enabled'],own;pages[actor].touchscreen.tap(*own['actions']['Stand']['point']);pages[actor].wait_for_timeout(30);pump()
   if hand==1 and actor==0:
    held=probe(receiver);assert held['event']['age']==0 and not held['event']['launched']and len(held['audio'])==before_audio,held
    pages[receiver].evaluate('onklavGameAction("pause")');pages[receiver].wait_for_timeout(50)
   idle();a=probe(0)
  g=probe(1);assert a['hands']==g['hands']and a['wins']==g['wins']and len(a['history'])==hand and a['revealed']and g['revealed'],(a,g)
  count=(len(a['audio']),len(g['audio']));push(0);push(1);assert (len(probe(0)['audio']),len(probe(1)['audio']))==count
  if hand==1:
   for i in range(2):probe(i,'reduced')
  if hand<10:
   a=probe(0);assert a['actions']['Deal next hand']['enabled']and not probe(1)['actions']['Waiting for host to deal']['enabled'],a
   pages[0].touchscreen.tap(*a['actions']['Deal next hand']['point']);pages[0].wait_for_timeout(30);pump();idle()
 a=probe(0);g=probe(1);assert a['finished']and g['finished']and a['winner']==g['winner']and sum(a['wins'])<=10,(a,g)
 pages[0].screenshot(path=str(out/'friend-final.png'))
 for i in range(2):
  a=probe(i,'legacy_friend');assert pages[i].evaluate('blackjackLegacyFriendOk') and a['finished']and a['round']==10,a
 pages[0].evaluate('onklavGameAction("return")');pages[0].wait_for_timeout(500);pages[0].reload();pages[0].wait_for_function('typeof blackjackProbe==="function"');a=probe(0);assert [v['text']for v in a['buttons']]==['New','Resume']and a['finished']and a['round']==10 and not a['audio'],a
 push(0);assert not probe(0)['invalid']and probe(0)['wins']==g['wins']
 assert not errors,errors
 result={'two_isolated_players':True,'ten_complete_hands':True,'alternating_starting_player':True,'hidden_opponent_card_and_reveal':True,'paused_arrival_silent_until_resume':True,'only_host_deals_next_hand':True,'wins_and_history_match':True,'duplicate_delivery_no_replayed_cues':True,'reduced_motion_rounds':True,'legacy_friend_journal_and_saved_return':True,'real_carrier':False,'errors':errors};(out/'friend-browser.json').write_text(json.dumps(result,indent=2));print(result)
 b.close()
server.shutdown()
