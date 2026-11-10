package app.onklav.home

import android.app.*
import android.content.*
import android.media.*
import android.media.session.*
import android.net.Uri
import android.os.*
import android.view.SurfaceHolder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** One shared player, focus owner and media session for all SDK apps. */
internal object SdkMedia {
 val changes=MutableStateFlow(0L)
 private val main=Handler(Looper.getMainLooper())
 private val mediaScope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
 private var preparation:Job?=null
 internal var openSource:(Context,JSONObject,JSONObject)->MediaDataSource=SdkMediaSources::open

 @Volatile private var owner:JSONObject?=null;private var queue=JSONArray();private var index=0;private var player:MediaPlayer?=null;private var source:MediaDataSource?=null
 private var attachedGeneration=""
 private var context:Context?=null;private var service:SdkPlaybackService?=null;private var session:MediaSession?=null
 private var networkRecovery:DefaultNetworkRecovery?=null
 private var routeRetries=0
 private var compatibleMp3:java.io.File?=null
 private var mp3Attempt=""
 internal var radioFactory:((Context,String,()->Unit,RadioStreamEngine.Events)->RadioStreamEngine)?={c,url,verify,events->ExoRadioStream(c,url,verify,events)}
 private var radioEngine:RadioStreamEngine?=null
 private var radioReconnect:Runnable?=null
 private var radioShortEnds=0
 private var radioStartedAt=0L
 private var radioProgressAt=0L
 private var radioProgressPosition=-1L
 private var radioConnection=""
 internal var internetAvailable:(Context)->Boolean=::validatedInternet
 private fun cancelRadioReconnect(){radioReconnect?.let{main.removeCallbacks(it)};radioReconnect=null}
 private var recoveryRetry:Runnable?=null
 private fun resetConnections(){SdkMediaSources.networkChanged();SdkRadio.networkChanged();RadioHttpSource.networkChanged();SdkPodcasts.networkChanged()}
 private fun remoteBroadcast():Boolean {val item=queue.optJSONObject(index)?:return false;return item.has("radio_station")||item.has("podcast_episode")&&!item.optBoolean("podcast_local")}

