package app.onklav.home

import android.Manifest
import android.app.*
import android.content.*
import android.net.Uri
import android.os.*
import android.provider.ContactsContract
import android.telecom.*
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID

internal data class PhoneLiveCall(val id:String,val number:String,val name:String,val state:Int,val connected:Long=0,val hold:Boolean=false,val merge:Boolean=false,val parent:String?=null,val children:List<String> = emptyList(),val video:Int=0,val status:String="",val separate:Boolean=false,val supportsVideo:Boolean=false)
internal data class PhoneAudio(val id:String,val name:String,val selected:Boolean,val earpiece:Boolean=false,val speaker:Boolean=false)
internal data class PhoneCallState(val calls:List<PhoneLiveCall> = emptyList(),val muted:Boolean=false,val audio:List<PhoneAudio> = emptyList(),val add:Boolean=false)
internal object PhoneCalls {
 val state=MutableStateFlow(PhoneCallState());val error=MutableStateFlow("");val keypad=MutableStateFlow(false);val waiting=MutableStateFlow<Map<String,String>>(emptyMap());val ended=MutableStateFlow<PhoneLiveCall?>(null);internal var service:PhoneInCallService?=null;private var visibleScreen:Any?=null
 internal val screenVisible get()=visibleScreen!=null
 fun screen(owner:Any,visible:Boolean){if(visible)visibleScreen=owner else if(visibleScreen===owner)visibleScreen=null;service?.let{runCatching{it.publish()}.onFailure{error->android.util.Log.w("OnKlavPhone","Call presentation update failed",error)}}}
 fun action(id:String,action:String){val s=service?:return;s.main.post{runCatching{check(PhoneAccess.role(s));s.act(id,action)}.onFailure{error.value="This call action is not available right now."}}}
 private var previousAudio:String?=null
 internal fun resetSpeakerMemory(){previousAudio=null}
 internal fun speakerTarget(routes:List<PhoneAudio>,previous:String?):PhoneAudio? = if(routes.any{it.selected&&it.speaker})routes.firstOrNull{it.id==previous&&!it.speaker}?:routes.firstOrNull{it.earpiece}?:routes.firstOrNull{!it.speaker}else routes.firstOrNull{it.speaker}
 fun toggleSpeaker(){
  service?.let{s->s.main.post{
   val routes=state.value.audio
   if(routes.none{it.selected&&it.speaker})previousAudio=routes.firstOrNull{it.selected&&!it.speaker}?.id
   val target=speakerTarget(routes,previousAudio)
   if(target==null)error.value="Speaker is not available for this call."
   else runCatching{s.route(target.id)}.onFailure{error.value="Audio could not be changed."}
  }}
 }
 fun audio(id:String){service?.let{s->s.main.post{runCatching{s.route(id)}.onFailure{error.value="Audio could not be changed."}}}}
 fun tone(id:String,key:Char){if(key !in "0123456789*#")return;service?.let{s->s.main.post{s.tone(id,key)}}}
 fun show(c:Context){runCatching{c.startActivity(Intent(c,PhoneCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))}.onFailure{error.value="Open the call notification to return to your call."}}
 fun reconcile(c:Context){if(service==null&&state.value.calls.isEmpty())c.runCatching{getSystemService(NotificationManager::class.java).cancel(7310)}}
 fun status(state:Int)=when(state){Call.STATE_NEW,Call.STATE_SELECT_PHONE_ACCOUNT->"Connecting";Call.STATE_CONNECTING->"Connecting";Call.STATE_DIALING->"Calling";Call.STATE_RINGING->"Incoming call";Call.STATE_ACTIVE->"Connected";Call.STATE_HOLDING->"On hold";Call.STATE_DISCONNECTING->"Ending call";Call.STATE_DISCONNECTED->"Call ended";Call.STATE_PULLING_CALL->"Transferring call";else->"Call"}
}
/** Telecom binds this service with BIND_INCALL_SERVICE; it never creates its own connection. */
@Suppress("DEPRECATION")
class PhoneInCallService:InCallService(){
 internal val main=Handler(Looper.getMainLooper());private val callsById=linkedMapOf<String,Call>();private val callbacks=mutableMapOf<Call,Call.Callback>();private val observedStates=mutableMapOf<Call,Int>();private val shownRinging=mutableSetOf<String>();private val openedOutgoing=mutableSetOf<String>();private var lastRingingNotice:String?=null;private val names=mutableMapOf<String,String>();private val connected=mutableMapOf<String,Long>();private val lookup=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
 private var audioState:CallAudioState?=null;private var endpoints:List<CallEndpoint> = emptyList();private var endpoint:CallEndpoint?=null;private var muted=false;private var canAdd=false;private var toneStop:Runnable?=null;private var toneCall:Call?=null
 override fun onCreate(){super.onCreate();PhoneCalls.service=this;PhoneCalls.error.value="";PhoneCalls.state.value=PhoneCallState();PhoneCalls.waiting.value=emptyMap();PhoneCalls.keypad.value=false;runCatching{getSystemService(NotificationManager::class.java).cancel(7310)}}
 override fun onCallAdded(call:Call){super.onCallAdded(call);if(id(call)!=null)return;val id=UUID.randomUUID().toString();callsById[id]=call;PhoneCalls.ended.value=null
  val cb=object:Call.Callback(){override fun onStateChanged(c:Call,state:Int){if(PhoneCalls.service!==this@PhoneInCallService||id(c)==null)return;if(observedStates[c]!=Call.STATE_DISCONNECTED)observedStates[c]=state;showOutgoing(c,state);publish()};override fun onDetailsChanged(c:Call,d:Call.Details){publish()};override fun onParentChanged(c:Call,parent:Call?){publish()};override fun onChildrenChanged(c:Call,children:MutableList<Call>){publish()};override fun onConferenceableCallsChanged(c:Call,list:MutableList<Call>){publish()};override fun onVideoCallChanged(c:Call,v:InCallService.VideoCall?){publish()};override fun onCallDestroyed(c:Call){remove(c)};override fun onPostDialWait(c:Call,remaining:String){if(PhoneCalls.service===this@PhoneInCallService&&id(c)!=null&&observedStates[c]!=Call.STATE_DISCONNECTED)PhoneCalls.waiting.value=PhoneCalls.waiting.value+(id to remaining);publish()}}
  callbacks[call]=cb;call.registerCallback(cb,main);publish();val number=call.details.handle?.schemeSpecificPart.orEmpty()
  if(number.isNotBlank()&&call.details.handlePresentation==TelecomManager.PRESENTATION_ALLOWED&&PhoneAccess.permission(this,Manifest.permission.READ_CONTACTS))lookup.launch{val name=withContext(Dispatchers.IO){runCatching{contentResolver.query(Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,Uri.encode(number)),arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())it.getString(0)else null}}.getOrNull()};if(callsById[id]===call&&name!=null){names[id]=name;publish()}}
  if(call.state!=Call.STATE_RINGING&&call.state!=Call.STATE_DISCONNECTED&&call.state!=Call.STATE_DISCONNECTING&&(call.state!=Call.STATE_NEW||call.details.callDirection==Call.Details.DIRECTION_OUTGOING)&&PhoneAccess.role(this)){openedOutgoing.add(id);PhoneCalls.show(this)}
 }
 override fun onCallRemoved(call:Call){super.onCallRemoved(call);remove(call)}
 private fun remove(call:Call){
  val key=id(call)?:return
  if(PhoneCalls.service===this){
   runCatching{if(call.details.disconnectCause?.code==DisconnectCause.MISSED)missed(call)}.onFailure{android.util.Log.w("OnKlavPhone","Missed-call cleanup failed",it)}
   PhoneCalls.state.value.calls.firstOrNull{it.id==key&&it.parent==null}?.let{PhoneCalls.ended.value=it.copy(state=Call.STATE_DISCONNECTED)}
   PhoneCalls.waiting.value=PhoneCalls.waiting.value-key
  }
  val callback=callbacks.remove(call);callsById.remove(key);callback?.let{runCatching{call.unregisterCallback(it)}};shownRinging.remove(key);openedOutgoing.remove(key);observedStates.remove(call);names.remove(key);connected.remove(key)
  if(toneCall===call)stopTone();publish()
 }
 override fun onCallAudioStateChanged(s:CallAudioState){audioState=s;muted=s.isMuted;if(Build.VERSION.SDK_INT<34)publish()}
 override fun onCanAddCallChanged(value:Boolean){canAdd=value;publish()}
 override fun onCallEndpointChanged(e:CallEndpoint){endpoint=e;publish()}
 override fun onAvailableCallEndpointsChanged(available:MutableList<CallEndpoint>){endpoints=available.toList();publish()}
 override fun onMuteStateChanged(value:Boolean){muted=value;publish()}
 private fun id(call:Call?)=callsById.entries.firstOrNull{it.value===call}?.key
 private fun callState(c:Call)=observedStates[c]?:c.state
 private fun showOutgoing(c:Call,state:Int){
  val key=id(c)?:return
  if(callState(c)!=Call.STATE_DISCONNECTED&&state in setOf(Call.STATE_CONNECTING,Call.STATE_DIALING,Call.STATE_SELECT_PHONE_ACCOUNT)&&c.details.callDirection!=Call.Details.DIRECTION_INCOMING&&PhoneAccess.role(this)&&openedOutgoing.add(key))PhoneCalls.show(this)
 }
 private fun snapshot(key:String,c:Call):PhoneLiveCall {val d=c.details;val state=callState(c);if(state==Call.STATE_ACTIVE&&!connected.containsKey(key)){val ago=(System.currentTimeMillis()-d.connectTimeMillis).coerceIn(0,7*86400000L);connected[key]=SystemClock.elapsedRealtime()-if(d.connectTimeMillis>0)ago else 0};val shown=d.handlePresentation==TelecomManager.PRESENTATION_ALLOWED
  return PhoneLiveCall(key,if(shown)d.handle?.schemeSpecificPart.orEmpty()else"",if(shown)names[key]?:d.callerDisplayName.orEmpty()else"Private number",state,connected[key]?:0,d.can(Call.Details.CAPABILITY_HOLD),d.can(Call.Details.CAPABILITY_MERGE_CONFERENCE)||c.conferenceableCalls.isNotEmpty(),id(c.parent),c.children.mapNotNull(::id),d.videoState,d.disconnectCause?.label?.toString().orEmpty(),d.can(Call.Details.CAPABILITY_SEPARATE_FROM_CONFERENCE),d.can(Call.Details.CAPABILITY_SUPPORTS_VT_LOCAL_BIDIRECTIONAL))}
 private fun routes():List<PhoneAudio>{if(Build.VERSION.SDK_INT>=34&&endpoints.isNotEmpty())return endpoints.map{PhoneAudio(it.identifier.toString(),it.endpointName.toString(),it.identifier==endpoint?.identifier,it.endpointType==CallEndpoint.TYPE_EARPIECE,it.endpointType==CallEndpoint.TYPE_SPEAKER)};val a=audioState?:return emptyList();return listOf(CallAudioState.ROUTE_EARPIECE to "Phone",CallAudioState.ROUTE_SPEAKER to "Speaker",CallAudioState.ROUTE_WIRED_HEADSET to "Headphones",CallAudioState.ROUTE_BLUETOOTH to "Bluetooth").filter{a.supportedRouteMask and it.first!=0}.map{PhoneAudio(it.first.toString(),it.second,a.route==it.first,it.first==CallAudioState.ROUTE_EARPIECE,it.first==CallAudioState.ROUTE_SPEAKER)}}
 internal fun publish(){
  if(PhoneCalls.service!==this)return
  // A disconnected Call can remain in Telecom until removal. Never publish it
  // as live, or let delayed detail/name callbacks revive its notification.
  val snapshots=callsById.toList().mapNotNull{(key,call)->runCatching{snapshot(key,call)}.onFailure{android.util.Log.w("OnKlavPhone","Detached Telecom call ignored",it)}.getOrNull()}
  snapshots.lastOrNull{it.parent==null&&it.state==Call.STATE_DISCONNECTED}?.let{PhoneCalls.ended.value=it}
  val list=snapshots.filter{it.state!=Call.STATE_DISCONNECTED}
  PhoneCalls.waiting.value=PhoneCalls.waiting.value.filterKeys{key->list.any{it.id==key}}
  if(toneCall?.let{observedStates[it]?:it.state}==Call.STATE_DISCONNECTED)stopTone()
  if(list.isEmpty()){PhoneCalls.keypad.value=false;PhoneCalls.error.value=""}
  if(list.isEmpty())PhoneCalls.resetSpeakerMemory()
  PhoneCalls.state.value=PhoneCallState(list,muted,runCatching{routes()}.getOrDefault(emptyList()),canAdd);runCatching{notice(list)}.onFailure{android.util.Log.w("OnKlavPhone","Call notification update failed",it)}
 }
 internal fun call(id:String)=callsById[id]
 internal fun act(id:String,action:String){val c=callsById[id]?:return;val state=callState(c);if(state==Call.STATE_DISCONNECTED)return;when(action){
  "answer"->if(state==Call.STATE_RINGING)c.answer(c.details.videoState);"audio-answer"->if(state==Call.STATE_RINGING)c.answer(VideoProfile.STATE_AUDIO_ONLY);"decline"->if(state==Call.STATE_RINGING)c.reject(false,null);"end"->c.disconnect();"mute"->setMuted(!muted);"hold"->if(c.details.can(Call.Details.CAPABILITY_HOLD)){if(state==Call.STATE_HOLDING)c.unhold()else c.hold()};"merge"->if(c.details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE))c.mergeConference()else c.conferenceableCalls.firstOrNull()?.let(c::conference);"swap"->if(c.details.can(Call.Details.CAPABILITY_SWAP_CONFERENCE))c.swapConference()else{val held=callsById.values.firstOrNull{callState(it)==Call.STATE_HOLDING};callsById.values.firstOrNull{callState(it)==Call.STATE_ACTIVE}?.hold();held?.unhold()};"split"->if(c.details.can(Call.Details.CAPABILITY_SEPARATE_FROM_CONFERENCE))c.splitFromConference();"continue"->{c.postDialContinue(true);PhoneCalls.waiting.value=PhoneCalls.waiting.value-id};"cancel-digits"->{c.postDialContinue(false);PhoneCalls.waiting.value=PhoneCalls.waiting.value-id}
 };publish()}
 internal fun selectAccount(id:String,handle:PhoneAccountHandle){runCatching{check(PhoneAccess.role(this));check(PhoneRepository(this).lines().any{it.handle==handle});callsById[id]?.phoneAccountSelected(handle,false)}.onFailure{PhoneCalls.error.value="Calling account is no longer available."}}
 internal fun route(id:String){check(PhoneAccess.role(this));if(Build.VERSION.SDK_INT>=34&&endpoints.isNotEmpty()){val e=endpoints.firstOrNull{it.identifier.toString()==id}?:error("Audio route is no longer available");requestCallEndpointChange(e,mainExecutor,object:OutcomeReceiver<Void,CallEndpointException>{override fun onResult(result:Void?){};override fun onError(error:CallEndpointException){PhoneCalls.error.value="Audio could not be changed."}})}else id.toIntOrNull()?.let{setAudioRoute(it)}}
 internal fun tone(id:String,key:Char){if(!PhoneAccess.role(this))return;val c=callsById[id]?:return;if(callState(c)!=Call.STATE_ACTIVE)return;stopTone();toneCall=c;runCatching{c.playDtmfTone(key)};toneStop=Runnable{stopTone()}.also{main.postDelayed(it,180)}}
 private fun stopTone(){toneStop?.let(main::removeCallbacks);toneStop=null;toneCall?.let{runCatching{it.stopDtmfTone()}};toneCall=null}
 private fun action(id:String,action:String):PendingIntent {val open=action in setOf("answer","audio-answer");val intent=Intent(this,if(open)PhoneCallActivity::class.java else PhoneCallActionReceiver::class.java).setData(Uri.parse("onklav-phone:$id/$action")).putExtra("call",id).putExtra("action",action);val flags=PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE;return if(open)PendingIntent.getActivity(this,0,intent,flags)else PendingIntent.getBroadcast(this,0,intent,flags)}

 private fun notice(list:List<PhoneLiveCall>){val nm=getSystemService(NotificationManager::class.java);val roots=list.filter{it.parent==null&&it.state!=Call.STATE_DISCONNECTED};val c=roots.firstOrNull{it.state==Call.STATE_RINGING}?:roots.firstOrNull{it.state==Call.STATE_ACTIVE}?:roots.firstOrNull();if(c==null||!PhoneAccess.role(this)){lastRingingNotice=null;nm.cancel(7310);return}
  if(c.state==Call.STATE_RINGING&&PhoneCalls.screenVisible){shownRinging.add(c.id);lastRingingNotice=c.id;nm.cancel(7310);return}
  // A fresh incoming channel is needed because existing badge settings persist.
  val channel=NotificationChannel("phone-incoming-v2","Incoming calls",NotificationManager.IMPORTANCE_HIGH).apply{description="Incoming phone calls";setSound(null,null);enableVibration(false);setShowBadge(false);lockscreenVisibility=Notification.VISIBILITY_PRIVATE};nm.createNotificationChannel(channel)
  nm.createNotificationChannel(NotificationChannel("phone-ongoing","Ongoing calls",NotificationManager.IMPORTANCE_LOW).apply{description="Return to an active phone call";setSound(null,null);enableVibration(false);setShowBadge(false);lockscreenVisibility=Notification.VISIBILITY_PRIVATE})
  val open=PendingIntent.getActivity(this,7310,Intent(this,PhoneCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  val person=Person.Builder().setName(c.name.ifBlank{c.number.ifBlank{"Caller"}}).setImportant(true).build();val ringing=c.state==Call.STATE_RINGING
  val style=if(ringing)NotificationCompat.CallStyle.forIncomingCall(person,action(c.id,"decline"),action(c.id,if(VideoProfile.isVideo(c.video))"audio-answer"else"answer"))else NotificationCompat.CallStyle.forOngoingCall(person,action(c.id,"end"))
  val b=NotificationCompat.Builder(this,if(ringing)"phone-incoming-v2"else"phone-ongoing").setSmallIcon(app.onklav.shared.R.drawable.ref_app_phone).setStyle(style).setContentTitle(person.name).setContentText(PhoneCalls.status(c.state)).setCategory(NotificationCompat.CATEGORY_CALL).setPriority(if(ringing)NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_LOW).setBadgeIconType(NotificationCompat.BADGE_ICON_NONE).setOngoing(true).setOnlyAlertOnce(!ringing||lastRingingNotice==c.id).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setContentIntent(open)
  if(ringing&&c.id !in shownRinging)b.setFullScreenIntent(open,true)
  if(Build.VERSION.SDK_INT<33||checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED){nm.notify(7310,b.build());lastRingingNotice=if(ringing)c.id else null}
 }
 private fun missed(call:Call){
  if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)return
  val key=id(call)?:return;val d=snapshot(key,call);val nm=getSystemService(NotificationManager::class.java);nm.createNotificationChannel(NotificationChannel("phone-missed","Missed calls",NotificationManager.IMPORTANCE_DEFAULT))
  val open=PendingIntent.getActivity(this,7311,Intent(this,PhoneDialActivity::class.java).setAction(Intent.ACTION_DIAL).putExtra("phone_tab","Recents"),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  val notification=NotificationCompat.Builder(this,"phone-missed").setSmallIcon(app.onklav.shared.R.drawable.ref_app_phone).setContentTitle("Missed call").setContentText(d.name.ifBlank{d.number.ifBlank{"Unknown caller"}}).setContentIntent(open).setAutoCancel(true).setCategory(NotificationCompat.CATEGORY_MISSED_CALL).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build();nm.notify(7311,notification)
 }
 override fun onBringToForeground(showDialpad:Boolean){PhoneCalls.keypad.value=showDialpad;PhoneCalls.show(this)}
 override fun onSilenceRinger(){super.onSilenceRinger()}
 private fun clearSession(){
  val detached=callbacks.toList();callbacks.clear();detached.forEach{(c,cb)->runCatching{c.unregisterCallback(cb)}};callsById.clear();shownRinging.clear();openedOutgoing.clear();lastRingingNotice=null;observedStates.clear();names.clear();connected.clear();stopTone()
  if(PhoneCalls.service===this){PhoneCalls.state.value=PhoneCallState();PhoneCalls.waiting.value=emptyMap();PhoneCalls.keypad.value=false;PhoneCalls.error.value="";runCatching{getSystemService(NotificationManager::class.java).cancel(7310)}}
 }
 override fun onUnbind(intent:Intent):Boolean{clearSession();return super.onUnbind(intent)}
 override fun onDestroy(){clearSession();lookup.cancel();if(PhoneCalls.service===this)PhoneCalls.service=null;super.onDestroy()}
}
class PhoneCallActionReceiver:BroadcastReceiver(){override fun onReceive(c:Context,intent:Intent){val id=intent.getStringExtra("call")?:return;val action=intent.getStringExtra("action")?:return;if(action in setOf("decline","end"))PhoneCalls.action(id,action)}}
