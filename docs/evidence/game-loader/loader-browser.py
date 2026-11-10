from pathlib import Path
import zipfile,threading,http.server,json
from playwright.sync_api import sync_playwright
w=Path('/workspace/scratch/f6c9793330b3');r=w/'project/OnKlav';out=w/'splash-qa';out.mkdir(exist_ok=True)
with zipfile.ZipFile(r/'developer/games/engine-template.zip')as z:html=z.read('godot.html').decode()
html=html.replace('$GODOT_PROJECT_NAME','Games').replace('$GODOT_SPLASH_COLOR','#603D77').replace('$GODOT_SPLASH_CLASSES','show-image--true').replace('$GODOT_SPLASH','index.png').replace('$GODOT_URL','mock.js').replace('$GODOT_CONFIG','{}').replace('$GODOT_THREADS_ENABLED','false').replace('$GODOT_HEAD_INCLUDE','')
(out/'index.html').write_text(html);(out/'custom.html').write_text(html.replace('</head>','<meta name="onklav-loader" content="custom"></head>'))
(out/'mock.js').write_text('''class Engine {
 static getMissingFeatures(){return []}
 start(){window.engineStartedAt=performance.now();return Promise.resolve()}
 startGame(options){window.mockOptions=options;const mode=new URL(location.href).searchParams.get('case');
  if(mode==='fast'){options.onProgress(100,100);return this.start()}
  if(mode==='fail')return new Promise((r,j)=>setTimeout(()=>j(new Error('Could not load the game files. Try opening the game again.')),300));
  options.onProgress(0,0);setTimeout(()=>options.onProgress(10,100),200);setTimeout(()=>options.onProgress(60,100),450);setTimeout(()=>options.onProgress(100,100),800);
  return new Promise(r=>setTimeout(r,mode==='slow'?2500:1000)).then(()=>this.start());
 }}''')
(out/'index.png').write_bytes((r/'games/engine-checks/onklav-splash.png').read_bytes())
class H(http.server.SimpleHTTPRequestHandler):
 def __init__(self,*a,**k):super().__init__(*a,directory=str(out),**k)
 def log_message(self,*a):pass
server=http.server.ThreadingHTTPServer(('127.0.0.1',0),H);threading.Thread(target=server.serve_forever,daemon=True).start();rows=[]
with sync_playwright()as p:
 b=p.chromium.launch(executable_path=str(w/'toolchain/chrome-headless-shell-linux64/chrome-headless-shell'),args=['--no-sandbox','--autoplay-policy=no-user-gesture-required'])
 for width,height in [(320,640),(432,800),(740,360)]:
  c=b.new_context(viewport={'width':width,'height':height});page=c.new_page();errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
  page.add_init_script('''window.audioCount=0;const Audio=window.AudioContext;window.AudioContext=function(...a){window.audioCount++;window.cueContext=new Audio(...a);return window.cueContext};const connect=AudioNode.prototype.connect;AudioNode.prototype.connect=function(target,...args){if(target instanceof AudioDestinationNode){window.cueAnalyser=this.context.createAnalyser();connect.call(this,window.cueAnalyser);return connect.call(window.cueAnalyser,target)}return connect.call(this,target,...args)}''')
  page.goto(f'http://127.0.0.1:{server.server_port}/index.html?case=slow');page.wait_for_timeout(340)
  a=page.evaluate('''()=>{const logo=document.querySelector('.onklav-logo');const buffer=new Float32Array(2048);if(window.cueAnalyser)cueAnalyser.getFloatTimeDomainData(buffer);return {background:getComputedStyle(document.querySelector('#status')).backgroundColor,width:logo.getBoundingClientRect().width,base:parseFloat(getComputedStyle(logo).width),percent:document.querySelector('#onklav-loading-percent').textContent,audio:Math.max(...buffer.map(Math.abs))}}''');assert a['background']=='rgb(96, 61, 119)'and a['percent']=='10%'and a['audio']>0,a
  page.wait_for_timeout(400);peak=page.locator('.onklav-logo').bounding_box();assert peak['width']>a['base']*1.05,(a,peak);page.screenshot(path=str(out/f'approach-{width}.png'))
  page.wait_for_timeout(740);settled=page.locator('.onklav-logo').bounding_box();assert abs(settled['width']-a['base'])<1
  assert all(float(x)==1 for x in page.locator('.onklav-word span').evaluate_all('(nodes)=>nodes.map(n=>getComputedStyle(n).opacity)'))
  page.screenshot(path=str(out/f'settled-{width}.png'));track=page.locator('#onklav-loading').bounding_box();assert track['y']+track['height']<height-10
  page.wait_for_function('window.engineStartedAt>0');page.wait_for_function('!document.getElementById("status")');assert page.evaluate('engineStartedAt')>=2450;assert not errors,errors
  rows.append({'viewport':[width,height],'real_progress_percent':True,'forward_overshoot_settle':True,'legible_final_logo_word_bar':True,'actual_sound_signal':True,'slow_load_waits_for_engine':True});c.close()
 # Reduced motion and mute cached by the game's canonical preference controls.
 for pref,media in [({'muted':True},'no-preference'),({'volume':0},'no-preference'),({'reduce_motion':True,'muted':True},'no-preference'),({'muted':True},'reduce')]:
  c=b.new_context(viewport={'width':320,'height':640},reduced_motion=media);page=c.new_page();page.add_init_script('localStorage.setItem("onklav-loader-preferences",'+json.dumps(json.dumps(pref))+');window.audioCount=0;const Audio=window.AudioContext;window.AudioContext=function(...a){window.audioCount++;return new Audio(...a)}');page.goto(f'http://127.0.0.1:{server.server_port}/index.html?case=fast');page.wait_for_function('window.engineStartedAt>0');elapsed=page.evaluate('engineStartedAt');assert page.evaluate('audioCount')==0
  if media=='reduce'or pref.get('reduce_motion'):assert elapsed<500,elapsed
  else:assert elapsed>=1400,elapsed
  c.close()
 # Failure stays visible, does not pretend success or animate forever.
 c=b.new_context();page=c.new_page();page.goto(f'http://127.0.0.1:{server.server_port}/index.html?case=fail');page.wait_for_timeout(550);assert page.locator('#status-notice').is_visible() and page.locator('#status').get_attribute('class')=='loader-error';assert not page.locator('#onklav-loading').is_visible();page.wait_for_timeout(1000);assert page.locator('#status').is_visible();c.close()
 # Explicit studio art keeps its own splash; the default OnKlav logo/text and sound are suppressed.
 c=b.new_context();page=c.new_page();page.goto(f'http://127.0.0.1:{server.server_port}/custom.html');page.wait_for_timeout(200);assert page.locator('#status-splash').is_visible()and not page.locator('#onklav-brand').is_visible();page.wait_for_function('window.engineStartedAt>0');assert page.evaluate('engineStartedAt')<1400;c.close();b.close()
server.shutdown();(out/'results.json').write_text(json.dumps({'visual_cases':rows,'muted_and_zero_volume_silent':True,'system_and_cached_reduced_motion':True,'no_fake_completion_on_error':True,'studio_brand_opt_out':True,'browser_autoplay_cue_measured':True},indent=2));print('LOADER ANIMATION, REAL PROGRESS, AUDIO, MUTE, REDUCED MOTION, ERROR AND CUSTOM BRAND CHECKS PASS')
