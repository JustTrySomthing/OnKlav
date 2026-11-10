exec(open('/workspace/scratch/f6c9793330b3/dominos_browser.py').read().split('with sync_playwright()as p:')[0])
with sync_playwright()as p:
 b=p.chromium.launch(executable_path=str(w/'toolchain/chrome-headless-shell-linux64/chrome-headless-shell'),args=['--no-sandbox','--use-angle=swiftshader','--enable-unsafe-swiftshader','--disable-dev-shm-usage','--autoplay-policy=no-user-gesture-required']);c=b.new_context(viewport={'width':432,'height':800},has_touch=True);c.add_init_script('window.onklavHosted=true;window.onklavFreshStart=true');pg=c.new_page();errors=[];pg.on('pageerror',lambda e:errors.append(str(e)));pg.on('console',lambda m:errors.append(m.text)if m.type=='error'or'ERROR:'in m.text else None);pg.goto(f'http://127.0.0.1:{server.server_port}/index.html?game=dominos');pg.wait_for_function('typeof dominosProbe==="function"')
 def state(op='state'):
  pg.evaluate('(op)=>dominosProbe(op)',op);pg.wait_for_timeout(30);return pg.evaluate('dominosState')
 state('opening');pg.wait_for_timeout(180);pg.evaluate('onklavGameAction("settings")');pg.wait_for_timeout(130);a=state();toggle=next(x for x in a['dialogs']if x['text']=='Reduce motion');pg.touchscreen.tap(*toggle['point']);a=state();done=next(x for x in a['dialogs']if x['text']=='Done');pg.touchscreen.tap(*done['point']);pg.wait_for_timeout(120);a=state();assert a['reduced'],a
 tile=next(t for t in a['tiles']if t['id']==a['model']['required']);pg.touchscreen.tap(*tile['point']);a=state();pg.touchscreen.tap(*a['endpoints'][0]);pg.wait_for_timeout(50);a=state();assert len(a['model']['chain'])==1 and a['feedback_scale']==1 and a['particle_count']==0,a
 assert not errors,errors
 (out/'reduced-motion.json').write_text(json.dumps({'real_settings_toggle':True,'legal_touch_move':True,'stationary_feedback_no_particles':True,'errors':errors},indent=2));print('REDUCED MOTION SETTINGS AND PLAY PASSED');b.close()
server.shutdown()
