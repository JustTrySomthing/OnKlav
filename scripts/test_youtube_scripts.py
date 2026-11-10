#!/usr/bin/env python3
"""Exercise the exact fixed WebView scripts in a bounded JavaScript fake DOM."""
from pathlib import Path
import json,re,subprocess
source=(Path(__file__).resolve().parents[1]/'android/app/src/main/kotlin/app/onklav/home/SdkMediaScripts.kt').read_text()
scripts={k:re.search(r'val '+k+r'="""(.*?)"""',source,re.S).group(1) for k in ['metadata','youtube','landscape','portrait','pause']}
download_source=(Path(__file__).resolve().parents[1]/'android/app/src/main/kotlin/app/onklav/home/YoutubeDownloads.kt').read_text()
scripts['download_manifest']=re.search(r'val manifest="""(.*?)"""',download_source,re.S).group(1)
program=r'''
const assert=require('node:assert/strict'),vm=require('node:vm');
const scripts=SCRIPTS;
let player={classList:{contains:n=>n==='ad-showing'},querySelector:()=>video};
let clicks=0,plays=0;const attrs=new Map(),styles=new Map();
let parent={setAttribute:(k,v)=>attrs.set(k,v),removeAttribute:k=>attrs.delete(k),parentElement:null};
let video={muted:false,playbackRate:1,duration:30,currentTime:0,readyState:4,paused:true,ended:false,parentElement:parent,setAttribute:(k,v)=>attrs.set(k,v),removeAttribute:k=>attrs.delete(k),addEventListener(){},removeEventListener(){},pause(){this.paused=true},play(){plays++;this.paused=false;return null}};
let skip={getClientRects:()=>[1],disabled:false,getAttribute:()=>null,click(){clicks++}};
let tick,cleanup;const docEvents=new Map();
const document={hidden:false,head:{appendChild(n){if(n.id)styles.set(n.id,n)}},getElementById:id=>styles.get(id),createElement:()=>({remove(){styles.delete(this.id)}}),querySelector:q=>q.startsWith('video')?video:q==='.ad-showing,.ad-interrupting'||q.startsWith('span[')?null:player,querySelectorAll:q=>q.startsWith('video')?[video]:q.startsWith('[data-')?[video,parent]:[skip],addEventListener(k,f){docEvents.set(k,f)},removeEventListener(k,f){if(docEvents.get(k)===f)docEvents.delete(k)}};
const window={addEventListener(){},removeEventListener(){}};
const location={href:'https://www.youtube.com/watch?v=AbC_def-123'};
const context=vm.createContext({document,window,URL,location,Date,setTimeout:()=>1,setInterval:f=>{tick=f;return 1},clearInterval(){}});
vm.runInContext(scripts.youtube,context);
assert.equal(clicks,1);assert.equal(video.currentTime,30);assert.equal(video.muted,true);
player.classList.contains=()=>false;tick();assert.equal(video.muted,false);assert.equal(video.playbackRate,1);
skip.disabled=true;tick();assert.equal(clicks,2); // Previous tick clicked; disabled tick did not.
const rate=Object.getOwnPropertyDescriptor(video,'playbackRate');Object.defineProperty(video,'playbackRate',{get(){return 1},set(){throw Error('unsupported rate')},configurable:true});
player.classList.contains=n=>n==='ad-showing';video.currentTime=0;tick();assert.equal(video.currentTime,30);assert.equal(video.muted,true);
player.classList.contains=()=>false;tick();assert.equal(video.muted,false);Object.defineProperty(video,'playbackRate',rate);
window.__onklavVideoCleanup();assert.equal(video.muted,false);
console.log('PASS visible skip, disabled control, ad seek, playback restoration, cleanup');
vm.runInContext(scripts.youtube,context);
window.__onklavAutoplay='AbC_def-123';window.__onklavAutoplayUntil=Date.now()+15000;tick();assert.equal(plays,1);
video.pause();tick();assert.equal(plays,1); // A user's pause is not overridden.
window.__onklavAutoplay='AbC_def-124';tick();assert.equal(plays,1);assert.equal(window.__onklavAutoplay,null);
window.__onklavAutoplay='AbC_def-123';window.__onklavAutoplayUntil=Date.now()-1;tick();assert.equal(plays,1);
video.muted=true;video.paused=false;window.__onklavAutoplay='AbC_def-123';window.__onklavAutoplayUntil=Date.now()+15000;tick();assert.equal(video.muted,false);assert.equal(video.volume,1);
docEvents.get("pointerdown")({type:"pointerdown",isTrusted:true,target:{closest:()=>({})}});video.muted=true;tick();assert.equal(video.muted,true); // A later user mute stays in effect.
location.href="https://www.youtube.com/watch?v=AbC_def-124";tick();assert.equal(video.muted,false); // Bookmarked navigation clears site mute.
location.href="https://www.youtube.com/watch?v=AbC_def-123";tick();
video.muted=false;
vm.runInContext(scripts.landscape,context);assert.ok(styles.get('onklav-wide-video').textContent.includes('position:fixed'));assert.ok(attrs.has('data-onklav-wide-video'));
vm.runInContext(scripts.portrait,context);assert.equal(styles.has('onklav-wide-video'),false);assert.equal(attrs.size,0);
vm.runInContext(scripts.pause,context);assert.equal(video.paused,true);assert.equal(window.__onklavAutoplay,null);
console.log('PASS explicit autoplay, no restart after pause, wrong-video/expired intent, landscape/portrait viewport restoration');
window.ytInitialPlayerResponse={videoDetails:{videoId:'AbC_def-123',channelId:'UC'+'a'.repeat(22),author:'Example'}};
document.querySelectorAll=()=>[];document.title='Video';
Object.assign(context,{URL,location});
let data=JSON.parse(vm.runInContext(scripts.metadata,context));assert.equal(data.channel,'https://www.youtube.com/channel/UC'+'a'.repeat(22));assert.equal(data.channel_title,'Example');
window.ytInitialPlayerResponse.videoDetails.videoId='stale';data=JSON.parse(vm.runInContext(scripts.metadata,context));assert.equal(data.channel,'');
document.querySelectorAll=()=>[{querySelector:()=>({href:'https://www.youtube.com/@Owner',textContent:'Owner'})}];data=JSON.parse(vm.runInContext(scripts.metadata,context));assert.equal(data.channel_title,'Owner');
player.getPlayerResponse=()=>({videoDetails:{videoId:'AbC_def-123',channelId:'UC'+'b'.repeat(22),author:'Live owner'}});data=JSON.parse(vm.runInContext(scripts.metadata,context));assert.equal(data.channel_title,'Live owner');assert.equal(data.channel,'https://www.youtube.com/channel/UC'+'b'.repeat(22));
location.href='https://www.youtube.com/@Owner/videos';document.querySelector=()=>({textContent:'Owner'});data=JSON.parse(vm.runInContext(scripts.metadata,context));assert.equal(data.channel,'https://www.youtube.com/@Owner/videos');
console.log('PASS current video owner, stale player rejection, scoped owner link, channel page');
const response={videoDetails:{videoId:'AbC_def-123',isLive:false},streamingData:{formats:[{itag:18,url:'https://r1.googlevideo.com/videoplayback?id=main',mimeType:'video/mp4',qualityLabel:'360p',width:640,height:360,audioQuality:'AUDIO_QUALITY_MEDIUM'}],adaptiveFormats:[{itag:137,signatureCipher:'url='+encodeURIComponent('https://r1.googlevideo.com/videoplayback?id=main'),mimeType:'video/mp4',qualityLabel:'1080p60',width:1080,height:1920}]}};
const dc=vm.createContext({URL,URLSearchParams,window:{ytInitialPlayerResponse:response},location:{href:'https://www.youtube.com/watch?v=AbC_def-123'},document:{querySelector:()=>null}});
let dm=JSON.parse(vm.runInContext(scripts.download_manifest,dc));assert.equal(dm.ad,false);assert.equal(dm.streams[0].audio,true);assert.equal(dm.streams[1].height,1080);assert.equal(dm.streams[1].url,'');assert.equal(dm.streams[1].identity,'main');
response.videoDetails.videoId='stale';dm=JSON.parse(vm.runInContext(scripts.download_manifest,dc));assert.equal(dm.ad,true);assert.equal(dm.streams.length,0);
console.log('PASS download metadata, muxed audio, portrait quality labels, no undeciphered URL use, stale response rejection');

'''.replace('SCRIPTS',json.dumps(scripts))
subprocess.run(['node','-e',program],check=True,timeout=10)
