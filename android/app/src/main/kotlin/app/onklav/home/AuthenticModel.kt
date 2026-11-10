package app.onklav.home

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream

internal class AuthenticModel(app:Application):AndroidViewModel(app){
 private val store=AuthenticStore(app)
 var exists by mutableStateOf(store.exists());private set
 var recoveryChoices by mutableStateOf(runCatching{store.recoveryQuestions()}.getOrNull());private set
 var enrolling by mutableStateOf(false);private set
 private var enrollmentRows=emptyList<AuthenticAccount>()
 var unlocked by mutableStateOf(false);private set
 var accounts by mutableStateOf(emptyList<AuthenticAccount>());private set
 var pending by mutableStateOf(emptyList<AuthenticAccount>());private set
 var busy by mutableStateOf(false);private set
 var status by mutableStateOf("");private set
 var epoch by mutableIntStateOf(0);private set
 var retryAt by mutableLongStateOf(0L);private set
 private var failures=0;private var job:Job?=null
 private val io=Dispatchers.IO.limitedParallelism(1)
 private var parts=linkedMapOf<Int,AuthenticMigration.Part>()
 var batchStatus by mutableStateOf("");private set
 private var backup:ByteArray?=null
 var hasBackup by mutableStateOf(false);private set
 private var generation=0
 private fun task(block:suspend(Int)->Unit){if(busy)return;val g=generation;busy=true;job=viewModelScope.launch{try{block(g)}catch(e:CancellationException){throw e}catch(e:Exception){status=e.message?.take(180)?:"Unable to complete the action."}finally{exists=store.exists();busy=false}}}
 fun unlock(password:CharArray,create:Boolean=false){if(SystemClock.elapsedRealtime()<retryAt){password.fill('\u0000');return};task{g->
  try{val rows=withContext(io){try{if(create)store.create(password)else store.unlock(password)}finally{password.fill('\u0000')}};if(g==generation){recoveryChoices=store.recoveryQuestions();failures=0;retryAt=0;if(recoveryChoices==null){enrollmentRows=rows;enrolling=true;accounts=emptyList();unlocked=false;status="Finish recovery setup to open Authentic"}else{accounts=rows;unlocked=true;status=""}}}
  catch(e:Exception){if(e is CancellationException)throw e;failures++;retryAt=SystemClock.elapsedRealtime()+(1000L shl failures.coerceAtMost(5));status="Could not unlock. Check your password. Encrypted data was kept."}
 }}
 fun lock(){enrollmentRows=emptyList();enrolling=false;generation++;job?.cancel();store.close();accounts=emptyList();pending=emptyList();parts.clear();batchStatus="";backup?.fill(0);backup=null;hasBackup=false;unlocked=false;epoch++;status=""}
 fun completeSetup(password:CharArray?,choices:List<Int>,answers:List<CharArray>){
  if(busy||exists&&!enrolling){password?.fill('\u0000');answers.forEach{it.fill('\u0000')};return}
  val original=enrollmentRows
  task{g->try{withContext(io){if(password!=null)store.createWithRecovery(password,choices,answers)else store.enrollRecovery(choices,answers,original)};if(g==generation){recoveryChoices=choices.toList();lock();status="Setup complete · enter your password"}}finally{password?.fill('\u0000');answers.forEach{it.fill('\u0000')}}}
 }
 fun resetPassword(answers:List<CharArray>,replacement:CharArray){
  if(busy||unlocked||SystemClock.elapsedRealtime()<retryAt){answers.forEach{it.fill('\u0000')};replacement.fill('\u0000');return}
  task{g->try{withContext(io){store.resetPassword(answers,replacement)};if(g==generation){failures=0;retryAt=0;lock();status="Password reset · enter your new password"}}catch(e:Exception){if(e is CancellationException)throw e;failures++;retryAt=SystemClock.elapsedRealtime()+(1000L shl failures.coerceAtMost(5));status="Could not reset. Check all three answers. Your accounts were kept."}finally{answers.forEach{it.fill('\u0000')};replacement.fill('\u0000')}}
 }
 fun reset(){if(unlocked||busy)return;store.erase();recoveryChoices=null;exists=false;failures=0;retryAt=0;lock()}
 fun dismiss(){status=""}
 fun discardImport(){pending=emptyList();parts.clear();batchStatus=""}
 fun accept(input:String){if(!unlocked)return;runCatching{
  if(input.trim().startsWith("otpauth-migration:")){
   val p=AuthenticMigration.parse(input);val first=parts.values.firstOrNull();require(first==null||first.batch==p.batch&&first.size==p.size){"This QR belongs to another transfer. Cancel this import to start again."}
   require(parts.values.sumOf{it.accounts.size}+p.accounts.size<=1000){"Transfer is too large."};parts[p.index]=p;batchStatus="${parts.size} of ${p.size} transfer QR codes scanned"
   if(parts.size==p.size){val ready=AuthenticOtp.merge(emptyList(),parts.toSortedMap().values.flatMap{it.accounts});AuthenticStore.validate(ready);pending=ready}
  }else{pending=listOf(AuthenticOtp.parse(input));batchStatus=""}
 }.onFailure{status=it.message?:"This QR is not an authenticator setup."}}
 fun stage(a:AuthenticAccount){if(!unlocked)return;runCatching{a.validate();pending=listOf(a);batchStatus=""}.onFailure{status=it.message?:"Check account settings."}}
 fun importPending(){val incoming=pending;if(incoming.isEmpty()||!unlocked)return;task{g->val original=accounts;val next=withContext(io){AuthenticOtp.merge(original,incoming).also{store.save(it)}};if(g==generation){val added=next.size-accounts.size;accounts=next;discardImport();status="$added accounts added · duplicates kept once"}}}
 private fun update(transform:(List<AuthenticAccount>)->List<AuthenticAccount>){if(!unlocked)return;task{g->val next=transform(accounts);withContext(io){AuthenticStore.validate(next);store.save(next)};if(g==generation){accounts=next;status="Saved"}}}
 fun labels(id:String,issuer:String,account:String)=update{rows->rows.map{if(it.id==id)it.copy(issuer=issuer.trim(),account=account.trim()).also{a->a.validate()}else it}}
 fun pin(id:String)=update{rows->rows.map{if(it.id==id)it.copy(pinned=!it.pinned)else it}}
 fun move(id:String,delta:Int)=update{rows->val a=rows.first{it.id==id};val group=rows.filter{it.pinned==a.pinned};val index=group.indexOf(a);val other=group.getOrNull(index+delta)?:return@update rows;rows.toMutableList().also{val first=rows.indexOf(a);val second=rows.indexOf(other);it[first]=other;it[second]=a}}
 fun delete(id:String)=update{it.filter{a->a.id!=id}}
 fun advance(id:String)=update{rows->rows.map{if(it.id==id){check(it.type=="hotp"&&it.counter<Long.MAX_VALUE-1){"Counter limit reached."};it.copy(counter=it.counter+1)}else it}}
 fun verifyQr(id:String,password:CharArray,ready:(String)->Unit){if(!unlocked){password.fill('\u0000');return};task{g->withContext(io){try{store.verify(password)}finally{password.fill('\u0000')}};if(g==generation)ready(id)}}
 fun changePassword(old:CharArray,next:CharArray,success:()->Unit={}){if(!unlocked){old.fill('\u0000');next.fill('\u0000');return};task{g->withContext(io){try{store.changePassword(old,next,accounts)}finally{old.fill('\u0000');next.fill('\u0000')}};if(g==generation){status="Password changed. Older backups still use their original password.";success()}}}
 fun export(uri:Uri){if(!unlocked){status="Unlock Authentic and choose Encrypted backup again. No backup was saved.";return};task{g->val encrypted=withContext(io){store.bytes()};try{writeFiledExport(getApplication(),uri){it.write(encrypted)};if(g==generation)status="Encrypted backup saved. Keep its password separately."}finally{encrypted.fill(0)}}}
 fun openBackup(uri:Uri){if(exists&&!unlocked){status="Unlock Authentic before restoring a backup to merge accounts.";return};task{g->val bytes=withContext(io){getApplication<Application>().contentResolver.openInputStream(uri).use{input->requireNotNull(input);val out=ByteArrayOutputStream();FilesBroker.copyBoundedLimit(input,out,(AuthenticStore.LIMIT+20000).toLong()){};out.toByteArray()}};if(g==generation){backup?.fill(0);backup=bytes;hasBackup=true}else bytes.fill(0)}}
 fun cancelBackup(){backup?.fill(0);backup=null;hasBackup=false}
 fun restore(password:CharArray){val bytes=backup?:return;task{g->try{if(!exists){val rows=withContext(io){store.restoreNew(bytes,password)};if(g==generation){recoveryChoices=store.recoveryQuestions();if(recoveryChoices==null){enrollmentRows=rows;enrolling=true;accounts=emptyList();unlocked=false;status="Finish recovery setup"}else{accounts=rows;unlocked=true;status="Backup restored"}}}else{val rows=withContext(io){store.inspectBackup(bytes,password)};if(g==generation){pending=rows;status="Review accounts to merge. Existing accounts are preserved."}};cancelBackup()}catch(e:Exception){if(e is CancellationException)throw e;status="Backup could not be opened. Check its password and try again."}finally{password.fill('\u0000')}}}
 fun message(value:String){status=value}
 override fun onCleared(){lock();super.onCleared()}
}