 internal fun networkRecovered(){
  mainThread();resetConnections();val item=queue.optJSONObject(index)?:return
  if(!wantPlay||state !in setOf("playing","preparing","error")||!(item.has("radio_station")||item.has("podcast_episode")&&!item.optBoolean("podcast_local")))return
  // Keep a podcast's current position; a live station starts at the new live edge.
  if(ready)position=currentPosition()
  if(liveRadio())position=0
  cancelRadioReconnect();radioShortEnds=0;routeRetries=1;recoveryRetry?.let{main.removeCallbacks(it)};recoveryRetry=null;resetConnections();save();prepare()
 }
 private var pendingSeek:Long?=null
 private fun currentPosition():Long=pendingSeek?:runCatching{player?.currentPosition?.toLong()?.coerceAtLeast(0)?:position}.getOrDefault(position)
 private var state="idle";private var errorMessage="";private var speed=1f;private var position=0L;private var duration=0L;@Volatile private var generation="";private var focus:AudioFocusRequest?=null;private var ready=false;private var wantPlay=false;private var prepareTimeout:Runnable?=null
 private val discardedPositions=java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
 private var sleepDeadline=0L
 private var chapterSleep=false;private var chapterSleepAt=0L
 private val sleepStop=Runnable{if(sleepDeadline>0&&SystemClock.elapsedRealtime()>=sleepDeadline)stop()}
 private fun liveRadio()=queue.optJSONObject(index)?.has("radio_station")==true
 private var holder:SurfaceHolder?=null;private var lastSaved=0L;private var pausedAt=0L
 private fun same(a:JSONObject,b:JSONObject)=listOf("id","publisher","hash").all{a.optString(it)==b.optString(it)}
 private fun mainThread(){check(Looper.myLooper()==Looper.getMainLooper())}
 private fun verify(){if(queue.optJSONObject(index)?.has("audiobook_track")==true){SdkAudioBooks.policy(context?:error("Playback unavailable"),owner?:error("Playback stopped"),queue.getJSONObject(index));return};if(queue.optJSONObject(index)?.has("podcast_episode")==true){SdkPodcasts.policy(context?:error("Playback unavailable"),owner?:error("Playback stopped"),queue.getJSONObject(index));return};if(liveRadio()){SdkRadio.policy(context?:error("Playback unavailable"),owner?:error("Playback stopped"),queue.getJSONObject(index));return};val c=context?:error("Playback unavailable");val o=owner?:error("Playback stopped");AppSdk.nativeTask(c,JSONObject().put("action","media_authorize").put("owner",o).put("args",JSONObject()))}
 private fun snapshot()=JSONObject().put("state",state).put("position_ms",position).put("duration_ms",duration).put("index",index).put("count",queue.length()).put("speed",speed).put("title",queue.optJSONObject(index)?.optString("title")?:"").put("error",errorMessage).put("live",liveRadio()).put("connection",if(liveRadio())radioConnection else "").put("sleep_remaining_ms",if(sleepDeadline>0)(sleepDeadline-SystemClock.elapsedRealtime()).coerceAtLeast(0)else 0).put("video",ready&&(player?.videoWidth?:0)>0).put("aspect",if(ready&&(player?.videoHeight?:0)>0)player!!.videoWidth.toDouble()/player!!.videoHeight else 16.0/9.0)
 fun radioAudioSession(o:JSONObject):Int {mainThread();check(owner?.let{same(it,o)}==true&&liveRadio()&&state=="playing");verify();return (radioEngine?.audioSession?:player?.audioSessionId)?.takeIf{it>0}?:error("Station not playing")}
 fun audiobookStatus(o:JSONObject):JSONObject {val row=status(o);if(owner?.let{same(it,o)}==true){row.put("book",queue.optJSONObject(index)?.optString("audiobook_book")).put("chapter_sleep",chapterSleep)};return row}
 fun status(o:JSONObject):JSONObject {mainThread();return if(owner?.let{same(it,o)}==true)snapshot()else JSONObject().put("state","idle").put("position_ms",0).put("duration_ms",0).put("index",0).put("count",0).put("speed",1).put("title","").put("error","")}
 /** UI diagnostics never cross the strict signed SDK media reply boundary. */
 internal fun brokerStatus(o:JSONObject)=status(o).also{it.remove("connection")}
 internal fun pauseUncommittedRadio(o:JSONObject,id:String){mainThread();if(queue.optJSONObject(index)?.optString("radio_id")==id)pauseForRadioSelection(o)}
 internal fun pauseForRadioSelection(o:JSONObject){
  mainThread();if(!liveRadio()||owner?.let{same(it,o)}!=true)return
  // Cleanup must not authorize the superseded lease: closing/revoking an old
  // session cannot block an already-authorized new station selection.
  cancelRadioReconnect();wantPlay=false;disposePlayer();releaseFocus();state="paused";pausedAt=SystemClock.elapsedRealtime();publish()
 }
 internal suspend fun retireRadioForSelection(o:JSONObject)=withContext(Dispatchers.Main.immediate){
  pauseForRadioSelection(o)
  // disposePlayer queues release outside the carrier lock. Queue a fence behind
  // it before allowing the IO coroutine to probe/open the replacement stream.
  suspendCancellableCoroutine<Unit>{continuation->
   val fence=Runnable{if(continuation.isActive)continuation.resumeWith(Result.success(Unit))}
   main.post(fence);continuation.invokeOnCancellation{main.removeCallbacks(fence)}
  }
 }
 fun execute(c:Context,request:JSONObject,authorize:()->Unit):JSONObject {
  mainThread();authorize();val o=request.getJSONObject("owner");val args=request.getJSONObject("args")
  when(request.getString("service")){
   "media.status"->return brokerStatus(o)
   "media.play"->{if(args.getJSONArray("queue").optJSONObject(args.optInt("index"))?.has("radio_station")==true)resetConnections();stop(false);context=c.applicationContext;owner=JSONObject(o.toString());discardedPositions.remove(o.getString("id"));queue=JSONArray(args.getJSONArray("queue").toString());index=args.optInt("index");speed=args.optDouble("speed",1.0).toFloat();generation=UUID.randomUUID().toString();position=args.optLong("position_ms",saved(c));state="preparing";errorMessage="";wantPlay=true;authorize();try{if(service!=null)attach(service!!,generation)else ContextCompat.startForegroundService(c,Intent(c,SdkPlaybackService::class.java).putExtra("generation",generation));publish()}catch(e:Exception){stop();throw IllegalStateException("Playback could not start. Open the app and try again.")}}
   "media.control"->{check(owner?.let{same(it,o)}==true){"Start playback in this app first"};control(args.getString("operation"),args)}
  };return brokerStatus(o)
 }
 fun controlFor(o:JSONObject,op:String,args:JSONObject=JSONObject()){check(owner?.let{same(it,o)}==true){"Start playback in this app first"};control(op,args)}
 private fun positionKey():String=java.security.MessageDigest.getInstance("SHA-256").digest((owner!!.getString("id")+"\u0000"+owner!!.getString("publisher")+"\u0000"+queue.getJSONObject(index).let{it.optString("podcast_id",it.optString("file",it.optString("url")))+"|"+it.optString("account")}).toByteArray()).joinToString(""){"%02x".format(it)}
 private fun saved(c:Context):Long=if(liveRadio())0 else runCatching{c.getSharedPreferences("sdk-media-positions-"+owner!!.getString("id"),0).getLong(positionKey(),0)}.getOrDefault(0)
 private fun save()=AppSdk.carrierEgress{if(liveRadio())return@carrierEgress;val c=context?:return@carrierEgress;val o=owner?:return@carrierEgress;if(queue.length()==0||o.getString("id")in discardedPositions)return@carrierEgress;val prefs=c.getSharedPreferences("sdk-media-positions-"+o.getString("id"),0);val edit=prefs.edit();if(prefs.all.size>=128&&!prefs.contains(positionKey()))prefs.all.keys.take(prefs.all.size-127).forEach{edit.remove(it)};edit.putLong(positionKey(),position.coerceAtLeast(0)).apply();SdkAudioBooks.recorded(c,o,queue.getJSONObject(index),position,duration,speed,state=="ended");lastSaved=SystemClock.elapsedRealtime()}
 private fun publish(){changes.value++;runCatching{service?.refresh()}.onFailure{state="error";errorMessage="Android could not show playback controls. Reopen the app and try again.";wantPlay=false;disposePlayer();service?.stopSelf()};runCatching{session?.setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,queue.optJSONObject(index)?.optString("title")?:"").putLong(MediaMetadata.METADATA_KEY_DURATION,duration).build());session?.setPlaybackState(PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_STOP or (if(liveRadio())0L else PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS)).setState(when(state){"playing"->PlaybackState.STATE_PLAYING;"paused"->PlaybackState.STATE_PAUSED;"preparing"->PlaybackState.STATE_BUFFERING;"error"->PlaybackState.STATE_ERROR;else->PlaybackState.STATE_STOPPED},position,speed).build())}}
 private fun disposePlayer(){
  preparation?.cancel();preparation=null;prepareTimeout?.let{main.removeCallbacks(it)};prepareTimeout=null;pendingSeek=null;ready=false
  val live=radioEngine;radioEngine=null;radioProgressAt=0;radioProgressPosition=-1;if(live!=null){runCatching{live.pause()};main.post{runCatching{live.release()}}}
  val old=player;player=null;val input=source;source=null
  // Cancel decoder reads first. Release outside the caller's carrier/registry lock:
  // a decoder callback may be completing its live authorization under that lock.
  runCatching{input?.close()}
  if(old!=null){runCatching{old.setOnPreparedListener(null);old.setOnCompletionListener(null);old.setOnErrorListener(null);old.setOnSeekCompleteListener(null);if(old.isPlaying)old.pause()};main.post{runCatching{old.release()}}}
 }

 private fun mp3Key()=generation+":"+index+":"+queue.optJSONObject(index).toString()
 private fun recoverMp3():Boolean {
  if(liveRadio()||mp3Attempt==mp3Key())return false
  val c=context?:return false;val o=owner?:return false;val item=queue.optJSONObject(index)?:return false
  val file=runCatching{SdkMediaSources.mp3File(c,o,item)}.getOrNull()?:return false
  val key=mp3Key();mp3Attempt=key;if(ready)position=currentPosition();disposePlayer();state="preparing";errorMessage="";publish()
  val wave=java.io.File.createTempFile("mp3-playback-",".wav",c.cacheDir)
  preparation=mediaScope.launch{
   try{withContext(Dispatchers.IO){val job=currentCoroutineContext();var checkedAt=0L;app.onklav.shared.Mp3Codec.decode(file,wave,{job.ensureActive();val now=SystemClock.elapsedRealtime();if(now-checkedAt>=200){verify();checkedAt=now}})}
    if(mp3Key()==key){preparation=null;compatibleMp3?.delete();compatibleMp3=wave;prepare()}else wave.delete()
   }catch(e:CancellationException){wave.delete();throw e}catch(e:Exception){wave.delete();if(mp3Key()==key){preparation=null;failed("MP3 playback could not be prepared: ${e.message}")}}
  };return true
 }
 private fun prepare(){
  mainThread();if(liveRadio()&&radioFactory!=null){prepareRadio();return};try{verify();disposePlayer();if(mp3Attempt!=mp3Key()){compatibleMp3?.delete();compatibleMp3=null;mp3Attempt=""};errorMessage="";state="preparing";val c=context!!;val item=queue.getJSONObject(index);val epoch=generation
   val p=MediaPlayer();player=p;p.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());p.setWakeMode(c,PowerManager.PARTIAL_WAKE_LOCK);val local=if(compatibleMp3==null&&item.has("podcast_episode"))SdkPodcasts.openLocalDescriptor(c,owner!!,item)else null
   if(compatibleMp3!=null)source=SdkMediaSources.pcmFile(compatibleMp3!!){verify()}else if(local!=null)local.use{p.setDataSource(it.fileDescriptor)}else source=openSource(c,owner!!,item)
   val input=source
   fun begin(){if(player!==p||generation!=epoch)return;verify();if(input!=null)p.setDataSource(input);p.setDisplay(holder);p.prepareAsync()}

   p.setOnPreparedListener{if(player===it&&generation==epoch)try{verify();duration=it.duration.toLong().coerceAtLeast(0);position=position.coerceIn(0,if(duration>0)duration else Int.MAX_VALUE.toLong());if(position>0){pendingSeek=position;it.seekTo(position,MediaPlayer.SEEK_CLOSEST)};prepareTimeout?.let{guard->main.removeCallbacks(guard)};prepareTimeout=null;ready=true;routeRetries=0;if(liveRadio())SdkRadio.playbackRecovered(c,queue.getJSONObject(index));if(wantPlay)play()else{state="paused";pausedAt=SystemClock.elapsedRealtime()};publish()}catch(e:Exception){failed()}}
   p.setOnSeekCompleteListener{if(player===it&&generation==epoch)try{pendingSeek=null;position=it.currentPosition.toLong().coerceAtLeast(0)}catch(e:Exception){failed()}}
   p.setOnCompletionListener{if(player===it&&generation==epoch){if(liveRadio()){reconnectRadio();return@setOnCompletionListener};val audio=queue.optJSONObject(index)?.has("audiobook_track")==true;position=if(audio)duration else 0;save();if(chapterSleep){chapterSleep=false;chapterSleepAt=0;wantPlay=false;releaseFocus()};if(index+1<queue.length()){index++;position=if(audio)0 else saved(c);prepare()}else{state="ended";wantPlay=false;save();pausedAt=SystemClock.elapsedRealtime();releaseFocus();publish()}}}
   p.setOnErrorListener{_,what,extra->if(player===p){if(!liveRadio()&&recoverMp3())return@setOnErrorListener true;if(liveRadio()&&wantPlay&&(extra==MediaPlayer.MEDIA_ERROR_IO||extra==MediaPlayer.MEDIA_ERROR_TIMED_OUT||what==MediaPlayer.MEDIA_ERROR_SERVER_DIED)){reconnectRadio();return@setOnErrorListener true};if(liveRadio()&&routeRetries==0)context?.let{SdkRadio.playbackFailed(it,queue.getJSONObject(index),extra)};android.util.Log.w("OnKlavMedia","Decoder error $what / $extra");failed(when{liveRadio()->SdkRadioHealth.notices.value[queue.getJSONObject(index).optString("radio_id")]?.message?:"Could not connect to this station. Retry or choose another station.";extra==MediaPlayer.MEDIA_ERROR_UNSUPPORTED->"This audio format is unsupported. Try another episode.";extra==MediaPlayer.MEDIA_ERROR_MALFORMED->"This audio could not be decoded. Download the episode and retry.";else->"Audio playback was interrupted. Retry or download the episode first."})};true};prepareTimeout=Runnable{if(player===p&&!ready){if(liveRadio()){SdkRadio.playbackFailed(c,queue.getJSONObject(index),MediaPlayer.MEDIA_ERROR_TIMED_OUT);failed("The station took too long to respond. Retry or choose another station.")}else failed()}}.also{main.postDelayed(it,30000)};publish()
   if(input is SdkMediaSources.Remote&&input.probeFirst){
    preparation=mediaScope.launch{
     try{withContext(Dispatchers.IO){input.prime()};if(player===p&&generation==epoch){preparation=null;begin()}}
     catch(e:CancellationException){throw e}
     catch(e:Exception){if(player===p&&generation==epoch){preparation=null;failed("Could not connect to this episode. Retry or download it to listen offline.")}}
    }
   }else begin()
  }catch(e:Exception){if(!recoverMp3())failed(e.message?.take(180)?:"Could not prepare this audio. Retry or download the episode.")}
 }
 private fun prepareRadio(){
  try{verify();disposePlayer();radioStartedAt=0;state="preparing";radioConnection="connecting";errorMessage="";position=0;duration=0;val epoch=generation;val c=context!!;val item=queue.getJSONObject(index);val expectedOwner=owner!!;val permission=SdkRadio.policy(c,expectedOwner,item).toString();var candidate:RadioStreamEngine?=null
   val next=radioFactory!!(c,item.getString("url"),{check(SdkRadio.policy(c,expectedOwner,item).toString()==permission){"Radio permission changed"}},object:RadioStreamEngine.Events{
    private fun valid()=generation==epoch&&owner===expectedOwner&&radioEngine===candidate&&candidate!=null
    override fun ready(){if(!valid())return;try{verify();ready=true;if(wantPlay)play()else{state="paused";publish()}}catch(e:Exception){failed(e.message?:"Station could not start")}}
    override fun playing(value:Boolean){if(!valid())return;if(value&&wantPlay){
     state="playing";radioConnection="live";pausedAt=0;routeRetries=0
     val now=SystemClock.elapsedRealtime();if(radioStartedAt==0L)radioStartedAt=now
     radioProgressAt=now;radioProgressPosition=nextPosition();prepareTimeout?.let{main.removeCallbacks(it)};prepareTimeout=null;SdkRadio.playbackRecovered(c,item)
    }else if(wantPlay){state="preparing";radioConnection="buffering";armRadioTimeout(epoch,candidate)};publish()}
    override fun ended(){if(valid())reconnectRadio()}
    override fun failed(error:Exception){if(valid()){android.util.Log.w("OnKlavRadio","Stream playback failed",error);reconnectRadio()}}
   });candidate=next;radioEngine=next;next.adopt(SdkRadio.takeConnection(c,expectedOwner,item))
   armRadioTimeout(epoch,next)
   publish();next.prepare()
  }catch(e:Exception){failed(e.message?.take(180)?:"Station could not connect. Retry or choose another station.")}
 }
 private fun nextPosition():Long=runCatching{radioEngine?.positionMs?:-1L}.getOrDefault(-1L)
 private fun armRadioTimeout(epoch:String,engine:RadioStreamEngine?){
  if(prepareTimeout!=null)return
  prepareTimeout=Runnable{prepareTimeout=null;if(generation==epoch&&engine!=null&&radioEngine===engine&&wantPlay)reconnectRadio()}.also{main.postDelayed(it,20000)}
 }
 private fun radioWatchdog(){
  if(!liveRadio()||!wantPlay)return
  val now=SystemClock.elapsedRealtime()
  if(state=="playing"){
   val current=nextPosition()
   if(current<0)return
   if(current!=radioProgressPosition){radioProgressPosition=current;radioProgressAt=now}
   else if(radioProgressAt>0&&now-radioProgressAt>=20000)reconnectRadio()
  }
 }
 /** An Icecast connection may finish at a track boundary. This is still a live station,
  * never the end of its queue. Reopen at the live edge; bound rapid EOF/error loops. */
 private fun reconnectRadio(){
  if(!liveRadio())return
  if(!wantPlay){disposePlayer();state="paused";pausedAt=SystemClock.elapsedRealtime();publish();return}
  if(sleepDeadline>0&&SystemClock.elapsedRealtime()>=sleepDeadline){stop();return}
  val c=context
  if(c!=null&&!internetAvailable(c)){
   val epoch=generation;cancelRadioReconnect();disposePlayer();radioStartedAt=0;state="preparing";radioConnection="offline";errorMessage="";publish()
   radioReconnect=Runnable{radioReconnect=null;if(generation==epoch&&wantPlay&&liveRadio()){if(internetAvailable(c))networkRecovered()else reconnectRadio()}}.also{main.postDelayed(it,5000)}
   return
  }
  val elapsed=SystemClock.elapsedRealtime()-radioStartedAt
  if(radioStartedAt>0&&elapsed>=15_000&&(radioProgressPosition<0||radioProgressAt-radioStartedAt>=15_000))radioShortEnds=0
  radioShortEnds++
  if(radioShortEnds>3){context?.let{SdkRadio.playbackFailed(it,queue.getJSONObject(index),MediaPlayer.MEDIA_ERROR_IO)};failed("This station keeps disconnecting. Tap Play to reconnect or choose another station.");return}
  val epoch=generation;val currentOwner=owner
  cancelRadioReconnect();disposePlayer();radioStartedAt=0;position=0;duration=0;state="preparing";radioConnection="reconnecting";errorMessage="";publish()
  radioReconnect=Runnable{
   radioReconnect=null
   if(generation==epoch&&owner===currentOwner&&wantPlay&&liveRadio()&&state=="preparing"){
    if(sleepDeadline>0&&SystemClock.elapsedRealtime()>=sleepDeadline)stop()else prepare()
   }
  }.also{main.postDelayed(it,longArrayOf(250,1000,2500)[radioShortEnds-1])}
 }
 private fun play(){verify();if(liveRadio()&&state !in setOf("preparing","playing")){resetConnections();disposePlayer();position=0;pausedAt=0;radioStartedAt=0};if(state!="preparing"&&state!="playing")radioShortEnds=0;cancelRadioReconnect();wantPlay=true;if(!ready){prepare();return};val c=context!!;val audio=c.getSystemService(AudioManager::class.java)
  val claimEpoch=generation;val claimOwner=owner
  HostSpeechOwner.claim(this){val interrupt={if(generation==claimEpoch&&owner===claimOwner)runCatching{control("pause",JSONObject())};Unit};if(Looper.myLooper()==Looper.getMainLooper())interrupt()else main.post{interrupt()}}
  if(focus==null){val focusEpoch=generation;var requested:AudioFocusRequest?=null;val request=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()).setOnAudioFocusChangeListener({change->main.post{if(generation==focusEpoch&&focus===requested&&change!=AudioManager.AUDIOFOCUS_GAIN){runCatching{control("pause",JSONObject())}}}},main).build();requested=request;check(audio.requestAudioFocus(request)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED){"Audio focus unavailable"};focus=request}
  if(speed!=1f)runCatching{player!!.playbackParams=PlaybackParams().allowDefaults().setSpeed(speed)}.onFailure{speed=1f};if(radioEngine!=null){radioEngine!!.play();return};player!!.start();if(liveRadio()&&state!="playing")radioStartedAt=SystemClock.elapsedRealtime();state="playing";pausedAt=0;publish()
 }
 private fun releaseFocus(){val previous=focus;focus=null;HostSpeechOwner.release(this);runCatching{previous?.let{context?.getSystemService(AudioManager::class.java)?.abandonAudioFocusRequest(it)}}}
 private fun failed(message:String=if(liveRadio())"Could not connect to this station. Retry or choose another station."else"Playback interrupted. Retry, or download the episode first."){save();if(liveRadio())owner?.let{o->SdkRadio.releasePending(o,queue.getJSONObject(index))};disposePlayer();releaseFocus();state="error";radioConnection="station-error";errorMessage=message;pausedAt=SystemClock.elapsedRealtime();publish()
  val c=context;val epoch=generation
  if(wantPlay&&routeRetries>0&&remoteBroadcast()&&c!=null&&validatedInternet(c)){routeRetries--;recoveryRetry?.let{main.removeCallbacks(it)};recoveryRetry=Runnable{recoveryRetry=null;if(generation==epoch&&wantPlay&&state=="error"&&remoteBroadcast()&&validatedInternet(c)){resetConnections();prepare()}}.also{main.postDelayed(it,1000)}}
 }
 fun control(operation:String,args:JSONObject=JSONObject()){
  mainThread();verify()
  if(operation=="sleep.chapter"){check(queue.optJSONObject(index)?.has("audiobook_track")==true);main.removeCallbacks(sleepStop);sleepDeadline=0;chapterSleep=true;chapterSleepAt=SdkAudioBookChapters.nextBoundary(context!!,owner!!,queue.getJSONObject(index),position);publish();return}
  if(operation=="sleep"){chapterSleep=false;chapterSleepAt=0;val minutes=args.getInt("minutes");require(minutes in 0..120);main.removeCallbacks(sleepStop);sleepDeadline=if(minutes==0)0 else SystemClock.elapsedRealtime()+minutes*60000L;if(minutes>0)main.postDelayed(sleepStop,minutes*60000L);publish();return}
  require(!liveRadio()||operation !in setOf("seek","speed","next","previous")){"Live radio has no seek or speed control"}
  when(operation){
   "chapter"->{check(queue.optJSONObject(index)?.has("audiobook_track")==true);val next=args.getInt("index");require(next in 0 until queue.length());save();chapterSleep=false;chapterSleepAt=0;index=next;position=args.optLong("position_ms").coerceAtLeast(0);wantPlay=true;prepare()}
   "stop"->{stop();return}
   "play"->play()
   "pause"->{cancelRadioReconnect();wantPlay=false;if(radioEngine!=null){radioEngine?.pause();disposePlayer()}else if(ready&&player?.isPlaying==true)player?.pause();state="paused";position=if(ready)currentPosition()else position;pausedAt=SystemClock.elapsedRealtime();save();releaseFocus()}
   "seek"->{chapterSleep=false;chapterSleepAt=0;check(ready);val p=args.getLong("position_ms");require(p in 0..duration);pendingSeek=p;position=p;player!!.seekTo(p,MediaPlayer.SEEK_CLOSEST);save()}
   "speed"->{speed=args.getDouble("speed").toFloat();require(speed in 0.5f..3f);if(ready&&player?.isPlaying==true)player!!.playbackParams=PlaybackParams().setSpeed(speed);save()}
   "next","previous"->{if(queue.optJSONObject(index)?.has("audiobook_track")==true){val jump=SdkAudioBookChapters.jump(context!!,owner!!,queue.getJSONObject(index),position,operation=="next");if(jump.first>=queue.length()){position=duration;state="ended";wantPlay=false;save();disposePlayer();releaseFocus();pausedAt=SystemClock.elapsedRealtime();publish()}else control("chapter",JSONObject().put("index",jump.first).put("position_ms",jump.second));return};save();val next=if(operation=="next")index+1 else index-1;if(next<0){position=0;if(ready)player!!.seekTo(0,MediaPlayer.SEEK_CLOSEST)}else if(next>=queue.length()){position=0;save();disposePlayer();releaseFocus();state="ended";wantPlay=false;pausedAt=SystemClock.elapsedRealtime()}else{index=next;position=saved(context!!);wantPlay=true;prepare()}}
   else->error("Unsupported playback control")
  };publish()
 }
 private val tick=object:Runnable{override fun run(){if(owner!=null){if(sleepDeadline>0&&SystemClock.elapsedRealtime()>=sleepDeadline){stop();return};try{verify();radioWatchdog();if(ready){position=currentPosition();if(chapterSleep&&chapterSleepAt>0&&position>=chapterSleepAt){val boundary=chapterSleepAt;chapterSleep=false;chapterSleepAt=0;control("pause");if(duration>0)control("seek",JSONObject().put("position_ms",boundary.coerceAtMost(duration)))};if(SystemClock.elapsedRealtime()-lastSaved>=5000)save()};if(!liveRadio()&&pausedAt>0&&SystemClock.elapsedRealtime()-pausedAt>300_000){stop();return};publish()}catch(e:Exception){stop()};main.postDelayed(this,1000)}}}
 internal fun accepts(epoch:String)=owner!=null&&generation==epoch
 internal fun hasOwner()=owner!=null
 fun attach(s:SdkPlaybackService,epoch:String):Boolean{mainThread();if(!accepts(epoch))return false;if(service===s&&attachedGeneration==epoch&&session!=null)return true;service=s;attachedGeneration=epoch;if(networkRecovery==null)networkRecovery=DefaultNetworkRecovery(s){runCatching{networkRecovered()}.onFailure{serviceFailed()}};session=MediaSession(s,"OnKlav SDK media").also{m->m.setCallback(object:MediaSession.Callback(){override fun onPlay(){system("play",epoch)};override fun onPause(){system("pause",epoch)};override fun onStop(){system("stop",epoch)};override fun onSkipToNext(){system("next",epoch)};override fun onSkipToPrevious(){system("previous",epoch)};override fun onSeekTo(pos:Long){system("seek",epoch,JSONObject().put("position_ms",pos))}});m.isActive=true};s.refresh();main.removeCallbacks(tick);main.post(tick);prepare();return true}
 internal fun serviceFailed(){runCatching{failed("Android could not start playback. Reopen the app and try again.")}.onFailure{runCatching{stop()}}}
 fun system(op:String,epoch:String,args:JSONObject=JSONObject()){
  if(!accepts(epoch))return
  // Media controllers may send stale or unsupported commands despite advertised
  // actions. Reject the command without turning healthy playback into an error.
  if(liveRadio()&&op in setOf("seek","speed","next","previous"))return
  if(op=="seek"&&(!ready||!args.has("position_ms")||args.optLong("position_ms",-1) !in 0..duration))return
  runCatching{control(op,args)}.onFailure{failed()}
 }
 fun surface(o:JSONObject,next:SurfaceHolder?){if(owner?.let{same(it,o)}==true){holder=next;runCatching{player?.setDisplay(next)}}}
 fun stop(stopService:Boolean=true){mainThread();if(liveRadio())owner?.let{o->SdkRadio.releasePending(o,queue.getJSONObject(index))};generation="";attachedGeneration="";cancelRadioReconnect();compatibleMp3?.delete();compatibleMp3=null;mp3Attempt="";radioShortEnds=0;radioStartedAt=0;radioConnection="";recoveryRetry?.let{main.removeCallbacks(it)};recoveryRetry=null;routeRetries=0;networkRecovery?.close();networkRecovery=null;main.removeCallbacks(sleepStop);sleepDeadline=0;chapterSleep=false;chapterSleepAt=0;runCatching{save()}.onFailure{android.util.Log.w("OnKlavMedia","Listening position could not be saved during handoff",it)};disposePlayer();releaseFocus();main.removeCallbacks(tick);wantPlay=false;runCatching{session?.isActive=false};runCatching{session?.release()};session=null;state="idle";queue=JSONArray();index=0;position=0;duration=0;owner=null;holder=null;changes.value++;val s=service;if(stopService){service=null;runCatching{s?.stopForeground(Service.STOP_FOREGROUND_REMOVE)};runCatching{s?.stopSelf()}}}
 /** Serializes with saved positions, so a delayed stop cannot recreate deleted app data. */
 fun clearPositions(c:Context,id:String)=AppSdk.carrierEgress{discardedPositions.add(id);check(c.getSharedPreferences("sdk-media-positions-$id",0).edit().clear().commit())}
 fun podcastDownloadRemoved(app:String,episode:String){mainThread();if(owner?.optString("id")==app&&queue.optJSONObject(index)?.let{it.optBoolean("podcast_local")&&it.optString("podcast_id")==episode}==true)stop()}
 fun changed(c:Context,id:String){val current=owner?:return;val epoch=generation;if(id.isEmpty()||current.optString("id")==id)main.post{if(generation==epoch&&owner===current)stop()}}
 fun detached(s:SdkPlaybackService){if(service===s){service=null;stop()}}
 internal fun notification(s:SdkPlaybackService):Notification{
  val manager=s.getSystemService(NotificationManager::class.java);manager.createNotificationChannel(NotificationChannel("sdk-media","App playback",NotificationManager.IMPORTANCE_LOW))
  fun action(op:String,icon:Int,label:String):Notification.Action{val intent=Intent(s,SdkPlaybackService::class.java).setData(Uri.parse("onklav://sdk-media/$generation/$op")).putExtra("generation",generation).putExtra("control",op);return Notification.Action.Builder(icon,label,PendingIntent.getService(s,0,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)).build()}
  val transportActive=state=="playing"||(liveRadio()&&state=="preparing")
  val actions=if(liveRadio())listOf(action(if(transportActive)"pause"else"play",if(transportActive)android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,if(transportActive)"Pause"else"Play"),action("stop",android.R.drawable.ic_menu_close_clear_cancel,"Stop"))else listOf(action("previous",android.R.drawable.ic_media_previous,"Previous"),action(if(transportActive)"pause"else"play",if(transportActive)android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,if(transportActive)"Pause"else"Play"),action("next",android.R.drawable.ic_media_next,"Next"),action("stop",android.R.drawable.ic_menu_close_clear_cancel,"Stop"))
  return Notification.Builder(s,"sdk-media").setSmallIcon(app.onklav.shared.R.drawable.ref_nav_apps).setContentTitle(queue.optJSONObject(index)?.optString("title")?:"App playback").setContentText(owner?.optString("name")?:"OnKlav").setOnlyAlertOnce(true).setOngoing(state in setOf("playing","preparing")).setVisibility(Notification.VISIBILITY_PRIVATE).setStyle(Notification.MediaStyle().setMediaSession(session?.sessionToken).setShowActionsInCompactView(*(if(liveRadio())intArrayOf(0,1)else intArrayOf(0,1,2)))).setActions(*actions.toTypedArray()).build()
 }
}

