package app.onklav.home

import android.Manifest
import android.app.Application
import android.app.role.RoleManager
import android.content.*
import android.database.ContentObserver
import android.net.Uri
import android.os.*
import android.provider.*
import android.telecom.*
import android.telephony.PhoneNumberUtils
import androidx.compose.runtime.*
import androidx.lifecycle.*
import kotlinx.coroutines.*

internal object PhoneAccess {
 fun role(c:Context)=c.getSystemService(TelecomManager::class.java)?.defaultDialerPackage==c.packageName
 fun available(c:Context)=c.getSystemService(RoleManager::class.java)?.isRoleAvailable(RoleManager.ROLE_DIALER)==true
 fun permission(c:Context,p:String)=OnKlavPermissions.granted(c,p)
 val permissions=listOf(Manifest.permission.CALL_PHONE,Manifest.permission.READ_PHONE_STATE,Manifest.permission.READ_CALL_LOG,Manifest.permission.WRITE_CALL_LOG,Manifest.permission.READ_CONTACTS,Manifest.permission.WRITE_CONTACTS)
 fun request(c:Context)=c.getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_DIALER)
}
internal object PhoneRules {
 fun input(raw:String):String=PhoneNumberUtils.convertKeypadLettersToDigits(raw.removePrefix("tel:")).filter{it in "0123456789+*#,;"}.take(80).let{s->s.filterIndexed{i,c->c!='+'||i==0}}
 fun dialIntent(intent:Intent?):String=if(intent?.action in setOf(Intent.ACTION_DIAL,Intent.ACTION_VIEW)&&intent?.data?.scheme.equals("tel",true))input(intent?.data?.schemeSpecificPart.orEmpty())else""
 fun callable(number:String,presentation:Int=TelecomManager.PRESENTATION_ALLOWED)=presentation==TelecomManager.PRESENTATION_ALLOWED&&number.any{it.isDigit()||it=='*'||it=='#'}&&input(number)==number&&number.length<=80
 fun display(number:String):String=if(number.any{it in "*#,;"})number else android.telephony.PhoneNumberUtils.formatNumber(number,java.util.Locale.getDefault().country.ifBlank{"US"})?.takeIf{it.isNotBlank()}?:number
 fun typing(number:String,country:String=java.util.Locale.getDefault().country.ifBlank{"US"}):String {
  if(number.isEmpty()||number.any{it in "*#,;"})return number
  return runCatching{val text=android.text.SpannableStringBuilder(number);android.telephony.PhoneNumberFormattingTextWatcher(country).afterTextChanged(text);text.toString().takeIf{input(it)==number}?:number}.getOrDefault(number)
 }
 fun duration(seconds:Long):String {val n=seconds.coerceAtLeast(0);return if(n>=3600)"${n/3600}:${(n/60%60).toString().padStart(2,'0')}:${(n%60).toString().padStart(2,'0')}"else"${n/60}:${(n%60).toString().padStart(2,'0')}"}
 fun type(type:Int)=when(type){CallLog.Calls.INCOMING_TYPE->"Incoming";CallLog.Calls.OUTGOING_TYPE->"Outgoing";CallLog.Calls.MISSED_TYPE->"Missed";CallLog.Calls.REJECTED_TYPE->"Declined";CallLog.Calls.BLOCKED_TYPE->"Blocked";CallLog.Calls.VOICEMAIL_TYPE->"Voicemail";else->"Call"}
}
internal data class PhoneEvent(val id:Long,val number:String,val name:String,val type:Int,val date:Long,val duration:Long,val presentation:Int=TelecomManager.PRESENTATION_ALLOWED){
 val title get()=if(presentation==TelecomManager.PRESENTATION_RESTRICTED)"Private number"else if(presentation!=TelecomManager.PRESENTATION_ALLOWED)"Unknown caller"else name.ifBlank{number.ifBlank{"Unknown caller"}}
 val callable get()=PhoneRules.callable(PhoneRules.input(number),presentation)
}
internal class PhoneSpeedDial(c:Context){
 private val prefs=c.getSharedPreferences("phone-speed-dial",Context.MODE_PRIVATE)
 fun entries():Map<Int,String> =(2..9).mapNotNull{key->prefs.getString(key.toString(),null)?.takeIf{PhoneRules.callable(it)}?.let{key to it}}.toMap()
 fun set(key:Int,number:String?){require(key in 2..9);require(number==null||PhoneRules.callable(number));prefs.edit().apply{if(number==null)remove(key.toString())else putString(key.toString(),number)}.apply()}
}
internal data class PhoneContactRemoval(val uri:Uri,val name:String,val numbers:List<String>)
internal data class PhoneLine(val handle:PhoneAccountHandle,val name:String)
internal data class PhoneContactEdit(val raw:Long?=null,val name:String="",val numbers:List<String> = listOf(""),val version:Long=0,val account:String="")
internal class PhoneRepository(private val c:Context){
 private val resolver get()=c.contentResolver
 fun events(query:String,missed:Boolean,before:PhoneEvent?):Pair<List<PhoneEvent>,Boolean>{
  check(PhoneAccess.role(c)&&PhoneAccess.permission(c,Manifest.permission.READ_CALL_LOG)){"Complete Phone setup to see recent calls"}
  val args=mutableListOf<String>();val clauses=mutableListOf<String>()
  if(missed){clauses+="type = ?";args+=CallLog.Calls.MISSED_TYPE.toString()}
  if(query.isNotBlank()){clauses+="(number LIKE ? ESCAPE '\\' OR name LIKE ? ESCAPE '\\')";val q=query.replace("\\","\\\\").replace("%","\\%").replace("_","\\_");args+="%$q%";args+="%$q%"}
  if(before!=null){clauses+="(date < ? OR (date = ? AND _id < ?))";args+=before.date.toString();args+=before.date.toString();args+=before.id.toString()}
  val uri=CallLog.Calls.CONTENT_URI.buildUpon().appendQueryParameter("limit","101").build();val list=mutableListOf<PhoneEvent>()
  (resolver.query(uri,arrayOf("_id","number","name","type","date","duration","presentation"),clauses.takeIf{it.isNotEmpty()}?.joinToString(" AND "),args.toTypedArray(),"date DESC, _id DESC")?:error("Call history is unavailable")).use{q->while(q.moveToNext()&&list.size<101)list+=PhoneEvent(q.getLong(0),q.getString(1).orEmpty(),q.getString(2).orEmpty(),q.getInt(3),q.getLong(4),q.getLong(5),q.getInt(6))}
  check(PhoneAccess.role(c)&&PhoneAccess.permission(c,Manifest.permission.READ_CALL_LOG));return list.take(100) to (list.size>100)
 }
 @android.annotation.SuppressLint("MissingPermission")
 fun lines():List<PhoneLine>{if(!PhoneAccess.role(c)||!PhoneAccess.permission(c,Manifest.permission.READ_PHONE_STATE))return emptyList();val t=c.getSystemService(TelecomManager::class.java);return t.callCapablePhoneAccounts.mapNotNull{h->t.getPhoneAccount(h)?.let{PhoneLine(h,it.label?.toString().orEmpty().ifBlank{"Phone account"})}}}
 @android.annotation.SuppressLint("MissingPermission")
 fun place(number:String,line:PhoneAccountHandle?){check(PhoneAccess.role(c)&&PhoneAccess.permission(c,Manifest.permission.CALL_PHONE)){"Complete Phone setup before calling"};require(PhoneRules.callable(number)){"Enter a phone number"};val extras=Bundle();line?.let{check(lines().any{v->v.handle==it}){"That calling account is no longer available"};extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE,it)};c.getSystemService(TelecomManager::class.java).placeCall(Uri.fromParts("tel",number,null),extras)}
 fun favorite(id:Long,value:Boolean){check(PhoneAccess.permission(c,Manifest.permission.WRITE_CONTACTS)){"Allow contact editing in OnKlav setup."};check(resolver.update(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI,id),ContentValues().apply{put(ContactsContract.Contacts.STARRED,if(value)1 else 0)},null,null)>0){"Contact could not be updated"}}
 fun stars():Set<Long>{if(!PhoneAccess.permission(c,Manifest.permission.READ_CONTACTS))return emptySet();val ids=mutableSetOf<Long>();resolver.query(ContactsContract.Contacts.CONTENT_URI,arrayOf("_id"),"starred = 1",null,null)?.use{while(it.moveToNext()&&ids.size<5000)ids+=it.getLong(0)};return ids}
 fun remove(event:PhoneEvent){check(PhoneAccess.role(c)&&PhoneAccess.permission(c,Manifest.permission.WRITE_CALL_LOG));resolver.delete(ContentUris.withAppendedId(CallLog.Calls.CONTENT_URI,event.id),null,null)}
 fun removal(person:ContactRow):PhoneContactRemoval {
  check(PhoneAccess.permission(c,Manifest.permission.READ_CONTACTS)&&PhoneAccess.permission(c,Manifest.permission.WRITE_CONTACTS)){"Allow contact editing in OnKlav setup."}
  val key=resolver.query(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI,person.id),arrayOf(ContactsContract.Contacts.LOOKUP_KEY),null,null,null)?.use{if(it.moveToFirst())it.getString(0)else null}?:error("This contact is no longer available.")
  return PhoneContactRemoval(ContactsContract.Contacts.getLookupUri(person.id,key),person.name,person.numbers)
 }
 fun deleteContact(target:PhoneContactRemoval){
  check(PhoneAccess.permission(c,Manifest.permission.WRITE_CONTACTS)){"Allow contact editing in OnKlav setup."}
  check(resolver.delete(target.uri,null,null)>0){"Contact could not be deleted. It may be read-only or already removed."}
 }
 fun readMissed(){if(PhoneAccess.role(c)&&PhoneAccess.permission(c,Manifest.permission.WRITE_CALL_LOG)&&PhoneAccess.permission(c,Manifest.permission.READ_CALL_LOG)){resolver.update(CallLog.Calls.CONTENT_URI,ContentValues().apply{put("new",0);put("is_read",1)},"type = ? AND new = 1",arrayOf(CallLog.Calls.MISSED_TYPE.toString()));c.getSystemService(android.app.NotificationManager::class.java).cancel(7311)}}
 fun block(number:String){check(PhoneAccess.role(c)){"Choose OnKlav as the default Phone app in setup."};require(PhoneRules.callable(number));resolver.insert(BlockedNumberContract.BlockedNumbers.CONTENT_URI,ContentValues().apply{put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER,number)})?:error("This number could not be blocked")}
 fun edit(person:ContactRow):PhoneContactEdit{
  check(PhoneAccess.permission(c,Manifest.permission.READ_CONTACTS)){"Allow contacts in OnKlav setup."};var raw:Long?=null;var version=0L;var account=""
  resolver.query(ContactsContract.RawContacts.CONTENT_URI,arrayOf("_id","version","account_name"),"contact_id = ? AND deleted = 0 AND raw_contact_is_read_only = 0",arrayOf(person.id.toString()),"_id ASC")?.use{if(it.moveToFirst()){raw=it.getLong(0);version=it.getLong(1);account=it.getString(2).orEmpty()}}
  val id=raw?:error("This contact is read-only. You can add a new local contact instead.");val numbers=mutableListOf<String>();var name=""
  resolver.query(ContactsContract.Data.CONTENT_URI,arrayOf("mimetype","data1"),"raw_contact_id = ?",arrayOf(id.toString()),"_id ASC")?.use{q->while(q.moveToNext()){when(q.getString(0)){ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE->numbers+=q.getString(1).orEmpty();ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE->name=q.getString(1).orEmpty()}}}
  require(numbers.size<=10){"This contact has more than 10 numbers. Use its contact provider to edit it."};return PhoneContactEdit(id,name.ifBlank{person.name},numbers.take(10).ifEmpty{listOf("")},version,account)
 }
 fun save(d:PhoneContactEdit){
  check(PhoneAccess.permission(c,Manifest.permission.WRITE_CONTACTS)){"Allow contact editing in OnKlav setup."};require(d.name.isNotBlank()&&d.name.length<=120){"Enter a contact name"};val numbers=d.numbers.filter{it.isNotBlank()}.map{PhoneRules.input(it)}.distinct();require(numbers.size<=10&&numbers.all{PhoneRules.callable(it)}){"Enter valid phone numbers"}
  val ops=arrayListOf<ContentProviderOperation>();val data=ContactsContract.Data.CONTENT_URI;val nameMime=ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE;val phoneMime=ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
  if(d.raw==null){ops+=ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI).withValue("account_type",null).withValue("account_name",null).build()}
  else {ops+=ContentProviderOperation.newAssertQuery(ContactsContract.RawContacts.CONTENT_URI).withSelection("_id = ? AND deleted = 0 AND raw_contact_is_read_only = 0",arrayOf(d.raw.toString())).withValue("version",d.version).withExpectedCount(1).build()}
  fun row(mime:String,value:String){val op=ContentProviderOperation.newInsert(data).withValue("mimetype",mime).withValue("data1",value);if(d.raw==null)op.withValueBackReference("raw_contact_id",0)else op.withValue("raw_contact_id",d.raw);if(mime==phoneMime)op.withValue("data2",ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE);ops+=op.build()}
  if(d.raw==null){row(nameMime,d.name.trim());numbers.forEach{row(phoneMime,it)}}else{
   val old=mutableListOf<Pair<Long,String>>();var nameExists=false
   resolver.query(data,arrayOf("_id","mimetype","data1"),"raw_contact_id = ?",arrayOf(d.raw.toString()),null)?.use{q->while(q.moveToNext()){if(q.getString(1)==phoneMime)old+=q.getLong(0) to PhoneRules.input(q.getString(2).orEmpty());if(q.getString(1)==nameMime)nameExists=true}}
   if(nameExists)ops+=ContentProviderOperation.newUpdate(data).withSelection("raw_contact_id = ? AND mimetype = ?",arrayOf(d.raw.toString(),nameMime)).withValue("data1",d.name.trim()).build()else row(nameMime,d.name.trim())
   old.filter{it.second !in numbers}.forEach{(id,_)->ops+=ContentProviderOperation.newDelete(data).withSelection("_id = ? AND raw_contact_id = ?",arrayOf(id.toString(),d.raw.toString())).build()}
   numbers.filter{n->old.none{it.second==n}}.forEach{row(phoneMime,it)}
  };try{resolver.applyBatch(ContactsContract.AUTHORITY,ops)}catch(_:OperationApplicationException){error("Contact changed or is read-only. Reopen it and try again.")}
 }
}
internal class PhoneModel(app:Application):AndroidViewModel(app){
 private val repo=PhoneRepository(app);private val shortcuts=PhoneSpeedDial(app);var speedDial by mutableStateOf(shortcuts.entries());private set;var saving by mutableStateOf(false);private set;var contactRemoval by mutableStateOf<PhoneContactRemoval?>(null);var tab by mutableStateOf("Keypad");var number by mutableStateOf("");var query by mutableStateOf("");var missed by mutableStateOf(false);var favorites by mutableStateOf(false)
 var contacts by mutableStateOf<List<ContactRow>>(emptyList());private set;var stars by mutableStateOf<Set<Long>>(emptySet());private set;var events by mutableStateOf<List<PhoneEvent>>(emptyList());private set;var more by mutableStateOf(false);private set;var busy by mutableStateOf(false);private set;var status by mutableStateOf("");var editing by mutableStateOf<PhoneContactEdit?>(null);var lines by mutableStateOf<List<PhoneLine>>(emptyList());private set
 private var refresh:Job?=null;private var action:Job?=null;private val observer=object:ContentObserver(Handler(Looper.getMainLooper())){override fun onChange(self:Boolean){load()}}
 init{runCatching{app.contentResolver.registerContentObserver(CallLog.Calls.CONTENT_URI,true,observer);app.contentResolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI,true,observer)}}
 fun load(older:Boolean=false){if(older&&(busy||!more||events.size>=5000))return;refresh?.cancel();refresh=viewModelScope.launch{if(query.isNotBlank())delay(180);busy=true;try{val result=withContext(Dispatchers.IO){val logs=if(PhoneAccess.role(getApplication())&&PhoneAccess.permission(getApplication(),Manifest.permission.READ_CALL_LOG))repo.events(query,missed,if(older)events.lastOrNull()else null)else emptyList<PhoneEvent>() to false;val people=if(PhoneAccess.permission(getApplication(),Manifest.permission.READ_CONTACTS))loadPhoneContacts(getApplication())else emptyList();Triple(logs,people,repo.stars())};events=if(older)(events+result.first.first).distinctBy{it.id}.take(5000)else result.first.first;more=result.first.second&&events.size<5000;contacts=result.second;stars=result.third;lines=withContext(Dispatchers.IO){repo.lines()};if(tab=="Recents"&&!older)withContext(Dispatchers.IO){repo.readMissed()}}catch(e:CancellationException){throw e}catch(_:SecurityException){events=emptyList();contacts=emptyList();stars=emptySet();status="Phone access changed. Review setup."}catch(_:Exception){status="Could not load Phone data. Try again."}finally{busy=false}}}
 fun search(s:String){query=s.take(100);load()}
 private fun work(block:suspend()->Unit){if(action?.isActive==true){saving=false;return};action=viewModelScope.launch{try{withContext(Dispatchers.IO){block()};load()}catch(_:SecurityException){status="Review Phone permissions in setup."}catch(e:Exception){status=e.message?.take(200)?:"The action could not complete"}finally{saving=false}}}
 fun favorite(p:ContactRow)=work{repo.favorite(p.id,p.id !in stars)}
 fun edit(p:ContactRow)=work{withContext(Dispatchers.Main){status=""};val d=repo.edit(p);withContext(Dispatchers.Main){editing=d}}
 fun newContact(number:String=""){status="";editing=PhoneContactEdit(numbers=listOf(number))}
 fun save(d:PhoneContactEdit){if(saving)return;saving=true;work{try{repo.save(d);withContext(Dispatchers.Main){editing=null;status="Contact saved"}}finally{withContext(Dispatchers.Main){saving=false}}}}
 fun prepareRemoval(p:ContactRow)=work{val target=repo.removal(p);withContext(Dispatchers.Main){contactRemoval=target}}
 fun deleteContact(target:PhoneContactRemoval)=work{repo.deleteContact(target);withContext(Dispatchers.Main){speedDial.filterValues{n->target.numbers.any{PhoneRules.input(it)==n}}.keys.forEach{shortcuts.set(it,null)};speedDial=shortcuts.entries();status="Contact deleted"}}
 fun setSpeedDial(key:Int,number:String?){shortcuts.set(key,number);speedDial=shortcuts.entries()}

 fun readMissed()=work{repo.readMissed()}
 fun remove(e:PhoneEvent)=work{repo.remove(e);withContext(Dispatchers.Main){status="Call removed"}}
 fun block(n:String)=work{repo.block(n);withContext(Dispatchers.Main){status="Number blocked"}}
 private var lastCall=-1500L
 fun call(n:String,line:PhoneAccountHandle?=null){if(SystemClock.elapsedRealtime()-lastCall<1500)return;try{repo.place(n,line);lastCall=SystemClock.elapsedRealtime();status=""}catch(_:SecurityException){status="Complete Phone setup before calling."}catch(e:Exception){status=e.message?.take(160)?:"The call could not start"}}
 override fun onCleared(){getApplication<Application>().contentResolver.unregisterContentObserver(observer);super.onCleared()}
}
