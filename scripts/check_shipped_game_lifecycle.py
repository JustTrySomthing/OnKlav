#!/usr/bin/env python3
"""Exercise saved-session return/resume on unmodified APK game exports in Chromium."""
import argparse, http.server, io, json, re, tempfile, threading, time
from pathlib import Path
from urllib.parse import urlsplit
from PIL import Image
from playwright.sync_api import sync_playwright
from check_shipped_game_viewports import extract_game_exports

ROOT=Path(__file__).resolve().parents[1]
GAMES=['hockey','four','blackjack','dominos','panda','naval','shroom','last-light']

def wait(page,check,seconds=20):
    until=time.monotonic()+seconds
    while time.monotonic()<until:
        if check():return
        page.wait_for_timeout(80)
    raise AssertionError('Expected game state did not arrive before the deadline')

def colored_button(page,color):
    """Locate a broad, flat-colored Play button, not scenery pixels."""
    image=Image.open(io.BytesIO(page.locator('canvas').screenshot())).convert('RGB')
    px=image.load();runs=[]
    for y in range(image.height//4, image.height*9//10, 3):
        start=None
        for x in range(image.width//8,image.width*7//8):
            match=max(abs(a-b) for a,b in zip(px[x,y],color))<=3
            if match and start is None:start=x
            if not match and start is not None:
                if x-start>=65:runs.append((start,x,y))
                start=None
    if len(runs)<5:return None
    widest=max(end-start for start,end,y in runs)
    rows=[r for r in runs if r[1]-r[0]>=widest-12]
    left,right,y=rows[len(rows)//2]
    return ((left+right)//2,y)

def main():
    ap=argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--apk',required=True,type=Path);ap.add_argument('--chromium',required=True)
    ap.add_argument('--output',required=True,type=Path);ap.add_argument('--games',nargs='+',choices=GAMES,default=GAMES)
    ap.add_argument('--expect-legacy-hockey',action='store_true',help='Reproduce the missing ready signal in 0.101.54 only')
    args=ap.parse_args();args.output.parent.mkdir(parents=True,exist_ok=True)
    policy=(ROOT/'android/app/src/main/kotlin/app/onklav/home/GameWebPolicy.kt').read_text()
    csp=re.search(r'const val CSP="([^"\n]+)"',policy).group(1)
    bootstrap=re.search(r'const val BOOTSTRAP="""(.*?)"""',policy,re.S).group(1)
    rows=[]
    with tempfile.TemporaryDirectory(prefix='onklav-game-lifecycle-') as work,sync_playwright() as p:
        assets=Path(work);extract_game_exports(args.apk,assets)
        class Handler(http.server.SimpleHTTPRequestHandler):
            def __init__(self,*args,**kwargs):super().__init__(*args,directory=str(assets),**kwargs)
            def log_message(self,*args):pass
            def end_headers(self):
                self.send_header('Content-Security-Policy',csp)
                self.send_header('X-Content-Type-Options','nosniff')
                self.send_header('Cache-Control','no-store')
                super().end_headers()
        server=http.server.ThreadingHTTPServer(('127.0.0.1',0),Handler)
        threading.Thread(target=server.serve_forever,daemon=True).start()
        origin=f'http://127.0.0.1:{server.server_port}'
        browser=p.chromium.launch(executable_path=args.chromium,args=['--no-sandbox','--use-angle=swiftshader','--enable-unsafe-swiftshader','--disable-dev-shm-usage','--autoplay-policy=no-user-gesture-required'])
        try:
            for game in args.games:
                wide=game in ['shroom','last-light'];size={'width':1000,'height':600} if wide else {'width':360,'height':780}
                folder=assets/(game if wide else 'builtin');context=browser.new_context(viewport=size,has_touch=True)
                store={};errors=[];events=[];writes=[];missing=[]
                def route(r):
                    if r.request.url.startswith(origin+'/') and r.request.method=='GET':r.continue_()
                    else:missing.append(urlsplit(r.request.url).path);r.abort()
                context.route('**/*',route)
                def launch(action):
                    page=context.new_page()
                    def console(msg):
                        text=msg.text
                        if text.startswith('ONKLAV_SAVE|'):
                            q=json.loads(text.split('|',1)[1]);slot=q['slot'];reply={'ok':True}
                            if q['op']=='save':store[slot]={'state':q['state'],'resumable':q['resumable']};writes.append(slot)
                            elif q['op']=='clear':store.pop(slot,None)
                            elif q['op']=='load':reply.update(exists=slot in store,record=store.get(slot))
                            page.evaluate('([id,value])=>setTimeout(()=>onklavSaveReplies[id]=value,0)',[q['request'],reply])
                        elif text.startswith('ONKLAV_'):events.append(text)
                        elif 'SCRIPT ERROR:' in text or 'violates the following Content Security Policy' in text or 'Refused to ' in text:errors.append(text)
                    page.on('console',console);page.on('pageerror',lambda e:errors.append(str(e)))
                    boot={'protocol':2,'ok':True,'known':True,'exists':'session' in store,'record':store.get('session'),'slot':'resume','game':game}
                    flags={'onklavSaveBoot':boot,'onklavSaveReplies':{},'onklavHosted':True,'onklavEntryProtocol':1,'onklavLaunchAction':action,'onklavFreshStart':action=='new','onklavFriend':None}
                    page.add_init_script(bootstrap+'\nObject.assign(window,'+json.dumps(flags)+');')
                    page.on('response',lambda response:missing.append(urlsplit(response.url).path) if response.status>=400 and not response.url.endswith('/favicon.ico') else None)
                    page.goto(origin+'/'+folder.name+'/index.html?game='+game,timeout=30000)
                    page.wait_for_function('typeof window.onklavGameAction==="function"',timeout=30000)
                    page.wait_for_timeout(180)
                    return page
                try:
                    page=launch('new')
                    if game=='hockey':page.touchscreen.tap(180,374)
                    if game=='four':
                        point=None
                        def cat_play():
                            nonlocal point
                            point=colored_button(page,(23,96,77));return point is not None
                        wait(page,cat_play,12);page.touchscreen.tap(*point)
                    if game=='shroom':
                        point=None
                        def shroom_play():
                            nonlocal point
                            point=colored_button(page,(39,98,81));return point is not None
                        wait(page,shroom_play,40);page.touchscreen.tap(*point)
                        wait(page,lambda:store.get('session',{}).get('state',{}).get('phase')=='combat',20)
                        before=store['session']['state']['position']
                        page.keyboard.down('d');page.wait_for_timeout(800);page.keyboard.up('d')
                        page.evaluate("onklavGameAction('host_pause')")
                        wait(page,lambda:store['session']['state']['position']!=before,8)
                    if game=='last-light':
                        def start_train():
                            page.keyboard.press('Enter')
                            return bool(store.get('session')) and not store['session']['state'].get('intro_pending',False)
                        wait(page,start_train,40)
                    ready='ONKLAV_SESSION_READY' in events
                    assert ready != (game=='hockey' and args.expect_legacy_hockey),f'Unexpected readiness marker: {ready}'
                    page.evaluate("onklavGameAction('return')")
                    wait(page,lambda:'ONKLAV_GAME_RETURN' in events and bool(store.get('session')),20)
                    assert store['session']['resumable'],'No resumable checkpoint'
                    page.close();events.clear();before_writes=len(writes)
                    page=launch('resume')
                    page.evaluate("onklavGameAction('return')")
                    wait(page,lambda:'ONKLAV_GAME_RETURN' in events and len(writes)>before_writes,20)
                    assert 'ONKLAV_GAME_START_MENU' not in events,'Unexpected native menu during resume'
                    assert not errors and not missing,(errors,missing)
                    rows.append({'game':game,'passed':True,'startup_ready_signal':ready,'saved_return':True,'resumed_and_saved_again':True,'shroom_movement_verified':game=='shroom','train_intro_play_verified':game=='last-light'})
                except Exception as e:
                    rows.append({'game':game,'passed':False,'error':str(e),'events':events,'writes':writes,'console_errors':errors,'missing_paths':missing})
                finally:context.close()
                args.output.write_text(json.dumps({'scope':'Unmodified APK exports, Android CSP/bootstrap and simulated save acknowledgements in Chromium; not Android WebView hardware','checks':rows},indent=2)+'\n')
                print(game,'PASS' if rows[-1]['passed'] else 'FAIL',flush=True)
        finally:browser.close();server.shutdown();server.server_close()
    if not all(row['passed'] for row in rows):raise SystemExit(1)

if __name__=='__main__':main()
