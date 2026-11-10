from pathlib import Path
import os
from playwright.sync_api import sync_playwright
import http.server,threading,json,copy,time
w=Path(os.environ['ONKLAV_PANDA_CHECK_DIR']);shots=w/'panda-reboot-qa';shots.mkdir(exist_ok=True)
class Handler(http.server.SimpleHTTPRequestHandler):
 def __init__(self,*a,**k):super().__init__(*a,directory=str(w/'panda-reboot-web'),**k)
 def log_message(self,*a):pass
server=http.server.ThreadingHTTPServer(('127.0.0.1',0),Handler);threading.Thread(target=server.serve_forever,daemon=True).start()
rows=[]
with sync_playwright()as p:
 browser=p.chromium.launch(executable_path=str(Path(os.environ['CHROMIUM_BIN'])),args=['--no-sandbox','--use-angle=swiftshader','--enable-unsafe-swiftshader','--disable-dev-shm-usage','--autoplay-policy=no-user-gesture-required'])
 for width,height in json.loads(os.environ.get("ONKLAV_PANDA_VIEWPORTS","[[360,780],[320,640],[640,360]]")):
  context=browser.new_context(viewport={'width':width,'height':height},has_touch=True);page=context.new_page();saved={};errors=[];returned=[];profile_fault={"remaining":0};context_state={"closing":False}
  def console(m,current_page=page,guard=context_state,storage=saved,fault=profile_fault,return_log=returned,error_log=errors):
   if guard["closing"] or current_page.is_closed():return
   if m.text.startswith('ONKLAV_SAVE|'):
    command=json.loads(m.text.split('|',1)[1]);slot=command['slot'];response={'ok':True}
    if command['op']=='save':
     if slot=='data.panda_profile' and fault['remaining']>0:
      fault['remaining']-=1;response={'ok':False,'error':'Injected profile write failure'}
     else:storage[slot]={'state':copy.deepcopy(command['state']),'resumable':command['resumable']}
    elif command['op']=='clear':storage.pop(slot,None)
    elif command['op']=='load':response.update({'exists':slot in storage,'record':storage.get(slot)})
    current_page.evaluate('([id,r])=>setTimeout(()=>onklavSaveReplies[id]=r,90)',[command['request'],response])
   elif m.text=='ONKLAV_GAME_RETURN':return_log.append(True)
   elif 'SCRIPT ERROR:'in m.text or (m.type=='error' and 'Parse JSON'not in m.text):error_log.append(m.text)
  page.on('console',console);page.on('pageerror',lambda e:errors.append(str(e)))
  def probe():page.evaluate("pandaProbe('state')");return page.evaluate('pandaState')
  def wait_for(fn,timeout=20):
   end=time.monotonic()+timeout
   while time.monotonic()<end:
    state=probe()
    if fn(state):return state
    page.wait_for_timeout(80)
   raise AssertionError(('timeout',probe()))
  def load(action):
   record=saved.get('session');boot={'protocol':2,'ok':True,'known':record is not None,'exists':record is not None,'record':record,'slot':'resume','game':'panda'}
   page.add_init_script('window.onklavHosted=true;window.onklavEntryProtocol=1;window.onklavFreshStart='+str(action=='new').lower()+';window.onklavLaunchAction='+json.dumps(action)+';window.onklavSaveReplies={};window.onklavSaveBoot='+json.dumps(boot)+';')
   page.goto(f'http://127.0.0.1:{server.server_port}/index.html?game=panda');page.wait_for_function('typeof pandaProbe==="function"',timeout=30000);wait_for(lambda s:s['model']['phase']=='aim')
  def touch(kind,point=None):
   cdp.send('Input.dispatchTouchEvent',{'type':kind,'touchPoints':[]if point is None else[{'x':point[0],'y':point[1],'id':1,'force':1}]})
  load('new');s=wait_for(lambda s:s['can_shoot']);assert not s['dialogs'] and s['model']['boards'][0]['shots']==0;assert s['power']==0 and not s['stage_caption_visible'] and s['footer_size']>=18
  page.screenshot(path=str(shots/f'idle-{width}.png'))
  cdp=context.new_cdp_session(page)
  touch('touchStart',s['anchor']);page.wait_for_timeout(50);assert probe()['held']
  page.set_viewport_size({'width':width+20,'height':height});page.wait_for_timeout(200)
  assert not probe()['held'] and probe()['model']['boards'][0]['shots']==0
  touch('touchEnd');page.set_viewport_size({'width':width,'height':height});page.wait_for_timeout(200)
  s=probe();anchor=s['anchor'];low=s['low'];assert 0<anchor[1]<low[1]<height,(width,s)
  touch('touchStart',anchor)
  for step in range(1,9):
   t=step/8;touch('touchMove',[anchor[0],anchor[1]+(low[1]-anchor[1])*t]);page.wait_for_timeout(25)
  s=probe();assert s['held'] and s['power']>=98,(width,s)
  page.screenshot(path=str(shots/f'charge-{width}.png'));touch('touchEnd')
  s=wait_for(lambda s:s['model']['phase']=='flight');assert s['model']['boards'][0]['shots']==1
  page.evaluate('onklavGameAction("audio")');page.wait_for_timeout(160);s=probe();assert s['paused']
  ticks=s['model']['ticks'];page.wait_for_timeout(180);assert probe()['model']['ticks']==ticks
  button=next(b for b in probe()['dialogs']if b['text']=='Done');page.touchscreen.tap(*button['point']);wait_for(lambda s:not s['paused'])
  s=wait_for(lambda s:s['model']['phase']=='report',timeout=25);assert s['model']['actor']==0 and s['model']['shot_points']>0,s
  page.screenshot(path=str(shots/f'impact-{width}.png'));assert s['report_visible']
  for shot in [1,2]:
   s=wait_for(lambda s:s['model']['actor']==0 and s['model']['phase']=='aim' and s['can_shoot'])
   assert s['model']['boards'][0]['shots']==shot and s['model']['boards'][1]['shots']==0,s
   page.evaluate("pandaProbe('launch',42,10)")
   wait_for(lambda s:s['model']['phase']=='report')
  s=wait_for(lambda s:s['model']['actor']==1 and s['model']['phase']=='aim')
  assert s['model']['boards'][0]['shots']==3 and s['model']['boards'][1]['shots']==0 and s['cannon_color']=='1479dd',s
  page.screenshot(path=str(shots/f'rival-{width}.png'))
  s=wait_for(lambda s:s['model']['phase'] in ['round','finished'],timeout=65);assert s['continue_width']<=180;assert 'round 1' in s['result_detail'].lower()
  assert s['model']['boards'][1]['shots']>0 and not s['can_shoot'],s
  page.evaluate("pandaProbe('advance')")
  s=wait_for(lambda s:s['model']['stage']==1 and s['can_shoot'])
  assert s['music_playing']
  page.evaluate('onklavGameAction("return")');page.wait_for_timeout(400);assert returned
  old=copy.deepcopy(saved['session']['state']['model']);load('resume');s=wait_for(lambda s:s['can_shoot']);assert not s['dialogs']
  assert s['model']['boards']==old['boards'] and s['model']['totals']==old['totals']
  profile_fault['remaining']=1
  page.evaluate("pandaProbe('round_view')");page.wait_for_timeout(400);s=probe();assert s['next_visible']
  page.screenshot(path=str(shots/f'result-{width}.png'))
  wait_for(lambda s:s['paused'] and s['save_error'] and not s['profile_busy'])
  assert probe()['profile_dirty']
  page.evaluate("pandaProbe('retry_save')")
  wait_for(lambda s:not s['save_error'] and not s['profile_busy'] and not s['profile_dirty'])
  assert saved['data.panda_profile']['state']['stars'][1]>=2
  page.evaluate("pandaProbe('resume')");wait_for(lambda s:not s['paused'])
  page.evaluate("pandaProbe('advance')");s=wait_for(lambda s:s['model']['stage']==2 and s['can_shoot']);assert s['model']['phase']=='aim'
  assert not errors,errors
  rows.append({'viewport':[width,height],'real_touch_full_pull':True,'resize_cancels_pull_without_shot':True,'direct_start_and_resume':True,'complete_player_then_rival_volley':True,'center_shot_scores':True,'sound_pause_preserves_flight':True,'saved_towers_and_scores_preserved':True,'round_continues_without_building':True,'profile_write_failure_retry':True})
  context_state["closing"]=True
  page.remove_listener("console",console)
  context.close()
 browser.close()
server.shutdown();(w/'panda-reboot-browser-results.json').write_text(json.dumps(rows,indent=2));print('PANDA REBOOT TOUCH / TURN / SOUND / SAVE / ROUND CHECKS PASS')