class SdkPlaybackService:Service(){
 override fun onBind(intent:Intent?)=null
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
  if(intent==null){stopSelf();return START_NOT_STICKY};val epoch=intent.getStringExtra("generation")?:""
  try{if(intent.hasExtra("control"))SdkMedia.system(intent.getStringExtra("control")?:"stop",epoch)else if(!SdkMedia.attach(this,epoch)&&!SdkMedia.hasOwner())stopSelfResult(startId)}catch(e:Exception){android.util.Log.w("OnKlavMedia","Playback startup failed",e);if(SdkMedia.accepts(epoch)){SdkMedia.serviceFailed();stopSelfResult(startId)}}
  return START_NOT_STICKY
 }
 internal fun refresh(){startForeground(0x53444d,SdkMedia.notification(this))}
 override fun onCreate(){super.onCreate();val filter=IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);if(Build.VERSION.SDK_INT>=33)registerReceiver(noisy,filter,RECEIVER_NOT_EXPORTED)else registerReceiver(noisy,filter)}
 private val noisy=object:BroadcastReceiver(){override fun onReceive(context:Context?,intent:Intent?){runCatching{SdkMedia.control("pause")}}}
 override fun onDestroy(){runCatching{unregisterReceiver(noisy)};runCatching{SdkMedia.detached(this)};super.onDestroy()}
}
