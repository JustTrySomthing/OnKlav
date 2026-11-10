package app.onklav.home

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Observations describe the last attempt, never assert permanent closure or a broadcast schedule. */
internal object SdkRadioHealth {
 internal data class Options(val autoHide:Boolean=true,val showHidden:Boolean=false)
 internal data class Notice(val code:String,val label:String,val message:String,val until:Long,val manual:Boolean=false)
 internal class Failure(val code:String,val label:String,message:String,val hide:Boolean=false):IllegalStateException(message)
 val options=MutableStateFlow(Options());val notices=MutableStateFlow<Map<String,Notice>>(emptyMap());val hidden=MutableStateFlow<Map<String,Long>>(emptyMap())
 private var loaded=false
 internal fun http(code:Int)=when(code){
  404,410->Failure("unavailable","Unavailable","This station is unavailable. It may be off air or its stream address may have changed.",true)
  429,503->Failure("busy","Server busy","The station's server is busy or temporarily unavailable. Try again shortly.")
  401,403->Failure("restricted","Access restricted","This station is restricting access. You can retry or choose another station.")
  else->Failure("server","Server problem","The station's server could not play this stream. Try again later.")
 }
 internal fun classify(e:Exception):Failure=when(e){is Failure->e;is java.io.InterruptedIOException->Failure("timeout","Connection timed out","The station took too long to respond. Retry or choose another station.");is javax.net.ssl.SSLException->Failure("secure","Secure connection failed","Could not establish a secure connection with this station. Retry later or choose another station.");is UnknownHostException->Failure("connection","Connection problem","Couldn't reach the station. Check your connection or try again later.");is java.io.IOException->Failure("connection","Connection problem","The connection was interrupted. Check your connection and retry.");else->Failure("connection","Could not connect","Couldn't connect to this station. Retry or choose another station.")}
 internal fun unsupported()=Failure("unsupported","Unsupported stream","This station did not provide audio the player can use. Try another station.",true)
 @Synchronized fun load(c:Context){if(loaded)return;val p=c.getSharedPreferences("radio-unavailable",0);val now=System.currentTimeMillis();val rows=linkedMapOf<String,Notice>();val hides=linkedMapOf<String,Long>()
  for((id,value)in p.all){if(value is Long&&value>now&&!id.startsWith("notice.")){hides[id]=value;rows[id]=Notice("unavailable","Unavailable","This station was unavailable on its last attempt.",value)} }
  for((key,value)in p.all){if(key.startsWith("notice.")&&value is String)runCatching{val j=JSONObject(value);val until=j.getLong("until");if(until>now)rows[key.removePrefix("notice.")]=Notice(j.getString("code"),j.getString("label"),j.getString("message"),until,j.optBoolean("manual"))}}
  options.value=Options(p.getBoolean("auto_hide",true),p.getBoolean("show_hidden",false));notices.value=rows;hidden.value=hides;loaded=true
 }
 internal fun isHidden(id:String):Boolean{val o=options.value;val n=notices.value[id];return !o.showHidden&&(n?.manual==true||o.autoHide)&&(hidden.value[id]?:0)>System.currentTimeMillis()}
 @Synchronized fun configure(c:Context,auto:Boolean?,show:Boolean?){load(c);val old=options.value;val next=Options(auto?:old.autoHide,show?:old.showHidden);options.value=next;c.getSharedPreferences("radio-unavailable",0).edit().putBoolean("auto_hide",next.autoHide).putBoolean("show_hidden",next.showHidden).apply()}
 @Synchronized fun record(c:Context,id:String,f:Failure,manual:Boolean=false){load(c);val ids=SdkRadioCatalog.aliases(id)+id;val now=System.currentTimeMillis();val manualChoice=manual||notices.value[id]?.manual==true;val until=now+if(f.hide||manualChoice)24*60*60*1000L else 15*60*1000L;val n=Notice(f.code,f.label,f.message?:f.label,until,manualChoice);notices.value=notices.value.filter{it.value.until>now}+ids.associateWith{n};if(f.hide||manualChoice)hidden.value=hidden.value.filter{it.value>now}+ids.associateWith{until}else hidden.value=hidden.value-ids.toSet()
  val p=c.getSharedPreferences("radio-unavailable",0).edit();val value=JSONObject().put("code",n.code).put("label",n.label).put("message",n.message).put("until",until).put("manual",manualChoice).toString();for(key in ids){p.putString("notice.$key",value);if(f.hide||manualChoice)p.putLong(key,until)else p.remove(key)};p.apply()
 }
 @Synchronized fun recovered(c:Context,id:String){load(c);val ids=(SdkRadioCatalog.aliases(id)+id).toSet();if(ids.none{it in notices.value||it in hidden.value})return;notices.value=notices.value-ids;hidden.value=hidden.value-ids;val edit=c.getSharedPreferences("radio-unavailable",0).edit();ids.forEach{edit.remove(it).remove("notice.$it")};edit.apply()}
 @Synchronized fun restore(c:Context){load(c);notices.value=emptyMap();hidden.value=emptyMap();val p=c.getSharedPreferences("radio-unavailable",0).edit().clear();p.putBoolean("auto_hide",options.value.autoHide).putBoolean("show_hidden",options.value.showHidden).apply()}
}
