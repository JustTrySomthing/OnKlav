/* OnKlav loader: local assets only; sound is best-effort under browser autoplay rules. */
(() => {
  'use strict';
  const screen=document.getElementById('status');
  const fill=document.getElementById('onklav-fill');
  const label=document.getElementById('onklav-loading-label');
  const percent=document.getElementById('onklav-loading-percent');
  const progress=document.getElementById('status-progress');
  const started=performance.now();
  const custom=!!document.querySelector('meta[name="onklav-loader"][content="custom"]');
  let prefs={};
  try { prefs=JSON.parse(localStorage.getItem('onklav-loader-preferences')||'{}')||{}; } catch (_) {}
  const reduced=matchMedia('(prefers-reduced-motion:reduce)').matches||prefs.reduce_motion===true;
  const volume=typeof prefs.volume==='number'&&Number.isFinite(prefs.volume)?Math.max(0,Math.min(1,prefs.volume)):.8;
  screen.classList.toggle('onklav-custom',custom);
  screen.classList.toggle('loader-reduced',reduced);
  progress.setAttribute('aria-label','Loading game files');
  let failed=false,leaving=false,context=null,played=false,closeTimer=0;
  const intro=new Promise(resolve=>setTimeout(resolve,custom||reduced?0:1650));
  function stopSound() {
    clearTimeout(closeTimer);
    if(context) { const closing=context; context=null; closing.close().catch(()=>{}); }
  }
  async function sound() {
    if(played||failed||leaving||document.hidden||custom||prefs.muted===true||volume===0||performance.now()-started>1100)return;
    const Audio=window.AudioContext||window.webkitAudioContext;
    if(!Audio)return;
    try {
      if(!context)context=new Audio();
      const current=context;
      await current.resume();
      if(current!==context||current.state!=='running'||failed||leaving||document.hidden||performance.now()-started>1100){stopSound();return;}
      if(played)return;
      played=true;
      const now=current.currentTime;
      const master=current.createGain();master.gain.value=.22*volume;master.connect(current.destination);
      // Soft airborne sweep and a short resolving three-note shimmer; every voice has tapered edges.
      const sweep=current.createOscillator(),env=current.createGain();sweep.type='sine';
      sweep.frequency.setValueAtTime(145,now);sweep.frequency.exponentialRampToValueAtTime(520,now+.46);
      env.gain.setValueAtTime(0,now);env.gain.linearRampToValueAtTime(.2,now+.08);env.gain.exponentialRampToValueAtTime(.001,now+.56);
      sweep.connect(env).connect(master);sweep.start(now);sweep.stop(now+.58);
      [523.251,783.991,1046.502].forEach((hz,i)=>{
        const osc=current.createOscillator(),gain=current.createGain(),at=now+.5+i*.055;
        osc.type='sine';osc.frequency.value=hz;
        gain.gain.setValueAtTime(0,at);gain.gain.linearRampToValueAtTime(.25-i*.035,at+.018);gain.gain.exponentialRampToValueAtTime(.001,at+.48);
        osc.connect(gain).connect(master);osc.start(at);osc.stop(at+.5);
      });
      closeTimer=setTimeout(stopSound,1400);
    } catch (_) { stopSound(); }
  }
  const onPointer=()=>sound();
  document.addEventListener('pointerdown',onPointer,{passive:true});
  document.addEventListener('visibilitychange',()=>{
    screen.classList.toggle('loader-hidden',document.hidden);
    if(document.hidden)stopSound();
  });
  window.addEventListener('pagehide',stopSound,{once:true});
  setTimeout(sound,100);
  function cleanup() { stopSound();document.removeEventListener('pointerdown',onPointer); }
  window.OnKlavLoader={
    prepare(engine) {
      const start=engine.start;
      engine.start=function(...args) {
        label.textContent='Starting game';
        return intro.then(()=>start.apply(this,args));
      };
    },
    progress(current,total) {
      if(failed||leaving)return;
      const known=Number.isFinite(current)&&Number.isFinite(total)&&current>=0&&total>0;
      screen.dataset.determinate=String(known);
      if(known) {
        const ratio=Math.max(0,Math.min(1,current/total));
        fill.style.transform=`scaleX(${ratio})`;
        percent.textContent=`${Math.floor(ratio*100)}%`;
        label.textContent=ratio===1?'Preparing game':'Loading game files';
        progress.setAttribute('aria-valuetext',`${Math.floor(ratio*100)} percent of game files loaded`);
      } else {
        percent.textContent='';label.textContent='Getting ready';
        progress.setAttribute('aria-valuetext','Loading game files');
      }
    },
    finish(remove) {
      if(failed||leaving)return;
      leaving=true;cleanup();percent.textContent='';label.textContent='Ready';fill.style.transform='scaleX(1)';
      screen.classList.add('loader-leaving');
      setTimeout(remove,reduced?0:220);
    },
    fail() {
      failed=true;cleanup();screen.classList.add('loader-error');
      document.getElementById('status-notice').setAttribute('role','alert');
    }
  };
})();
