from pathlib import Path
from playwright.sync_api import sync_playwright
import http.server,threading,json,copy
w=Path.cwd()
class H(http.server.SimpleHTTPRequestHandler):
 def __init__(self,*a,**k):super().__init__(*a,directory=str(w/'save-web'),**k)
 def log_message(self,*a):pass
server=http.server.ThreadingHTTPServer(('127.0.0.1',0),H);threading.Thread(target=server.serve_forever,daemon=True).start()
rows=[]
with sync_playwright()as p:
 b=p.chromium.launch(executable_path=str(w/'toolchain/chrome-headless-shell-linux64/chrome-headless-shell'),args=['--no-sandbox','--use-angle=swiftshader','--enable-unsafe-swiftshader','--disable-dev-shm-usage','--autoplay-policy=no-user-gesture-required'])
 c=b.new_context(viewport={'width':360,'height':760},has_touch=True);page=c.new_page();store={};identity='';commands=[];exits=[];returns=[];errors=[];inflight=False
 def console(m):
  text=m.text
  if text.startswith('ONKLAV_SAVE|'):
   cmd=json.loads(text.split('|',1)[1]);commands.append((identity,cmd['op'],cmd['slot']));key=(identity,cmd['slot']);response={'ok':True}
   if cmd['op']=='save':store[key]={'state':copy.deepcopy(cmd['state']),'resumable':cmd['resumable']}
   elif cmd['op']=='clear':store[key]={'state':{'_onklav_discarded':True},'resumable':False,'discarded':True}
   elif cmd['op']=='load':response.update({'exists':key in store,'record':store.get(key)})
   page.evaluate('([id,r])=>setTimeout(()=>window.onklavSaveReplies[id]=r,400)',[cmd['request'],response])
  elif text=='ONKLAV_GAME_EXIT':exits.append(identity)
  elif text=='ONKLAV_GAME_RETURN':returns.append(identity)
  elif 'SCRIPT ERROR:'in text or (m.type=='error' and 'Parse JSON'not in text):errors.append(text)
 page.on('console',console);page.on('pageerror',lambda e:errors.append(str(e)))
 def op(value='state'):
  page.evaluate('(v)=>saveProbe(v)',value);page.wait_for_timeout(30);return page.evaluate('saveProbeState')
 def load(game,action):
  global identity
  identity=game;record=store.get((game,'session'));boot={'protocol':2,'ok':True,'known':record is not None,'exists':record is not None and not record.get('discarded',False),'record':record,'game':game,'slot':'resume'}
  script='window.onklavHosted=true;window.onklavEntryProtocol=1;window.onklavLaunchAction='+json.dumps(action)+';window.onklavSaveBoot='+json.dumps(boot)+';window.onklavSaveReplies={};window.onklavFreshStart='+str(action=='new').lower()+';'
  page.add_init_script(script);page.goto(f'http://127.0.0.1:{server.server_port}/index.html?game={game}');page.wait_for_function('typeof saveProbe==="function"',timeout=30000);page.wait_for_timeout(650)
 def press(name):
  s=op();button=next(x for x in s['dialogs']if x['text']==name);page.touchscreen.tap(*button['point'])
 for game in ['panda','dominos','hockey','naval','four','blackjack']:
  load(game,'new');s=op();assert not s['dialogs'] and not s['paused'] and not s['error'],(game,s)
  page.evaluate('onklavGameAction("pause")');page.wait_for_timeout(500)
  op('profile');page.wait_for_timeout(500)
  page.evaluate('onklavGameAction("return")');page.wait_for_timeout(100);assert game not in returns
  page.wait_for_timeout(650);assert returns[-1]==game
  assert store[(game,'session')]['resumable']
  load(game,'resume');s=op();assert not s['dialogs'] and not s['paused'],(game,s)
  page.evaluate('onklavGameAction("host_pause")');page.wait_for_timeout(450)
  page.evaluate('onklavGameAction("host_resume")');page.wait_for_timeout(150);s=op();assert s['paused'] and not s['dialogs'],(game,s)
  page.evaluate('onklavGameAction("resume")');page.wait_for_timeout(100)
  page.evaluate('onklavGameAction("close")');page.wait_for_timeout(500);assert any(x['text']=='Quit'for x in op()['dialogs'])
  press('Quit');page.wait_for_timeout(80);assert game not in exits
  # A focus-loss checkpoint arriving while clear awaits its host acknowledgement
  # must never re-create a discarded game.
  page.evaluate('onklavGameAction("host_pause")');op('checkpoint');page.wait_for_timeout(750)
  assert exits[-1]==game,(game,exits)
  assert store[(game,'session')].get('discarded'),(game,commands[-8:])
  assert store[(game,'data.profile')]['state']['inventory']==['sword','key']
  lastclear=max(i for i,x in enumerate(commands)if x==(game,'clear','session'))
  assert not any(x==(game,'save','session')for x in commands[lastclear+1:]),(game,commands[lastclear:])
  rows.append({'game':game,'start_without_runtime_dialog':True,'resume_without_second_dialog':True,'return_waits_for_save':True,'quit_waits_for_discard':True,'focus_loss_cannot_resurrect':True,'profile_preserved':True,'foreground_without_entry_dialog':True})
 assert not errors,errors
 b.close()
server.shutdown();(w/'entry-browser-results.json').write_text(json.dumps(rows,indent=2));print('SINGLE LAUNCH / RESUME / ACKNOWLEDGED QUIT PASS FOR ALL SIX GAMES')
