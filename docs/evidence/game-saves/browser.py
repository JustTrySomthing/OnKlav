from pathlib import Path
from playwright.sync_api import sync_playwright
import http.server,threading,json,time,copy
w=Path('/workspace/scratch/f6c9793330b3')
class H(http.server.SimpleHTTPRequestHandler):
 def __init__(self,*a,**k):super().__init__(*a,directory=str(w/'save-web'),**k)
 def log_message(self,*a):pass
server=http.server.ThreadingHTTPServer(('127.0.0.1',0),H);threading.Thread(target=server.serve_forever,daemon=True).start()
rows=[]
with sync_playwright()as p:
 b=p.chromium.launch(executable_path=str(w/'toolchain/chrome-headless-shell-linux64/chrome-headless-shell'),args=['--no-sandbox','--use-angle=swiftshader','--enable-unsafe-swiftshader','--disable-dev-shm-usage','--autoplay-policy=no-user-gesture-required'])
 for width,height in [(320,640),(432,800)]:
  c=b.new_context(viewport={'width':width,'height':height},has_touch=True);page=c.new_page();store={};game='';returned=[];errors=[];fail=False;last_commands=[]
  def console(m):
   nonlocal_dummy=None
   text=m.text
   if text.startswith('ONKLAV_SAVE|'):
    cmd=json.loads(text.split('|',1)[1]);key=(game,cmd['slot']);last_commands.append((game,cmd['op'],cmd['request']))
    response={'ok':True}
    if cmd['op']=='save':
     if fail:response={'ok':False,'error':'Storage full (injected)'}
     else:store[key]={'state':copy.deepcopy(cmd['state']),'resumable':cmd['resumable'],'payload_version':cmd['version']}
    elif cmd['op']=='clear':store[key]={'state':{},'resumable':False,'discarded':True}
    elif cmd['op']=='load':response.update({'known':key in store,'exists':key in store,'record':store.get(key)})
    page.evaluate('([id,result])=>setTimeout(()=>window.onklavSaveReplies[id]=result,350)',[cmd['request'],response])
   elif text=='ONKLAV_GAME_RETURN':returned.append(game)
   elif 'SCRIPT ERROR:'in text or (m.type=='error' and 'Parse JSON'not in text):errors.append(text)
  page.on('console',console);page.on('pageerror',lambda e:errors.append(str(e)))
  def op(value='state'):
   page.evaluate('(v)=>saveProbe(v)',value);page.wait_for_timeout(40);return page.evaluate('saveProbeState')
  def open_game(identity,fresh=None):
   global game
   game=identity;record=store.get((game,'session'));known=record is not None;exists=known and not record.get('discarded',False)
   boot={'protocol':2,'ok':True,'known':known,'exists':exists,'record':record,'game':identity,'slot':'resume'}
   script='window.onklavHosted=true;window.onklavSaveBoot='+json.dumps(boot)+';window.onklavSaveReplies={};window.onklavFreshStart='+str((not known)if fresh is None else fresh).lower()+';'
   page.add_init_script(script);page.goto(f'http://127.0.0.1:{server.server_port}/index.html?game={identity}');page.wait_for_function('typeof saveProbe==="function"',timeout=30000);page.wait_for_timeout(700)
  def press(text):
   a=op();button=next(v for v in a['dialogs']if v['text']==text);page.touchscreen.tap(*button['point']);page.wait_for_timeout(100)
  for identity in ['panda','dominos','hockey','naval','four','blackjack']:
   open_game(identity)
   for attempt in range(5):
    if store.get((identity,'session'),{}).get('resumable',False):break
    open_game(identity,fresh=True)
   a=op();assert not a['error'],a;assert (identity,'session')in store
   page.evaluate('onklavGameAction("pause")');page.wait_for_timeout(450);a=op();assert a['paused'],a
   op('checkpoint');page.wait_for_timeout(450);saved=copy.deepcopy(store[(identity,'session')]);previous_returns=len(returned)
   page.evaluate('onklavGameAction("return")');page.wait_for_timeout(100);assert len(returned)==previous_returns,(identity,returned)
   page.wait_for_timeout(600);assert returned[-1]==identity and op()['pending']==0
   preserved=copy.deepcopy(store)
   open_game(identity,fresh=False);a=op();assert a['paused'] and any(x['text']=='Resume'for x in a['dialogs']),a
   now=copy.deepcopy(a['state']);original=copy.deepcopy(saved['state']);original.pop('_onklav_mode',None)
   if identity=='hockey':original={k:original[k]for k in ['match','rink']}
   assert now==original,(identity,now,original)
   op('choose_new');assert any(x['text']=='Cancel'for x in op()['dialogs']);press('Cancel');page.wait_for_timeout(450);assert store[(identity,'session')]['state']==saved['state']
   for key,value in preserved.items():
    if key[0]!=identity:assert store[key]==value,('cross-game mutation',key)
   rows.append({'viewport':[width,height],'game':identity,'host_ack_before_return':True,'exact_resume':True,'new_cancel_preserves_save':True,'other_games_unchanged':True})
  # RPG-style permanent data is independent of the automatic resume slot.
  op('profile');page.wait_for_timeout(500);op('load_profile');page.wait_for_function('window.profileRead && window.profileRead.ok',timeout=4000);assert page.evaluate('profileRead.state.inventory')==['sword','key']
  # Failed new-game replacement must keep the old committed session and stop play.
  before=copy.deepcopy(store[('blackjack','session')]);fail=True;op('new');page.wait_for_timeout(650);a=op();assert a['paused'] and a['error'] and store[('blackjack','session')]==before,a
  assert any(x['text']=='Retry save'for x in a['dialogs']),a
  fail=False;page.evaluate('onklavGameAction("return")');page.wait_for_timeout(650);assert op()['error']==''
  assert not errors,errors;c.close()
 b.close()
server.shutdown();(w/'save-browser-results.json').write_text(json.dumps({'cases':rows,'permanent_rpg_data_roundtrip':True,'failed_new_preserves_committed_save':True,'failed_save_pauses_and_retry_succeeds':True,'console_errors':[]},indent=2));print('SIX GAME HOST SAVE/RESUME, CROSS-GAME ISOLATION, DURABILITY ACK AND FAILED REPLACEMENT PASSED')
