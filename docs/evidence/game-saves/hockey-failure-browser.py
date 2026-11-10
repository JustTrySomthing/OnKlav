exec(open('save_browser.py').read().split('rows=[]')[0])
from playwright.sync_api import sync_playwright
rows=[]
with sync_playwright()as p:
 b=p.chromium.launch(executable_path=str(w/'toolchain/chrome-headless-shell-linux64/chrome-headless-shell'),args=['--no-sandbox','--use-angle=swiftshader','--enable-unsafe-swiftshader','--disable-dev-shm-usage','--autoplay-policy=no-user-gesture-required'])
 for width,height in [(320,640),(432,800)]:
  c=b.new_context(viewport={'width':width,'height':height},has_touch=True);page=c.new_page();fail=False;saved=None;errors=[]
  def console(m):
   global saved
   if m.text.startswith('ONKLAV_SAVE|'):
    cmd=json.loads(m.text.split('|',1)[1]);response={'ok':not fail}
    if fail:response['error']='Injected disk-full condition'
    elif cmd['op']=='save':saved=copy.deepcopy(cmd['state'])
    page.evaluate('([id,r])=>setTimeout(()=>window.onklavSaveReplies[id]=r,250)',[cmd['request'],response])
   elif m.type=='error' or 'SCRIPT ERROR:'in m.text:errors.append(m.text)
  page.on('console',console)
  page.add_init_script('window.onklavHosted=true;window.onklavFreshStart=true;window.onklavSaveReplies={};window.onklavSaveBoot={protocol:2,ok:true,known:false,exists:false,slot:"resume"}')
  page.goto(f'http://127.0.0.1:{server.server_port}/index.html?game=hockey');page.wait_for_function('typeof saveProbe==="function"');page.wait_for_timeout(800)
  def state(op='state'):
   page.evaluate('(op)=>saveProbe(op)',op);page.wait_for_timeout(40);return page.evaluate('saveProbeState')
  assert saved and not state()['paused']
  before=copy.deepcopy(saved);fail=True;state('checkpoint');page.wait_for_timeout(450);a=state();assert a['paused'] and a['tree_paused'] and a['error'] and saved==before,a
  page.evaluate('onklavGameAction("pause")');page.wait_for_timeout(100);assert state()['paused']
  fail=False;state('checkpoint');page.wait_for_timeout(450);assert state()['error']==''
  page.evaluate('onklavGameAction("pause")');page.wait_for_timeout(150);a=state();assert not a['paused'] and not a['tree_paused'],a
  # Both controller pause systems remain usable after that failure and retry.
  page.evaluate('onklavGameAction("audio")');page.wait_for_timeout(100);a=state();assert a['paused'];button=next(v for v in a['dialogs']if v['text']=='Done');page.touchscreen.tap(*button['point']);page.wait_for_timeout(150);a=state();assert not a['paused']and not a['tree_paused'],a
  assert not errors,errors;rows.append({'viewport':[width,height],'failed_save_retains_previous':True,'resume_blocked_until_saved':True,'successful_retry_resumes_scene_and_rink':True,'sound_done_after_retry_resumes':True});c.close()
 b.close()
server.shutdown();(w/'save-hockey-failure-browser.json').write_text(json.dumps(rows,indent=2));print('HOCKEY SAVE FAILURE, RETRY, PLAY AND SOUND DONE PASS AT BOTH PHONE SIZES')
