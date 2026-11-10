package app.onklav.home

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.compose.runtime.*
import androidx.lifecycle.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File

internal object MailLocks{val operations=Mutex();val drafts=CoroutineScope(SupervisorJob()+Dispatchers.IO)}
internal class MailModel @JvmOverloads constructor(app:Application,overrideDisk:MailDisk?=null,private val allowNetwork:Boolean=true):AndroidViewModel(app){
 val disk=overrideDisk?:MailDisk(app)
 var accounts by mutableStateOf(listOf<MailAccount>());private set
 var headers by mutableStateOf(listOf<MailHeader>());private set
 var folders by mutableStateOf(mapOf<String,List<MailFolder>>());private set
 var rules by mutableStateOf(listOf<MailRule>());private set
 var drafts by mutableStateOf(listOf<MailDraft>());private set
 var account by mutableStateOf("");private set
 var folder by mutableStateOf("INBOX");private set
 var tab by mutableStateOf("Inbox");private set
 var query by mutableStateOf("")
 var unread by mutableStateOf(false)
 var selected by mutableStateOf(setOf<String>())
 var opened by mutableStateOf<MailHeader?>(null);private set
 var body by mutableStateOf<MailBody?>(null);private set
 var draft by mutableStateOf<MailDraft?>(null);private set
 var draftSaving by mutableStateOf(false);private set
 var draftSaved by mutableLongStateOf(0L);private set
 var threaded by mutableStateOf(app.getSharedPreferences("email-ui",0).getBoolean("threaded",true));private set
 fun threading(value:Boolean){threaded=value;getApplication<Application>().getSharedPreferences("email-ui",0).edit().putBoolean("threaded",value).apply();selected=emptySet()}
 var status by mutableStateOf("");private set
 var error by mutableStateOf(false);private set
 var busy by mutableStateOf(false);private set
 var progress by mutableFloatStateOf(-1f);private set
 var accountStatus by mutableStateOf(mapOf<String,String>());private set
 var onlineResults by mutableStateOf<List<MailHeader>?>(null);private set
 var more by mutableStateOf(mapOf<String,Boolean>());private set
 private var job:Job?=null;private var save:Job?=null;private var disposed=false
 private val verify={check(mailAccess(app)&&mailAccess(app,"storage.secrets")){"Email storage access is off. Restore it in Settings > Apps > Email."};Unit}
 private val netVerify={verify();check(allowNetwork){"Offline"};check(mailAccess(app,"network.http")){"Email network access is off. Restore it in Settings > Apps > Email."};Unit}
 private val net=MailProtocol(app,disk,netVerify)
 init{run("Opening email"){load();status=""}}
 private suspend fun load(){withContext(Dispatchers.IO){verify();accounts=disk.accounts();headers=accounts.flatMap{disk.headers(it.id)};folders=accounts.associate{it.id to disk.folders(it.id)};rules=disk.rules();drafts=disk.drafts()}}
 private fun run(label:String,block:suspend()->Unit){if(busy)return;busy=true;error=false;progress=-1f;status=label;job=viewModelScope.launch{try{MailLocks.operations.withLock{verify();block()}}catch(e:CancellationException){status="Cancelled";throw e}catch(e:Exception){message(MailMime.error(e),true)}finally{busy=false;progress=-1f}}}
 fun message(text:String,failed:Boolean=false){status=text;error=failed}
 fun search(value:String){query=value;onlineResults=null;selected=emptySet()}
 fun dismiss(){status="";error=false}
 fun cancel(){job?.cancel()}
 fun choose(id:String,path:String="INBOX"){if(busy)return;account=id;folder=path;tab="Inbox";query="";onlineResults=null;selected=emptySet();opened=null;body=null;if(accounts.isNotEmpty())refresh()}
 fun tab(value:String){if(busy)return;tab=value;selected=emptySet();onlineResults=null}
 fun settingsChanged(){MailSync.schedule(getApplication())}
 fun refresh(older:Boolean=false,serverSearch:Boolean=false){val target=accounts.filter{it.enabled&&(account.isEmpty()||it.id==account)};if(target.isEmpty())return;run(if(older)"Loading earlier email" else"Checking mail"){
  var success=0
  for(a in target){currentCoroutineContext().ensureActive();status="Checking ${a.email}"
   try{val path=if(account.isEmpty())"INBOX" else folder;val searching=serverSearch||onlineResults!=null&&query.isNotBlank();val previous=withContext(Dispatchers.IO){disk.headers(a.id)};if(older&&!searching)require(previous.count{mailSameFolder(it.folder,path)}<5000){"Reached the downloaded email limit. Use Search server to find older messages."};val before=if(older)(if(searching)onlineResults.orEmpty()else previous).filter{it.account==a.id&&mailSameFolder(it.folder,path)}.minOfOrNull{it.uid}else null
    val page=net.page(a,path,before=before,query=if(searching)query.trim()else"");netVerify()
    if(searching){onlineResults=((if(older)onlineResults.orEmpty()else onlineResults.orEmpty().filter{it.account!=a.id})+page.headers).distinctBy{it.key}.sortedByDescending{it.date}}else{
     var fetched=page.headers
     if(path.equals("INBOX",true)&&!older)for(h in page.headers){var row=h;for(rule in rules.filter{it.matches(h)}){when(rule.action){"star"->{if(!row.starred){net.flag(a,row,star=true);row=row.copy(starred=true)}};"read"->{if(!row.seen){net.flag(a,row,seen=true);row=row.copy(seen=true)}};"move"->{if(rule.folder!=path){net.move(a,row,rule.folder);fetched=fetched.filter{it.key!=h.key};break}}};fetched=fetched.map{if(it.key==h.key)row else it}}}
     val merged=mailMergePage(previous,fetched,path,page.validity,older);withContext(Dispatchers.IO){netVerify();disk.saveHeaders(a.id,merged)};headers=headers.filter{it.account!=a.id}+merged
    }
    more=more+(a.id to (page.more&&(!older||page.headers.any{it.uid<(before?:Long.MAX_VALUE)})));accountStatus=accountStatus+(a.id to "Up to date");success++
    if(!older&&!serverSearch){val f=net.folders(a);withContext(Dispatchers.IO){netVerify();disk.saveFolders(a.id,f)};folders=folders+(a.id to f)}
   }catch(e:CancellationException){throw e}catch(e:Exception){accountStatus=accountStatus+(a.id to MailMime.error(e))}
  };if(success==target.size)status=if(serverSearch)"Search complete" else"Up to date" else message("Updated $success of ${target.size} accounts. Open Accounts for details.",true)
 }}
 val visible get()=(onlineResults?:headers).filter{(account.isEmpty()||it.account==account)&&when(tab){"Starred"->it.starred;else->if(account.isEmpty())it.folder.equals("INBOX",true)else mailSameFolder(it.folder,folder)}&&(!unread||!it.seen)&&(query.isBlank()||"${it.from} ${it.to} ${it.subject}".contains(query,true))}.distinctBy{it.key}.sortedByDescending{it.date}
 fun connect(a:MailAccount,password:String,smtpPassword:String="",finished:()->Unit){run("Connecting ${a.email}"){
  a.validate();require(accounts.none{it.id!=a.id&&it.email.equals(a.email,true)}){"This account is already added"};val old=withContext(Dispatchers.IO){disk.read("secret-${a.id}")};var committed=false
  try{withContext(Dispatchers.IO){netVerify();run{require(password.isNotBlank()||old!=null){"Enter your app password"};if(password.isNotBlank())disk.write("secret-${a.id}",JSONObject().put("password",password).put("smtpPassword",smtpPassword))}}
   net.test(a);val f=net.folders(a);withContext(Dispatchers.IO){netVerify();disk.saveAccounts(accounts.filter{it.id!=a.id}+a);disk.saveFolders(a.id,f)};committed=true;accounts=accounts.filter{it.id!=a.id}+a;folders=folders+(a.id to f);accountStatus=accountStatus+(a.id to "Connected");status="Connected";finished();settingsChanged()
  }finally{if(!committed)withContext(NonCancellable+Dispatchers.IO){if(old!=null)disk.write("secret-${a.id}",old)else disk.remove("secret-${a.id}")}}
 }}
 fun remove(a:MailAccount){run("Removing account"){withContext(Dispatchers.IO){verify();disk.saveAccounts(accounts.filter{it.id!=a.id});disk.clearBodies(a.id);disk.remove("secret-${a.id}");disk.remove("headers-${a.id}");disk.remove("folders-${a.id}");val removed=disk.drafts().filter{it.account==a.id};disk.saveDrafts(disk.drafts().filter{it.account!=a.id});removed.flatMap{it.files}.forEach{disk.discardAttachment(it.part)};disk.saveRules(disk.rules().filter{it.account!=a.id})};load();if(account==a.id)choose("");status="Account removed from OnKlav";settingsChanged()}}
 fun updateAccount(a:MailAccount){run("Saving account"){a.validate();val original=accounts.single{it.id==a.id};require(a.copy(name=original.name,signature=original.signature,enabled=original.enabled,notify=original.notify)==original){"Use Save & check connection after changing addresses or server settings"};withContext(Dispatchers.IO){verify();disk.saveAccounts(accounts.map{if(it.id==a.id)a else it})};accounts=accounts.map{if(it.id==a.id)a else it};status="Saved";settingsChanged()}}
 fun open(h:MailHeader){opened=h;body=null;run("Opening message"){val cached=withContext(Dispatchers.IO){disk.read(disk.bodyName(h))?.let(MailBody::parse)};if(cached!=null){body=cached;status=""}else{val a=accounts.single{it.id==h.account};val b=net.body(a,h);withContext(Dispatchers.IO){netVerify();disk.write(disk.bodyName(h),b.json())};body=b;status=""};if(!h.seen)try{net.flag(accounts.single{it.id==h.account},h,seen=true);replaceHeader(h.copy(seen=true));opened=h.copy(seen=true)}catch(e:Exception){if(e is CancellationException)throw e;message("Message opened. Read status will update when you reconnect.")}}}
 fun closeMessage(){if(!busy){opened=null;body=null;status=""}}
 private suspend fun replaceHeader(h:MailHeader){headers=headers.map{if(it.key==h.key)h else it};onlineResults=onlineResults?.map{if(it.key==h.key)h else it};withContext(Dispatchers.IO){netVerify();disk.saveHeaders(h.account,headers.filter{it.account==h.account})}}
 fun star(h:MailHeader){run(if(h.starred)"Removing star" else"Starring message"){net.flag(accounts.single{it.id==h.account},h,star=!h.starred);replaceHeader(h.copy(starred=!h.starred));opened=opened?.let{if(it.key==h.key)h.copy(starred=!h.starred)else it};status=""}}
 fun act(action:String,destination:String="",rows:List<MailHeader> =selectedRows()){if(rows.isEmpty())return;run("Updating ${rows.size} message${if(rows.size==1)"" else"s"}"){var done=0;try{for(h in rows){val a=accounts.single{it.id==h.account};when(action){"read","unread"->{net.flag(a,h,seen=action=="read");replaceHeader(h.copy(seen=action=="read"))};"star","unstar"->{net.flag(a,h,star=action=="star");replaceHeader(h.copy(starred=action=="star"))};"copy"->net.copy(a,h,destination);else->{if(action=="delete")net.delete(a,h)else{val target=if(action=="archive"||action=="trash"||action=="junk")folders[a.id]?.firstOrNull{it.role==action}?.path?:error("Choose or create a destination folder using Move")else destination;net.move(a,h,target)};headers=headers.filter{it.key!=h.key};onlineResults=onlineResults?.filter{it.key!=h.key};withContext(Dispatchers.IO){netVerify();disk.saveHeaders(a.id,headers.filter{it.account==a.id})};if(opened?.key==h.key){opened=null;body=null}}};done++};selected=emptySet();status="Updated $done message${if(done==1)"" else"s"}"}catch(e:Exception){if(e is CancellationException)throw e;message("Updated $done of ${rows.size}. ${MailMime.error(e)}",true)}}}
 fun selectedRows()=if(opened!=null)listOf(opened!!)else (headers+onlineResults.orEmpty()).distinctBy{it.key}.filter{it.key in selected}
 fun folderChange(a:MailAccount,path:String,action:String,newName:String=""){run("Updating folder"){net.folder(a,path,action,newName);val f=net.folders(a);withContext(Dispatchers.IO){netVerify();disk.saveFolders(a.id,f)};folders=folders+(a.id to f);if(folder==path&&action!="create"){folder=if(action=="rename")newName else"INBOX"};status="Folder updated"}}
 fun rule(value:MailRule?,delete:String?=null){run("Saving filters"){value?.validate();val next=if(delete!=null)rules.filter{it.id!=delete}else rules.filter{it.id!=value!!.id}+value!!;require(next.size<=100);withContext(Dispatchers.IO){verify();disk.saveRules(next)};rules=next;status="Filters saved"}}
 fun compose(h:MailHeader?=null,mode:String="new"){if(busy)return;val a=accounts.firstOrNull{it.id==(h?.account?:account)}?:accounts.firstOrNull()?:return;val text=if(h!=null)"\n\n${a.signature}\n\n—— ${h.from} ——\n${body?.text.orEmpty()}" else if(a.signature.isBlank())"" else"\n\n${a.signature}";val to=if(mode=="forward")"" else h?.replyTo?.ifBlank{h.from}.orEmpty();val cc=if(mode=="replyAll"&&h!=null)mailReplyAllCc(h,a.email,to)else"";draft=MailDraft(account=a.id,to=to,cc=cc,subject=h?.subject?.let{if(mode=="forward")"Fwd: $it" else if(it.startsWith("Re:",true))it else"Re: $it"}.orEmpty(),doc=WriteDocument(text=text),replyId=if(mode=="forward")"" else h?.messageId.orEmpty());opened=null;body=null;saveDraft()}
 fun editDraft(d:MailDraft){if(!busy){draft=d;opened=null;body=null}}
 fun changeDraft(d:MailDraft){draft=d.copy(modified=maxOf(System.currentTimeMillis(),(draft?.modified?:0L)+1));saveDraft()}
 private fun saveDraft(){save?.cancel();val snapshot=draft?:return;draftSaving=true;save=viewModelScope.launch{delay(400);try{MailLocks.operations.withLock{persist(snapshot)}}catch(e:CancellationException){throw e}catch(e:Exception){message("Draft not saved. ${MailMime.error(e)}",true)}finally{if(draft?.modified==snapshot.modified)draftSaving=false}}}
 private suspend fun persist(d:MailDraft){withContext(Dispatchers.IO){verify();val existing=disk.drafts();val stored=existing.firstOrNull{it.id==d.id};if(d.phase=="draft"&&stored!=null&&(stored.phase!="draft"||stored.modified>d.modified))return@withContext;val all=existing.filter{it.id!=d.id}+d;require(all.size<=200){"Delete old drafts to make room"};disk.saveDrafts(all)};if(!disposed){drafts=withContext(Dispatchers.IO){disk.drafts()};draftSaved=System.currentTimeMillis()}}
 fun closeDraft(){save?.cancel();val d=draft?:return;run("Saving draft"){persist(d);draft=null;status="Draft saved"}}
 fun deleteDraft(d:MailDraft){save?.cancel();run("Deleting local draft"){withContext(Dispatchers.IO){verify();disk.saveDrafts(disk.drafts().filter{it.id!=d.id});d.files.forEach{disk.discardAttachment(it.part)}};drafts=drafts.filter{it.id!=d.id};if(draft?.id==d.id)draft=null;status="Draft deleted"}}
 fun attach(uris:List<Uri>){val d=draft?:return;save?.cancel();run("Adding attachments"){require(d.files.size+uris.size<=20){"Use up to 20 attachments"};val added=mutableListOf<MailAttachment>();try{withContext(Dispatchers.IO){for(uri in uris){verify();val resolver=getApplication<Application>().contentResolver;val name=resolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())it.getString(0)else null}?.take(240)?:"Attachment";val id=mailId();val total=d.files.sumOf{f->f.size}+added.sumOf{f->f.size};val coroutine=currentCoroutineContext();val count=resolver.openInputStream(uri).use{disk.writeBlob("attach-$id",requireNotNull(it),25_000_000L-total){n->coroutine.ensureActive();verify();status="Adding $name · ${mailSize(n)}"}};added.add(MailAttachment(id,name,resolver.getType(uri)?:"application/octet-stream",count))}};val next=d.copy(files=d.files+added);persist(next);draft=next;status="Attachments added"}catch(e:Exception){withContext(NonCancellable+Dispatchers.IO){added.forEach{disk.remove("attach-${it.part}")}};throw e}}}
 fun removeAttachment(f:MailAttachment){val d=draft?:return;save?.cancel();run("Removing attachment"){val next=d.copy(files=d.files.filter{it.part!=f.part});persist(next);draft=next;withContext(Dispatchers.IO){verify();disk.discardAttachment(f.part)};status=""}}
 fun send(){val d=draft?:return;save?.cancel();run("Sending message"){require(d.phase=="draft"){"Check Sent before creating a new copy"};val a=accounts.single{it.id==d.account};MailMime.addresses(d.to);MailMime.addresses(d.cc);MailMime.addresses(d.bcc);persist(d);val stored=withContext(Dispatchers.IO){disk.drafts().firstOrNull{it.id==d.id}};require(stored?.phase=="draft"&&stored.modified==d.modified){"Draft changed. Reopen it before sending."};try{val result=net.send(a,d,{id->val f=d.files.single{it.part==id};disk.source("attach-$id",f.type,f.name)}){value->val updated=d.copy(phase=value);disk.saveDrafts(disk.drafts().filter{it.id!=d.id}+updated)};draft=null;drafts=withContext(Dispatchers.IO){disk.drafts()};status=result}catch(e:Exception){drafts=withContext(NonCancellable+Dispatchers.IO){disk.drafts()};draft=drafts.firstOrNull{it.id==d.id};throw e}}}

 fun saveAttachment(h:MailHeader,f:MailAttachment?,uri:Uri){run(if(f==null)"Saving original email" else"Downloading ${f.name}"){check(mailAccess(getApplication(),"files.export")){"File export access is off"};val a=accounts.single{it.id==h.account};writeFiledExport(getApplication(),uri){out->if(f==null)net.original(a,h,out)else net.download(a,h,f,out){n,total->progress=if(total>0)(n.toFloat()/total).coerceIn(0f,1f)else-1f;status="Downloading · ${n/1024} KB"}};status="Download complete"}}
 fun clearCache(){run("Clearing downloaded mail"){withContext(Dispatchers.IO){verify();disk.clearCache()};headers=emptyList();body=null;status="Downloaded mail cleared. Accounts and drafts kept."}}
 fun flush():Job?{save?.cancel();val d=draft?:return null;return MailLocks.drafts.launch{try{MailLocks.operations.withLock{persist(d)}}catch(e:CancellationException){throw e}catch(e:Exception){if(!disposed)message("Draft not saved. ${MailMime.error(e)}",true)}}}
 override fun onCleared(){flush();disposed=true;job?.cancel();super.onCleared()}
}
