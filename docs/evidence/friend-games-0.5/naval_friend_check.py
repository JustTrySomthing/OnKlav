from pathlib import Path
from playwright.sync_api import sync_playwright
import http.server,threading,json
w=Path('/workspace/scratch/b5e698131b7f')
class H(http.server.SimpleHTTPRequestHandler):
 def __init__(self,*a,**k):super().__init__(*a,directory=str(w/'naval-web-final'),**k)
 def log_message(self,*a):pass
server=http.server.ThreadingHTTPServer(('127.0.0.1',0),H);threading.Thread(target=server.serve_forever,daemon=True).start()
with sync_playwright()as p:
 b=p.chromium.launch(executable_path=str(w/'toolchain/chrome-headless-shell-linux64/chrome-headless-shell'),args=['--no-sandbox','--use-angle=swiftshader','--enable-unsafe-swiftshader','--disable-dev-shm-usage','--autoplay-policy=no-user-gesture-required']);pages=[];contexts=[];queue=[];errors=[];states=[]
 for i in range(2):
  c=b.new_context(viewport={'width':432,'height':740},has_touch=True);contexts.append(c);c.add_init_script('window.onklavHosted=true;window.onklavFriend='+json.dumps({'id':'a'*32,'host':i==0})+';window.onklavFreshStart=false;')
  pg=c.new_page();pg.on('pageerror',lambda e:errors.append(str(e)));pg.on('console',lambda m,i=i:queue.append((i,m.text.split('|',1)[1]))if m.text.startswith('ONKLAV_TURN|')else errors.append(m.text)if 'ERROR:'in m.text or m.type=='error'else None);pg.goto(f'http://127.0.0.1:{server.server_port}/index.html?game=naval');pg.wait_for_function('typeof navalProbe==="function"');pages.append(pg);states.append({'id':'a'*32,'status':'active','pending':False,'own':[],'events':[],'timeline':[]})
 def push(i):pages[i].evaluate('(s)=>{window.onklavTurnSnapshot=s;window.onklavTurnState(s)}',states[i])
 def pump():
  for t in range(30):
   for pg in pages:pg.wait_for_timeout(30)
   if not queue:continue
   i,data=queue.pop(0);j=1-i;seq=len(states[i]['own'])+1
   own={'sequence':seq,'data':data,'own':True};other={'sequence':seq,'data':data,'own':False}
   states[i]['own'].append(own);states[i]['timeline'].append(own);push(i)
   states[j]['events'].append(other);states[j]['timeline'].append(other);push(j)
 def probe(i,op='state'):
  pages[i].evaluate('(op)=>window.navalProbe(op)',op);pages[i].wait_for_timeout(40);return pages[i].evaluate('window.navalState')
 for i in range(2):push(i);probe(i,'random');probe(i,'primary');pump()
 a=probe(0);g=probe(1);assert a['phase']=='battle'and g['phase']=='battle'and a['turn']and not g['turn'],(a,g)
 for actor in [0,1,0,1]:
  a=probe(actor);x,y,sx,sy=a['grid'];n=len(a['shots']);pages[actor].touchscreen.tap(x+sx*(.05+.1*n),y+sy*.05);pages[actor].wait_for_timeout(60);probe(actor,'primary');pump();a=probe(actor);g=probe(1-actor);assert a['pending']==-1 and not a['turn']and g['turn'],(actor,a,g)
  # Repeated complete native history must not apply another shot.
  push(actor);push(1-actor);assert len(probe(actor)['shots'])==n+1
 pages[0].evaluate('window.onklavGameAction("return")');pages[0].wait_for_timeout(400);pages[0].reload();pages[0].wait_for_function('typeof navalProbe==="function"');a=probe(0);assert a['buttons']==['New','Resume'],a;probe(0,'entry_left') # New path is separately covered; reload must retain journal behavior before explicit discard.
 assert not errors,errors
 result={'two_isolated_browser_peers':True,'private_fleets_and_readiness':True,'four_alternating_sms_turns':True,'duplicate_history_ignored':True,'saved_return_prompt':True,'physical_carrier':False,'errors':errors};(w/'naval-friend-browser-results.json').write_text(json.dumps(result,indent=2));print(result)
 b.close()
server.shutdown()
