package app.onklav.home
/** Fixed provider code, never supplied by guest packages. Best effort against changing site markup. */
internal object SdkMediaScripts {
 // Check the current route on every play so YouTube's in-page navigation can
 // move between the feed and a selected video without reloading the document.
 fun feedPreviews(enabled: Boolean) = """((enabled)=>{
 if(window!==window.top)return;
 if(!window.__onklavSetFeedPreviews){
  let allowed=false;
  const cards='ytd-video-preview,ytm-inline-playback-renderer,ytd-rich-grid-media,ytm-video-with-context-renderer,ytd-compact-video-renderer,#inline-preview-player';
  const blocked=v=>{
   if(allowed||!(v instanceof HTMLVideoElement))return false;
   const u=new URL(location.href);
   const player=(u.pathname==='/watch'&&u.searchParams.has('v'))||/^\/(shorts|live|embed)\/[^/]+/.test(u.pathname);
   return !player||!!v.closest(cards);
  };
  const play=HTMLMediaElement.prototype.play;
  HTMLMediaElement.prototype.play=function(...args){
   return blocked(this)?Promise.resolve():play.apply(this,args);
  };
  document.addEventListener('play',event=>{if(blocked(event.target))event.target.pause();},true);
  window.__onklavSetFeedPreviews=value=>{
   allowed=value;
   if(!allowed)document.querySelectorAll('video').forEach(v=>{if(blocked(v)&&!v.paused)v.pause();});
  };
 }
 window.__onklavSetFeedPreviews(enabled);
 })($enabled);"""
 val metadata="""(()=>{
 const url=new URL(location.href),id=url.searchParams.get('v')||((url.pathname.match(/^\/(?:shorts|live)\/([^/]+)/)||[])[1])||'';
 let channel='',name='';
 const own=/^\/(?:@[^/]+|(?:channel|c|user)\/[^/]+)(?:\/|$)/.test(url.pathname);
 if(own){channel=url.href;name=(document.querySelector('yt-page-header-renderer h1,ytm-channel-header-renderer h1,#channel-header-container #channel-name')||{}).textContent||document.title;}
 else {
  const roots=document.querySelectorAll('ytd-watch-metadata #owner,#owner,ytm-slim-owner-renderer,ytm-video-owner-renderer,.slim-owner-channel-name,ytd-reel-video-renderer[is-active] #channel-name,ytm-reel-video-renderer[is-active]');
  for(const root of roots){const link=root.querySelector('a[href^="/@"],a[href^="/channel/"],a[href^="/c/"],a[href^="/user/"]');if(link){channel=link.href;name=link.textContent||root.textContent;break;}}
  let response=window.ytInitialPlayerResponse;
  const player=document.querySelector('#movie_player');try{if(player&&typeof player.getPlayerResponse==='function')response=player.getPlayerResponse()||response;}catch(_){}
  if(typeof response==='string')try{response=JSON.parse(response);}catch(_){response=null;}
  const details=response&&response.videoDetails;
  if(details&&details.videoId===id&&/^UC[A-Za-z0-9_-]{22}$/.test(details.channelId||'')){channel='https://www.youtube.com/channel/'+details.channelId;name=details.author||'';}
  if(!channel){const owner=document.querySelector('span[itemprop="author"] link[itemprop="url"]');if(owner){channel=owner.href;name=(document.querySelector('span[itemprop="author"] meta[itemprop="name"]')||{}).content||name;}}
 }
 return JSON.stringify({url:location.href,title:document.title,channel:channel,channel_title:(name||'').trim().slice(0,160)});
 })()"""
 val youtube="""(function(){
 if(window.__onklavVideoCleanup)window.__onklavVideoCleanup();
 const style=document.createElement('style');style.textContent='ytd-ad-slot-renderer,ytd-display-ad-renderer,ytm-promoted-sparkles-web-renderer,.ytp-ad-overlay-container{display:none!important}';document.head.appendChild(style);
 let altered=null,muted=false,rate=1,starting=false,soundVideo=null,soundUntil=0,soundId="";
 function sound(){
  const u=new URL(location.href),id=u.searchParams.get("v")||((u.pathname.match(/^\/(?:shorts|live|embed)\/([^/]+)/)||[])[1])||"";
  const v=document.querySelector("video.html5-main-video")||document.querySelector("video");if(!v||!id)return;
  if(soundId!==id||soundVideo!==v){soundId=id;soundVideo=v;soundUntil=Date.now()+12000;}
  if(Date.now()>soundUntil)return;
  const p=document.querySelector("#movie_player")||document.querySelector(".html5-video-player");
  if(p&&(p.classList.contains("ad-showing")||p.classList.contains("ad-interrupting")))return;
  v.muted=false;v.defaultMuted=false;v.removeAttribute("muted");if(v.volume===0)v.volume=1;
  try{if(p&&p.unMute)p.unMute();if(p&&p.getVolume&&p.getVolume()===0&&p.setVolume)p.setVolume(100);}catch(_){}
  if(!v.paused&&v.readyState>=2&&!v.muted){if(!v.__onklavSoundGuard){v.__onklavSoundGuard=true;const fix=()=>{if(Date.now()<soundUntil&&v.muted){v.muted=false;try{if(p&&p.unMute)p.unMute();}catch(_){}}};v.addEventListener("volumechange",fix);setTimeout(()=>{v.removeEventListener("volumechange",fix);v.__onklavSoundGuard=false;},12000);}}
 }
 function userSound(e){if(e.isTrusted&&((e.type==="keydown"&&e.key.toLowerCase()==="m")||(e.target&&e.target.closest&&e.target.closest(".ytp-mute-button,[aria-label*=Mute],[aria-label*=Unmute]"))))soundUntil=0;}
 document.addEventListener("pointerdown",userSound,true);document.addEventListener("keydown",userSound,true);
 function clearViewport(){
  const style=document.getElementById('onklav-wide-video');if(style)style.remove();
  document.querySelectorAll('[data-onklav-wide-video],[data-onklav-wide-parent]').forEach(n=>{n.removeAttribute('data-onklav-wide-video');n.removeAttribute('data-onklav-wide-parent');});
 }
 function viewport(){
  if(!window.__onklavWide){clearViewport();return;}
  const video=document.querySelector('video.html5-main-video')||document.querySelector('video');if(!video)return;
  video.setAttribute('data-onklav-wide-video','');
  for(let p=video.parentElement;p;p=p.parentElement)p.setAttribute('data-onklav-wide-parent','');
  if(!document.getElementById('onklav-wide-video')){
   const wide=document.createElement('style');wide.id='onklav-wide-video';
   wide.textContent='html,body{margin:0!important;overflow:hidden!important;background:#000!important}body *{visibility:hidden!important}[data-onklav-wide-parent]{transform:none!important;overflow:visible!important;contain:none!important}[data-onklav-wide-video]{visibility:visible!important;position:fixed!important;inset:0!important;width:100vw!important;height:100vh!important;max-width:none!important;max-height:none!important;object-fit:contain!important;background:#000!important;z-index:2147483647!important}';document.head.appendChild(wide);
  }
 }
 window.__onklavApplyViewport=viewport;
 function autoplay(){
  const wanted=window.__onklavAutoplay;if(!wanted||starting)return;
  const url=new URL(location.href),current=url.searchParams.get('v')||((url.pathname.match(/^\/(?:shorts|live|embed)\/([^/]+)/)||[])[1])||'';
  if(Date.now()>window.__onklavAutoplayUntil||(current&&current!==wanted)){window.__onklavAutoplay=null;return;}
  const video=document.querySelector('video');if(current!==wanted||!video||video.readyState<2||video.ended)return;
  video.muted=false;video.defaultMuted=false;video.volume=1;
  const api=document.querySelector('#movie_player')||document.querySelector('.html5-video-player');
  try{if(api&&typeof api.unMute==='function')api.unMute();if(api&&typeof api.setVolume==='function')api.setVolume(100);}catch(_){}
  if(!video.paused){window.__onklavAutoplay=null;return;}
  starting=true;
  try{const pending=video.play();if(pending&&pending.then)pending.then(()=>{window.__onklavAutoplay=null;starting=false;},()=>{starting=false;});else{window.__onklavAutoplay=null;starting=false;}}catch(_){starting=false;}
 }
 function restore(){if(altered){try{altered.muted=false;altered.playbackRate=rate;}catch(_){}altered=null;}}
 function tick(){
   if(document.hidden){restore();document.querySelectorAll('video').forEach(v=>v.pause());return;}
   viewport();autoplay();sound();
   const player=document.querySelector('.html5-video-player')||document.querySelector('#movie_player')||document.querySelector('#player');const video=(player&&player.querySelector('video'))||document.querySelector('video');
   document.querySelectorAll('.ytp-ad-skip-button,.ytp-ad-skip-button-modern,.ytp-skip-ad-button,ytm-ad-skip-button-renderer button,.ytm-ad-skip-button-container button,ytm-ad-skip-button-renderer,.ytm-ad-skip-button-container,.ytp-ad-skip-button-container button,button[aria-label="Skip ad"],button[aria-label="Skip ads"]').forEach(b=>{if(b.getClientRects().length&&!b.disabled&&b.getAttribute('aria-disabled')!=='true')b.click();});
   if(!player||!video||(!player.classList.contains('ad-showing')&&!player.classList.contains('ad-interrupting')&&!document.querySelector('.ad-showing,.ad-interrupting'))){restore();return;}
   if(altered!==video){restore();altered=video;muted=video.muted;rate=video.playbackRate;}
   video.muted=true;try{video.playbackRate=16;}catch(_){}
   if(Number.isFinite(video.duration)&&video.duration>0)try{video.currentTime=video.duration;}catch(e){}
 }
 const timer=setInterval(tick,500);const hide=()=>{restore();document.querySelectorAll('video').forEach(v=>v.pause());};
 document.addEventListener('visibilitychange',tick);window.addEventListener('pagehide',hide);
 window.__onklavVideoCleanup=()=>{soundUntil=0;document.removeEventListener("pointerdown",userSound,true);document.removeEventListener("keydown",userSound,true);clearInterval(timer);restore();clearViewport();window.__onklavApplyViewport=null;style.remove();document.removeEventListener('visibilitychange',tick);window.removeEventListener('pagehide',hide);};tick();
 })();"""
 /** Rotation uses the host's immersive viewport, so no gesture-gated fullscreen request is needed. */
 val landscape="""window.__onklavWide=true;if(window.__onklavApplyViewport)window.__onklavApplyViewport();"""
 val portrait="""window.__onklavWide=false;if(window.__onklavApplyViewport)window.__onklavApplyViewport();if(document.fullscreenElement&&document.exitFullscreen){try{const p=document.exitFullscreen();if(p&&p.catch)p.catch(()=>{});}catch(_){}}"""
 fun autoplay(video:String):String{
  require(video.matches(Regex("[A-Za-z0-9_-]{11}")))
  return "window.__onklavAutoplay='$video';window.__onklavAutoplayUntil=Date.now()+15000;"
 }
 val pause="""window.__onklavAutoplay=null;document.querySelectorAll('video,audio').forEach(v=>v.pause());if(window.__onklavVideoCleanup)window.__onklavVideoCleanup();"""
}
