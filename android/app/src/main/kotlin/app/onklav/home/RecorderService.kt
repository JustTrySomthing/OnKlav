package app.onklav.home

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.*
import android.media.projection.*
import android.os.*
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID
import java.util.concurrent.Executors
import app.onklav.shared.R

internal object RecordingRuntime{
 val state=MutableStateFlow(RecorderState())
 @Volatile internal var factory:((Context,String,RecorderOptions,MediaProjection?,(String)->Unit)->RecorderBackend)?=null
 @Synchronized fun start(c:Context,o:RecorderOptions,approval:Intent?=null){
  check(!state.value.active){"Stop the current recording first"}
  require(o.countdown in listOf(0,3,5,10))
  if(o.kind==RecorderKind.SCREEN)require(approval!=null){"Approve screen capture to start"}
  if(o.audio)check(ContextCompat.checkSelfPermission(c,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED){"Allow audio recording for this sound choice"}
  val id=UUID.randomUUID().toString().replace("-","")
  state.value=RecorderState(token=id,options=o,phase=RecorderPhase.STARTING,message="Preparing recorder…",revision=state.value.revision)
  try{ContextCompat.startForegroundService(c,Intent(c,RecorderService::class.java).setAction("start").putExtra("token",id).putExtra("kind",o.kind.name).putExtra("sound",o.sound.name).putExtra("format",o.format.name).putExtra("high",o.high).putExtra("countdown",o.countdown).putExtra("approval",approval))}
  catch(e:Exception){state.value=RecorderState(error=e.message?:"Recorder could not start",revision=state.value.revision+1);throw e}
 }
 fun control(c:Context,command:String){val s=state.value;if(s.active)c.startService(Intent(c,RecorderService::class.java).setAction(command).putExtra("token",s.token))}
}
/** A user-started recording owns one foreground-service lease. Never restarts or
 * reuses screen approval after process death. All backend transitions are serial. */
class RecorderService:Service(){
 private val main=Handler(Looper.getMainLooper());private val worker=Executors.newSingleThreadExecutor()
 private var token="";private var options:RecorderOptions?=null;@Volatile private var engine:RecorderBackend?=null
 private var projection:MediaProjection?=null;private var wake:PowerManager.WakeLock?=null
 private var focus:AudioFocusRequest?=null;private var clock=RecorderClock()
 @Volatile private var ending=false
 private var destroyed=false;private var finishedState:RecorderState?=null
 private var lastNotice=0L;private var lastWake=0L
 override fun onBind(intent:Intent?)=null
 override fun onCreate(){super.onCreate();getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("onklav-recorders","Recordings",NotificationManager.IMPORTANCE_LOW).apply{setSound(null,null)})}
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
  if(intent==null){stopSelf(startId);return START_NOT_STICKY}
  val id=intent.getStringExtra("token").orEmpty()
  if(intent.action=="start"){
   if(token.isNotEmpty()||id!=RecordingRuntime.state.value.token||id.isEmpty()){if(token.isEmpty())stopSelf(startId);return START_NOT_STICKY}
   token=id;options=runCatching{RecorderOptions(RecorderKind.valueOf(intent.getStringExtra("kind")!!),RecorderSound.valueOf(intent.getStringExtra("sound")!!),RecorderFormat.valueOf(intent.getStringExtra("format")!!),intent.getBooleanExtra("high",false),intent.getIntExtra("countdown",3))}.getOrElse{stopSelf(startId);return START_NOT_STICKY}
   try{
    val o=options!!;var type=if(o.kind==RecorderKind.SCREEN)ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0
    if(o.microphone&&Build.VERSION.SDK_INT>=30)type=type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
    startForeground(NOTICE,notification(),type)
    wake=getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"OnKlav:recording").apply{setReferenceCounted(false);acquire(300000)}
    @Suppress("DEPRECATION") val approval=if(Build.VERSION.SDK_INT>=33)intent.getParcelableExtra("approval",Intent::class.java)else intent.getParcelableExtra<Intent>("approval")
    worker.execute{prepare(approval)};main.post(tick)
   }catch(e:Exception){fail(e.message?:"Recording permission is unavailable")}
  }else if(id==token&&token.isNotEmpty())when(intent.action){"stop"->finish(discard=RecordingRuntime.state.value.phase in setOf(RecorderPhase.STARTING,RecorderPhase.COUNTDOWN));"pause"->pause();"resume"->resume()}
  else if(token.isEmpty())stopSelf(startId)
  return START_NOT_STICKY
 }
 private fun update(change:(RecorderState)->RecorderState){val s=RecordingRuntime.state.value;if(s.token==token)RecordingRuntime.state.value=change(s)}
 private fun prepare(approval:Intent?){try{
  val o=options!!;RecorderLibrary.create(this,token,o)
  if(o.kind==RecorderKind.SCREEN){check(approval!=null);projection=getSystemService(MediaProjectionManager::class.java).getMediaProjection(Activity.RESULT_OK,approval)
   projection!!.registerCallback(object:MediaProjection.Callback(){override fun onStop(){main.post{finish("Android stopped screen capture")}}},main)
  }
  main.post{if(!ending){if(o.kind==RecorderKind.SCREEN&&o.countdown>0)count(o.countdown)else begin()}}
 }catch(e:Exception){main.post{fail(e.message?:"Recorder could not prepare")}}}
 private fun count(value:Int){if(ending)return;if(value==0){begin();return};update{it.copy(phase=RecorderPhase.COUNTDOWN,countdown=value,message="Starts in $value · switch apps when ready")};main.postDelayed({count(value-1)},1000)}
 private fun begin(){if(ending)return;update{it.copy(phase=RecorderPhase.STARTING,countdown=0,message="Starting recording…")}
  worker.execute{try{
   if(ending)return@execute
   val o=options!!
   if(o.kind==RecorderKind.VOICE)acquireFocus()
   val backend=RecordingRuntime.factory?.invoke(this,token,o,projection){reason->main.post{finish(reason)}}?:AndroidRecorderBackend(this,token,o,projection){reason->main.post{finish(reason)}}
   engine=backend;backend.start();clock.resume()
   main.post{if(!ending)update{it.copy(phase=RecorderPhase.RECORDING,message="Recording",error=null)}}
  }catch(e:Exception){main.post{fail(e.message?:"Recording could not start")}}}
 }
 private fun acquireFocus(){val audio=getSystemService(AudioManager::class.java);check(audio.mode==AudioManager.MODE_NORMAL){"Finish your phone call before recording"}
  val request=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()).setOnAudioFocusChangeListener({change->if(change<0)main.post{pause("Paused by another audio app")}},main).build()
  check(audio.requestAudioFocus(request)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED){"Microphone audio is busy. Try again."};focus=request
 }
 private fun dropFocus(){focus?.let{runCatching{getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it)}};focus=null}
 private fun pause(reason:String="Paused"){
  val s=RecordingRuntime.state.value;if(ending||s.phase!=RecorderPhase.RECORDING||s.controlling)return
  update{it.copy(controlling=true)};worker.execute{try{engine?.pause();clock.pause();dropFocus();main.post{if(!ending)update{it.copy(phase=RecorderPhase.PAUSED,elapsed=clock.elapsed(),controlling=false,message=reason,error=null)}}}catch(e:Exception){main.post{if(!ending)update{it.copy(controlling=false,error=e.message?:"Could not pause")}}}}
 }
 private fun resume(){val s=RecordingRuntime.state.value;if(ending||s.phase!=RecorderPhase.PAUSED||s.controlling)return
  update{it.copy(controlling=true)};worker.execute{try{if(options?.kind==RecorderKind.VOICE)acquireFocus();engine?.resume();clock.resume();main.post{if(!ending)update{it.copy(phase=RecorderPhase.RECORDING,controlling=false,message="Recording",error=null)}}}catch(e:Exception){dropFocus();main.post{if(!ending)update{it.copy(controlling=false,error=e.message?:"Could not resume")}}}}
 }
 private fun fail(reason:String){finish(reason,true)}
 private fun finish(reason:String="",failed:Boolean=false,discard:Boolean=false){if(ending)return;ending=true;main.removeCallbacks(tick);val wasRecording=engine!=null
  update{it.copy(phase=RecorderPhase.FINISHING,elapsed=clock.elapsed(),message=if(discard)"Cancelling recording…"else"Saving recording…",controlling=true)}
  getSystemService(NotificationManager::class.java).notify(NOTICE,notification())
  worker.execute{clock.pause();val elapsed=clock.elapsed();var message=if(wasRecording)"Recording saved"else"Recording cancelled";var error:String?=null
   try{
    val backend=engine
    if(discard){releaseCapture();RecorderLibrary.removeDraft(this,token);message="Recording cancelled"}
    else if(backend!=null){val result=backend.finish();java.io.RandomAccessFile(result.file,"rw").use{it.fd.sync()};val savedWarning=listOf(reason.takeIf{it.isNotBlank()},result.warning.takeIf{it.isNotBlank()}).filterNotNull().joinToString(" · ");RecorderLibrary.ready(this,token,elapsed,savedWarning);releaseCapture();try{RecorderLibrary.publish(this,token);message=if(result.warning.isNotEmpty())savedWarning else if(reason.isNotEmpty())"$reason · recording saved"else"Saved in Files › ${options!!.kind.root.replaceFirstChar{it.uppercase()}} › ${options!!.kind.folder}"}catch(e:Exception){message="Recording kept on this device";error="Could not file it yet. Use Save to Files in Recordings. ${e.message.orEmpty().take(120)}"}}
    else{releaseCapture();RecorderLibrary.removeDraft(this,token);if(failed)error=reason}
   }catch(e:Exception){releaseCapture();message="Recording draft kept";error=e.message?:"Recording could not be finalized. Check interrupted recordings."}
   finally{releaseCapture();runCatching{if(wake?.isHeld==true)wake?.release()};wake=null;main.post{val old=RecordingRuntime.state.value;finishedState=RecorderState(options=options,message=message,error=error,revision=old.revision+1);if(destroyed)publishFinished()else{stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()}}}
  }
 }
 private fun releaseCapture(){runCatching{engine?.release()};engine=null;dropFocus();runCatching{projection?.stop()};projection=null}
 private val tick=object:Runnable{override fun run(){if(ending)return;val s=RecordingRuntime.state.value
  if(s.phase==RecorderPhase.RECORDING||s.phase==RecorderPhase.PAUSED){val level=if(s.phase==RecorderPhase.RECORDING)runCatching{engine?.level()?:0f}.getOrDefault(0f)else 0f;val muted=options?.microphone==true&&(getSystemService(AudioManager::class.java).isMicrophoneMute||runCatching{engine?.silenced()==true}.getOrDefault(false))
   update{it.copy(elapsed=clock.elapsed(),bytes=engine?.bytes()?:0,levels=(it.levels+level).takeLast(60),message=if(muted&&it.phase==RecorderPhase.RECORDING)"Microphone unavailable or silenced by Android"else if(it.message=="Microphone unavailable or silenced by Android"){if(it.phase==RecorderPhase.PAUSED)"Paused"else"Recording"}else it.message)}
   if(options?.microphone==true&&getSystemService(AudioManager::class.java).mode!=AudioManager.MODE_NORMAL)pause("Paused for a phone call")
   if(RecorderLibrary.directory(this@RecorderService).usableSpace<96L*1024*1024){finish("Storage is low");return}
  }
  val now=SystemClock.elapsedRealtime();if(now-lastWake>60000){wake?.acquire(300000);lastWake=now};if(now-lastNotice>=1000){getSystemService(NotificationManager::class.java).notify(NOTICE,notification());lastNotice=now};main.postDelayed(this,100)
 }}
 private fun action(command:String):PendingIntent=PendingIntent.getService(this,command.hashCode(),Intent(this,RecorderService::class.java).setAction(command).setData(android.net.Uri.parse("onklav-recording:$token:$command")).putExtra("token",token),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
 private fun notification():Notification{
  val s=RecordingRuntime.state.value;val open=PendingIntent.getActivity(this,NOTICE,Intent(this,MainActivity::class.java).putExtra("open_native_recorder",options?.kind?.tool).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  return Notification.Builder(this,"onklav-recorders").setSmallIcon(if(options?.kind==RecorderKind.SCREEN)R.drawable.recorder_screen else R.drawable.recorder_mic).setContentTitle(options?.kind?.title?:"Recorder").setContentText("${s.message} · ${recorderTime(s.elapsed)}").setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PRIVATE).apply{
   if(s.phase==RecorderPhase.RECORDING||s.phase==RecorderPhase.PAUSED)addAction(Notification.Action.Builder(null,if(s.phase==RecorderPhase.PAUSED)"Resume"else"Pause",action(if(s.phase==RecorderPhase.PAUSED)"resume"else"pause")).build())
   if(s.phase!=RecorderPhase.FINISHING)addAction(Notification.Action.Builder(null,if(s.phase in setOf(RecorderPhase.COUNTDOWN,RecorderPhase.STARTING))"Cancel"else"Stop & save",action("stop")).build())
  }.build()
 }
 private fun publishFinished(){val complete=finishedState?:return;if(RecordingRuntime.state.value.token==token)RecordingRuntime.state.value=complete}
 override fun onDestroy(){destroyed=true;main.removeCallbacks(tick);if(!ending&&token.isNotEmpty())finish("Recording service stopped");publishFinished();worker.shutdown();super.onDestroy()}
 companion object{private const val NOTICE=0x524543}
}
