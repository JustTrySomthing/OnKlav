import http.server,threading,functools,json,time
from pathlib import Path
from playwright.sync_api import sync_playwright
r=Path('/workspace/scratch/b5e698131b7f/recovered/OnKlav')
b=r/'android/games/src/main/assets/godot-builtin';e=r/'android/games/src/main/assets/godot-web'
class Handler(http.server.SimpleHTTPRequestHandler):
 def __init__(self,*a,**k):super().__init__(*a,directory=str(b),**k)
 def translate_path(self,path):
  name=path.split('?',1)[0].lstrip('/')
  if (e/name).is_file():return str(e/name)
  return super().translate_path(path)
 def log_message(self,*a):pass
server=http.server.ThreadingHTTPServer(('127.0.0.1',0),Handler);threading.Thread(target=server.serve_forever,daemon=True).start()
records=[]
with sync_playwright() as p:
 browser=p.chromium.launch(executable_path='/workspace/scratch/b5e698131b7f/toolchain/chrome-headless-shell-linux64/chrome-headless-shell',args=['--no-sandbox','--use-angle=swiftshader','--enable-unsafe-swiftshader','--disable-dev-shm-usage'])
 for w,h in [(432,740),(320,560)]:
  ctx=browser.new_context(viewport={'width':w,'height':h},device_scale_factor=1,has_touch=True)
  page=ctx.new_page();errors=[];console=[]
  page.on('pageerror',lambda error:errors.append(str(error)))
  page.on('console',lambda msg:console.append({'type':msg.type,'text':msg.text}))
  page.goto(f'http://127.0.0.1:{server.server_port}/index.html?game=hockey')
  page.wait_for_timeout(5000)
  page.screenshot(path=f'/workspace/scratch/b5e698131b7f/recovered-hockey-{w}.png')
  page.touchscreen.tap(w/2,h-30);page.wait_for_timeout(700)
  cdp=ctx.new_cdp_session(page)
  cdp.send('Input.dispatchTouchEvent',{'type':'touchStart','touchPoints':[{'x':w*.25,'y':h*.72}]})
  cdp.send('Input.dispatchTouchEvent',{'type':'touchMove','touchPoints':[{'x':w*.72,'y':h*.72}]})
  page.wait_for_timeout(250)
  cdp.send('Input.dispatchTouchEvent',{'type':'touchEnd','touchPoints':[]})
  page.screenshot(path=f'/workspace/scratch/b5e698131b7f/recovered-hockey-playing-{w}.png')
  page.touchscreen.tap(w-75 if w==432 else w-66,30);page.wait_for_timeout(600)
  page.screenshot(path=f'/workspace/scratch/b5e698131b7f/recovered-hockey-pause-{w}.png')
  records.append({'viewport':[w,h],'javascript_errors':errors,'console':console,'canvas':page.locator('canvas').bounding_box()})
  ctx.close()
 browser.close()
server.shutdown()
Path('/workspace/scratch/b5e698131b7f/recovery-browser.json').write_text(json.dumps(records,indent=2))
assert all(not x['javascript_errors'] for x in records),records
print('Two actual WebAssembly layouts loaded; touch and pause interactions exercised; no JavaScript exceptions')
