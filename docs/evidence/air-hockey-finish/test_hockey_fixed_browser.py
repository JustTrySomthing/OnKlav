import http.server,threading,json,functools
from pathlib import Path
from playwright.sync_api import sync_playwright
w=Path('/workspace/scratch/b5e698131b7f');folder=w/'hockey-finish-fixture-export'
class Handler(http.server.SimpleHTTPRequestHandler):
 def __init__(self,*a,**k):super().__init__(*a,directory=str(folder),**k)
 def end_headers(self):
  self.send_header('Content-Security-Policy',"default-src 'none'; script-src 'self' 'unsafe-inline' 'unsafe-eval' 'wasm-unsafe-eval'; connect-src 'self'; img-src 'self' data: blob:; media-src 'self' blob:; style-src 'self' 'unsafe-inline'; font-src 'self'; worker-src 'self' blob:; frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'")
  super().end_headers()
 def log_message(self,*args):pass
server=http.server.ThreadingHTTPServer(('127.0.0.1',0),Handler);threading.Thread(target=server.serve_forever,daemon=True).start();records=[]
with sync_playwright()as p:
 b=p.chromium.launch(executable_path=str(w/'toolchain/chrome-headless-shell-linux64/chrome-headless-shell'),args=['--no-sandbox','--use-angle=swiftshader','--enable-unsafe-swiftshader','--disable-dev-shm-usage','--autoplay-policy=no-user-gesture-required'])
 for width,height in [(432,740),(320,560)]:
  ctx=b.new_context(viewport={'width':width,'height':height},has_touch=True);page=ctx.new_page();messages=[];errors=[]
  page.on('console',lambda m:messages.append({'type':m.type,'text':m.text}));page.on('pageerror',lambda e:errors.append(str(e)))
  page.goto(f'http://127.0.0.1:{server.server_port}/index.html?game=hockey');page.wait_for_function('typeof window.hockeyProbe === "function"',timeout=30000)
  def op(value):
   page.evaluate('(op) => window.hockeyProbe(op)',value);return page.evaluate('window.hockeyState')
  assert op('state')['phase']==0
  page.touchscreen.tap(width/2,height-30);page.wait_for_timeout(150);assert op('state')['phase']==1
  cdp=ctx.new_cdp_session(page)
  cdp.send('Input.dispatchTouchEvent',{'type':'touchStart','touchPoints':[{'x':width*.3,'y':height*.72}]})
  cdp.send('Input.dispatchTouchEvent',{'type':'touchMove','touchPoints':[{'x':width*.73,'y':height*.72}]})
  page.wait_for_timeout(100);cdp.send('Input.dispatchTouchEvent',{'type':'touchEnd','touchPoints':[]});assert op('state')['target']>.65
  op('pause');page.wait_for_timeout(100);a=op('state');page.wait_for_timeout(150);assert op('state')['puck']==a['puck']
  assert op('resume')['paused'];page.wait_for_timeout(450);assert not op('state')['paused']
  op('pause');state=op('stress');assert state['stress_count']==2300 and state['zero_groups']==5000
  op('restart');page.wait_for_timeout(450);assert op('state')['phase']==0
  result=op('result');assert result['scores']==[7,3] and result['result']
  page.wait_for_timeout(100);page.screenshot(path=str(w/f'hockey-fixed-result-{width}.png'))
  op('restart');assert op('state')['paused'];page.wait_for_timeout(450);assert not op('state')['paused']
  op('quit');page.wait_for_timeout(800)
  assert any(m['text']=='ONKLAV_GAME_EXIT'for m in messages),messages
  engine_errors=[m for m in messages if m['type']=='error' or 'ERROR:'in m['text'] or 'SCRIPT ERROR:'in m['text']]
  rec={'viewport':[width,height],'zero_groups':5000,'nonempty_elements':2300,'touch_target_verified':True,'pause_resume_restart_result_verified':True,'clean_exit_marker':True,'javascript_errors':errors,'engine_errors':engine_errors,'console':messages};records.append(rec)
  assert not errors and not engine_errors,rec
  ctx.close()
 b.close()
server.shutdown();(w/'hockey-fixed-browser.json').write_text(json.dumps(records,indent=2));print('Two actual WebAssembly flows passed; empty/nonempty task groups, touch, pause/resume/restart/result and clean shutdown verified')
