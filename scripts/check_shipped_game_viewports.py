#!/usr/bin/env python3
"""Smoke-check shipped APK game exports in Chromium; this is not Android WebView/OEM qualification."""
import argparse, http.server, io, json, tempfile, threading, time, zipfile
from pathlib import Path
from playwright.sync_api import sync_playwright

def extract_game_exports(apk_path,root):
    with zipfile.ZipFile(apk_path) as apk:
        runtime={Path(n).name:apk.read(n) for n in apk.namelist() if n.startswith('assets/godot-web/') and not n.endswith('/')}
        builtin={Path(n).name:apk.read(n) for n in apk.namelist() if n.startswith('assets/godot-builtin/') and not n.endswith('/')}
        packages={'builtin':builtin}
        for name in ['shroom','last-light']:
            with zipfile.ZipFile(io.BytesIO(apk.read(f'assets/included-games/{name}.zip'))) as z:
                packages[name]={Path(n).name:z.read(n) for n in z.namelist() if n.startswith('web/') and not n.endswith('/')}
        for name,files in packages.items():
            folder=root/name;folder.mkdir()
            for filename,data in (runtime|files).items():(folder/filename).write_bytes(data)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk',required=True,type=Path)
    parser.add_argument('--output',required=True,type=Path)
    parser.add_argument('--chromium',default='/usr/bin/chromium')
    args=parser.parse_args();args.output.mkdir(parents=True,exist_ok=True)
    rows=[]
    with tempfile.TemporaryDirectory(prefix='onklav-shipped-viewports-') as temp:
        root=Path(temp)
        extract_game_exports(args.apk,root)
        class Handler(http.server.SimpleHTTPRequestHandler):
            def __init__(self,*a,**k):super().__init__(*a,directory=str(root),**k)
            def log_message(self,*a):pass
            def end_headers(self):
                self.send_header('Cross-Origin-Opener-Policy','same-origin')
                self.send_header('Cross-Origin-Embedder-Policy','require-corp')
                super().end_headers()
        server=http.server.ThreadingHTTPServer(('127.0.0.1',0),Handler)
        threading.Thread(target=server.serve_forever,daemon=True).start()
        try:
            with sync_playwright() as p:
                browser=p.chromium.launch(executable_path=args.chromium,args=['--no-sandbox','--use-angle=swiftshader','--enable-unsafe-swiftshader','--disable-dev-shm-usage','--autoplay-policy=no-user-gesture-required'])
                try:
                    for game in ['hockey','four','blackjack','dominos','panda','naval','shroom','last-light']:
                        for width,height in [(320,568),(800,360),(800,1280)]:
                            folder=game if game in ['shroom','last-light'] else 'builtin'
                            c=browser.new_context(viewport={'width':width,'height':height},has_touch=True)
                            page=c.new_page();errors=[];events=[]
                            def console(message):
                                body=message.text
                                if body.startswith('ONKLAV_'):events.append(body.split('|')[0])
                                if body.startswith('ONKLAV_SAVE|'):
                                    command=json.loads(body.split('|',1)[1]);response={'ok':True,'exists':False}
                                    page.evaluate('([id,r])=>setTimeout(()=>onklavSaveReplies[id]=r,0)',[command['request'],response])
                                if 'SCRIPT ERROR:' in body or 'ERROR: Failed to' in body:errors.append(body)
                            page.on('console',console);page.on('pageerror',lambda error:errors.append(str(error)))
                            page.add_init_script('window.onklavHosted=true;window.onklavEntryProtocol=1;window.onklavFreshStart=true;window.onklavLaunchAction="new";window.onklavSaveReplies={};window.onklavSaveBoot={protocol:2,ok:true,known:false,exists:false,slot:"resume",game:'+json.dumps(game)+'};')
                            started=time.monotonic()
                            try:
                                page.goto(f'http://127.0.0.1:{server.server_port}/{folder}/index.html?game={game}',timeout=30000)
                                page.wait_for_function('typeof window.onklavGameAction==="function"',timeout=30000)
                                page.wait_for_function('!!document.querySelector("canvas") && document.querySelector("canvas").width>0',timeout=5000)
                                page.wait_for_timeout(1200)
                                canvas=page.locator('canvas');box=canvas.bounding_box();assert box and box['width']>0 and box['height']>0
                                assert box['x']>=-1 and box['y']>=-1 and box['x']+box['width']<=width+1 and box['y']+box['height']<=height+1,box
                                assert not errors,errors
                                image=canvas.screenshot();from PIL import Image,ImageStat
                                rendered=Image.open(io.BytesIO(image)).convert('RGB');variation=max(ImageStat.Stat(rendered).stddev)
                                assert variation>2,'Blank or uniform game canvas'
                                if width==320:(args.output/f'{game}-{width}x{height}.png').write_bytes(image)
                                rows.append({'game':game,'viewport':[width,height],'passed':True,'render_seconds':round(time.monotonic()-started,2),'pixel_variation':round(variation,2),'events':sorted(set(events))})
                            except Exception as error:
                                rows.append({'game':game,'viewport':[width,height],'passed':False,'error':str(error),'console_errors':errors})
                            finally:c.close()
                            (args.output/'browser-game-results.json').write_text(json.dumps({'scope':'Shipped game exports in desktop Chromium with software WebGL; not Android WebView or phone GPU testing','checks':rows},indent=2)+'\n')
                            print(game,width,height,'PASS' if rows[-1]['passed'] else 'FAIL',flush=True)
                finally:browser.close()
        finally:server.shutdown();server.server_close()
    if any(not row['passed'] for row in rows):raise SystemExit(1)

if __name__=='__main__':main()
