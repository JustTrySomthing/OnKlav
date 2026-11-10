exec(open('/workspace/scratch/f6c9793330b3/final_games_browser.py').read().split('with sync_playwright()as p:')[0])
with sync_playwright()as p:
 b=p.chromium.launch(executable_path=str(w/'toolchain/chrome-headless-shell-linux64/chrome-headless-shell'),args=['--no-sandbox','--use-angle=swiftshader','--enable-unsafe-swiftshader','--disable-dev-shm-usage','--autoplay-policy=no-user-gesture-required']);pages=[];contexts=[];states=[];queue=[];errors=[]
 for i in range(2):
  c=b.new_context(viewport={'width':432,'height':800},has_touch=True);contexts.append(c);c.add_init_script('window.onklavHosted=true;window.onklavFreshStart=true;window.onklavFriend='+json.dumps({'id':'e'*32,'host':i==0,'panda_rules':2}));pg=c.new_page();pg.on('pageerror',lambda e:errors.append(str(e)));pg.on('console',lambda m,i=i:queue.append((i,m.text.split('|',1)[1]))if m.text.startswith('ONKLAV_TURN|')else errors.append(m.text)if m.type=='error'or'ERROR:'in m.text else None);pg.goto(f'http://127.0.0.1:{server.server_port}/index.html?game=panda');pg.wait_for_function('typeof controlsProbe==="function"');pages.append(pg);states.append({'id':'e'*32,'status':'active','pending':False,'timeline':[]})
 def probe(i,op='state'):
  pages[i].evaluate('(op)=>controlsProbe(op)',op);pages[i].wait_for_timeout(25);return pages[i].evaluate('controlsState')
 def push(i):pages[i].evaluate('(s)=>{window.onklavTurnSnapshot=s;window.onklavTurnState(s)}',states[i])
 def pump():
  for _ in range(8):
   for pg in pages:pg.wait_for_timeout(25)
   while queue:
    i,d=queue.pop(0);seq=sum(x['own']for x in states[i]['timeline'])+1
    for j in range(2):states[j]['timeline'].append({'sequence':seq,'data':d,'own':i==j});push(j)
 def ready(i):pages[i].wait_for_function('(()=>{controlsProbe("state");return controlsState.extra.local})()',timeout=15000)
 for i in range(2):push(i)
 ready(0);probe(0,'character');pump();ready(1);probe(1,'random');probe(1,'ready');pump();ready(0)
 assert probe(0)['snapshot']['model']['version']==2 and probe(1)['snapshot']['model']['version']==2
 pages[1].evaluate('onklavGameAction("pause")');pages[1].wait_for_timeout(80);held=probe(1)['snapshot']['model'];probe(0,'fire');pump();assert probe(1)['snapshot']['model']==held and len(probe(1)['snapshot']['incoming'])==1
 pages[1].evaluate('onklavGameAction("pause")')
 for i in range(2):pages[i].wait_for_function('(()=>{controlsProbe("state");return controlsState.extra.phase!=="flight"&&controlsState.snapshot.model.shots===1})()',timeout=20000)
 a=probe(0);z=probe(1);assert a['snapshot']['model']==z['snapshot']['model'],(a,z);assert not errors,errors
 (out/'friend-results.json').write_text(json.dumps({'rules':2,'eight_blocks':len(a['snapshot']['model']['blocks'])==8,'real_channel_intents':True,'paused_arrival_queued':True,'exact_model_after_physics':True,'errors':errors},indent=2));print('PANDA NEW-RULE FRIEND REPLAY PASSED');b.close()
server.shutdown()
