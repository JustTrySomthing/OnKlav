"""Actual exported-game touch and bottom-button replay checks; test-only state injection."""
from pathlib import Path
import http.server,threading,os,json,time
from playwright.sync_api import sync_playwright
w=Path(os.environ['ONKLAV_GAME_CHECK_DIR'])
class Handler(http.server.SimpleHTTPRequestHandler):
 def __init__(self,*a,**k):super().__init__(*a,directory=str(w/'web'),**k)
 def log_message(self,*a):pass
server=http.server.ThreadingHTTPServer(('127.0.0.1',0),Handler);threading.Thread(target=server.serve_forever,daemon=True).start()
rows=[]
def run():
 with sync_playwright() as p:
  browser=p.chromium.launch(executable_path=os.environ['CHROMIUM_BIN'],args=['--no-sandbox','--use-angle=swiftshader','--enable-unsafe-swiftshader','--disable-dev-shm-usage','--autoplay-policy=no-user-gesture-required'])
  for game,width,height in [('hockey',360,780),('hockey',640,360),('four',360,780),('blackjack',360,780),('dominos',360,780),('panda',360,780),('naval',360,780)]:
   if game not in os.environ.get("ONKLAV_GAME_CHECK_TITLES","hockey,four,blackjack,dominos,panda,naval").split(","):continue
   context=browser.new_context(viewport={'width':width,'height':height},has_touch=True);page=context.new_page();errors=[];menus=[];saves=[];discarded=False;finished_saved=False
   def console(m):
    if m.text.startswith('ONKLAV_SAVE|'):
     nonlocal discarded,finished_saved
     q=json.loads(m.text.split('|',1)[1]);saves.append(q['op']);reply={'ok':True,'exists':False}
     if q['slot']=='session':
      if q['op']=='clear':discarded=True
      if q['op']=='save' and discarded:reply={'ok':False,'error':'This session was discarded.'}
      if q['op']=='save' and q['state'].get('match',{}).get('phase')==2 and not q['resumable']:finished_saved=True
     page.evaluate('([id,r])=>setTimeout(()=>onklavSaveReplies[id]=r,20)',[q['request'],reply])
    elif m.text.startswith('ONKLAV_GAME_START_MENU'):menus.append(m.text)
    elif 'SCRIPT ERROR:' in m.text:errors.append(m.text)
   page.on('console',console);page.on('pageerror',lambda e:errors.append(str(e)))
   page.add_init_script('window.onklavHosted=true;window.onklavEntryProtocol=1;window.onklavFreshStart=true;window.onklavLaunchAction="new";window.onklavSaveReplies={};window.onklavSaveBoot={protocol:2,ok:true,known:false,exists:false,slot:"resume",game:'+json.dumps(game)+'};')
   page.goto(f'http://127.0.0.1:{server.server_port}/index.html?game={game}');page.wait_for_function('typeof gameTest==="function"',timeout=30000)
   def probe(command='state'):
    page.evaluate('(c)=>gameTest(c)',command);return page.evaluate('gameTestState')
   def wait(fn,seconds=20):
    end=time.monotonic()+seconds
    while time.monotonic()<end:
     state=probe()
     if fn(state):return state
     page.wait_for_timeout(80)
    raise AssertionError((game,'timeout',probe(),errors))
   if game=='hockey':
    state=wait(lambda s:any(b['text']=='Normal' for b in s['buttons']))
    page.touchscreen.tap(*next(b['point'] for b in state['buttons'] if b['text']=='Normal'))
   if game in ['four','dominos']:
    state=wait(lambda s:any(b['text']=='Play' for b in s['buttons']))
    page.touchscreen.tap(*next(b['point'] for b in state['buttons'] if b['text']=='Play'))
   wait(lambda s:not s['paused'] and not s['dialog'])
   if game=='hockey':
    button=next(b for b in probe()['buttons'] if b['text']=='Serve');page.touchscreen.tap(*button['point']);wait(lambda s:s['phase']==1)
    state=probe();x,y,rw,rh=state['rink'];cdp=context.new_cdp_session(page)
    def touch(kind,px=0):cdp.send('Input.dispatchTouchEvent',{'type':kind,'touchPoints':[] if kind=='touchEnd' else [{'x':x+rw*px,'y':y+rh*.8,'id':1,'force':1}]})
    touch('touchStart',.2);left=wait(lambda s:s['touches']>=0);assert abs(left['target']-state['target'])<.01,left
    touch('touchMove',.8);right=wait(lambda s:s['target']>.7 and s['player']>left['player']+40);touch('touchEnd')
   if game=='four':
    for col in [5,4,6,3]:
     state=wait(lambda s:s['can_play'] or s['finished'])
     if state['finished']:break
     page.touchscreen.tap(*state['columns'][col]);wait(lambda s:not s['can_play'])
     state=wait(lambda s:s['can_play'] or s['finished'])
    assert not state['finished'] or state['winner']!=0,state
   assert not menus,(game,menus)
   # Completion setup isolates the actual result button; the click and restart are production code.
   state=probe('complete');page.wait_for_timeout(180);state=probe()
   if game=='hockey':wait(lambda _:finished_saved and not discarded)
   candidates=[b for b in state['buttons'] if b['text'].lower() in ['play again','new game'] and not b['disabled']]
   assert candidates,(game,state,errors)
   def restarted(s):
    if s['paused'] or s['dialog']:return False
    if game=='hockey':return s['phase']==0
    if game=='four':return not s['finished'] and s['wins']==[0,0] and not any(s['board'])
    if game=='blackjack':return not s['finished'] and s['round']==1
    if game=='dominos':return s['phase']=='playing' and s.get('scores')==[0,0]
    if game=='panda':return s['phase']=='aim'
    return s['phase']=='setup'
   page.touchscreen.tap(*candidates[-1]['point']);state=wait(restarted)
   assert not menus and not errors,(game,menus,errors)
   rows.append({'game':game,'viewport':[width,height],'direct_result_replay':True,'no_entry_or_help_dialog':True,'save_acknowledged':'save' in saves,'touch_paddle':game=='hockey','opening_AI_blocks':game=='four'})
   print('PASS',game,width,height,flush=True);page.remove_listener('console',console);context.close()
  browser.close()
run()
server.shutdown();(w/'browser-results.json').write_text(json.dumps(rows,indent=2)+'\n');print('GAME_REPAIR_BROWSER_PASS')
