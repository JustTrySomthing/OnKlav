"""Compare one deterministic exported shot on baseline/fixed fixtures, not a phone benchmark."""
from pathlib import Path
import http.server,threading,os,time,json,statistics
from playwright.sync_api import sync_playwright

work=Path(os.environ['ONKLAV_PANDA_CHECK_DIR'])
class Handler(http.server.SimpleHTTPRequestHandler):
    def __init__(self,*args,**kwargs):super().__init__(*args,directory=str(work),**kwargs)
    def log_message(self,*args):pass
server=http.server.ThreadingHTTPServer(('127.0.0.1',0),Handler)
threading.Thread(target=server.serve_forever,daemon=True).start()
rows=[]
with sync_playwright() as p:
    browser=p.chromium.launch(executable_path=os.environ['CHROMIUM_BIN'],args=['--no-sandbox','--use-angle=swiftshader','--enable-unsafe-swiftshader','--disable-dev-shm-usage','--autoplay-policy=no-user-gesture-required'])
    for label,folder in [('baseline','panda-baseline-web'),('fixed','panda-reboot-web')]:
        page=browser.new_page(viewport={'width':360,'height':780});errors=[]
        def console(message):
            if message.text.startswith('ONKLAV_SAVE|'):
                request=json.loads(message.text.split('|',1)[1]);response={'ok':True,'exists':False}
                page.evaluate('([id,r])=>onklavSaveReplies[id]=r',[request['request'],response])
            elif 'SCRIPT ERROR:' in message.text:errors.append(message.text)
        page.on('console',console)
        page.add_init_script('window.onklavHosted=true;window.onklavEntryProtocol=1;window.onklavFreshStart=true;window.onklavLaunchAction="new";window.onklavSaveReplies={};window.onklavSaveBoot={protocol:2,ok:true,known:false,exists:false,slot:"resume",game:"panda"};')
        page.goto(f'http://127.0.0.1:{server.server_port}/{folder}/index.html?game=panda')
        page.wait_for_function('typeof pandaProbe==="function"',timeout=30000)
        def probe():
            page.evaluate("pandaProbe('state')");return page.evaluate('pandaState')
        deadline=time.monotonic()+10
        while not probe()['can_shoot']:
            assert time.monotonic()<deadline
            page.wait_for_timeout(50)
        page.evaluate("pandaProbe('course',11)");page.wait_for_timeout(350)
        page.evaluate('window.frameGaps=[];let last=performance.now();function collect(t){frameGaps.push(t-last);last=t;requestAnimationFrame(collect)};requestAnimationFrame(collect)')
        started=time.monotonic();page.evaluate("pandaProbe('launch',0,100)")
        while True:
            state=probe()
            if state['model']['phase']=='report':break
            assert time.monotonic()-started<45,(label,state['model']['ticks'])
            page.wait_for_timeout(40)
        elapsed=time.monotonic()-started;gaps=sorted(page.evaluate('frameGaps'))
        row={'variant':label,'viewport':[360,780],'course':12,'wall_seconds':round(elapsed,3),'simulation_seconds':round(state['model']['ticks']/240,3),'simulation_wall_ratio':round(state['model']['ticks']/240/elapsed,3),'score':state['model']['shot_points'],'ticks':state['model']['ticks'],'median_frame_ms':round(statistics.median(gaps),2),'p95_frame_ms':round(gaps[int(len(gaps)*.95)],2),'style_cache_entries':state['style_cache_size']}
        assert not errors,errors
        rows.append(row);page.remove_listener('console',console);page.close()
    browser.close()
server.shutdown()
assert rows[1]['ticks']<=1200 and rows[1]['score']>=0,rows # revised 120 Hz contact solver; exact resume is tested separately
assert rows[1]['simulation_wall_ratio']>=.85,rows
(work/'panda-pacing-results.json').write_text(json.dumps({'scope':'Software-rendered headless browser comparison, not Samsung performance acceptance','results':rows},indent=2)+'\n')
print(json.dumps(rows,indent=2))
