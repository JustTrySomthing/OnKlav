"""Real touch release/preview and fork defense checks on the exported production scene."""
from pathlib import Path
import os,json,time,threading,http.server
from playwright.sync_api import sync_playwright
w=Path(os.environ['ONKLAV_GAME_CHECK_DIR'])
class H(http.server.SimpleHTTPRequestHandler):
 def __init__(self,*a,**k):super().__init__(*a,directory=str(w/'web'),**k)
 def log_message(self,*a):pass
server=http.server.ThreadingHTTPServer(('127.0.0.1',0),H);threading.Thread(target=server.serve_forever,daemon=True).start()
rows=[]
with sync_playwright() as p:
 browser=p.chromium.launch(executable_path=os.environ['CHROMIUM_BIN'],args=['--no-sandbox','--use-angle=swiftshader','--enable-unsafe-swiftshader','--disable-dev-shm-usage','--autoplay-policy=no-user-gesture-required'])
 for width,height in [(360,780),(320,640)]:
  context=browser.new_context(viewport={'width':width,'height':height},has_touch=True);page=context.new_page();errors=[]
  def console(m):
   if m.text.startswith('ONKLAV_SAVE|'):
    q=json.loads(m.text.split('|',1)[1]);page.evaluate('([id,r])=>setTimeout(()=>onklavSaveReplies[id]=r,20)',[q['request'],{'ok':True,'exists':False}])
   elif 'SCRIPT ERROR:' in m.text:errors.append(m.text)
  page.on('console',console);page.on('pageerror',lambda e:errors.append(str(e)))
  page.add_init_script('window.onklavHosted=true;window.onklavEntryProtocol=1;window.onklavFreshStart=true;window.onklavLaunchAction="new";window.onklavSaveReplies={};window.onklavSaveBoot={protocol:2,ok:true,known:false,exists:false,slot:"resume",game:"four"};')
  page.goto(f'http://127.0.0.1:{server.server_port}/index.html?game=four');page.wait_for_function('typeof gameTest==="function"',timeout=30000)
  def state(command='state'):
   page.evaluate('(c)=>gameTest(c)',command);return page.evaluate('gameTestState')
  def wait(fn,timeout=25):
   end=time.monotonic()+timeout
   while time.monotonic()<end:
    s=state()
    if fn(s):return s
    page.wait_for_timeout(60)
   raise AssertionError((width,'timeout',state(),errors))
  wait(lambda s:s['can_play'] and not s['paused'])
  cdp=context.new_cdp_session(page)
  def touch(kind,point=None):cdp.send('Input.dispatchTouchEvent',{'type':kind,'touchPoints':[] if point is None else [{'x':point[0],'y':point[1],'id':7,'force':1}]})
  point=state()['columns'][3];touch('touchStart',point);s=wait(lambda s:s['pressed']);assert s['finger']>=0
  point=state()['columns'][4];touch('touchMove',point);wait(lambda s:s['hover']==4);touch('touchEnd')
  s=wait(lambda s:s['board'].count(1)==1);assert not s['pressed'] and s['hover']==-1,s
  page.mouse.move(*s['columns'][5]);page.wait_for_timeout(150);assert state()['hover']==-1
  # Repeated single taps, with no second click needed to commit any piece.
  for col in [1,6,3]:
   s=wait(lambda s:s['can_play'] or s['finished'])
   if s['finished']:break
   before=s['board'].count(1);page.touchscreen.tap(*s['columns'][col])
   s=wait(lambda s:s['board'].count(1)==before+1);assert not s['pressed'] and s['hover']==-1
  s=wait(lambda s:s['can_play'] or s['finished']);assert not s['finished'] or s['winner']!=0
  # A held preview is cancelled by pause, and resuming cannot commit that old gesture.
  if not s['finished']:
   before=s['board'].count(1);touch('touchStart',s['columns'][2]);wait(lambda s:s['pressed'])
   page.evaluate('onklavGameAction("pause")');s=wait(lambda s:s['paused']);assert not s['pressed'] and s['hover']==-1
   touch('touchEnd');page.evaluate('onklavGameAction("pause")');s=wait(lambda s:not s['paused']);assert s['board'].count(1)==before
  state('cats_prefix')
  s=wait(lambda s:s['board'].count(2)==2)
  assert s['board'][37]==2 or s['board'][40]==2,('failed fork prevention',s)
  assert s['nodes']<=4096 and s['depth']>=3,s
  assert not s['dialog'] and not errors,errors
  rows.append({'viewport':[width,height],'single_release_commits_once':True,'repeated_single_taps':True,'no_idle_floating_preview':True,'pause_cancels_held_preview':True,'reported_fork_prevented':True,'AI_completed_depth':s['depth'],'AI_nodes':s['nodes']})
  print('PASS',width,height,flush=True);page.remove_listener('console',console);context.close()
 browser.close()
server.shutdown();(w/'cats-input-ai-results.json').write_text(json.dumps(rows,indent=2)+'\n');print('CATS_INPUT_AI_BROWSER_PASS')
