package app.onklav.home

import android.content.Context
import android.util.AtomicFile
import org.json.*
import java.io.File
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

internal const val MAIL_APP="app.onklav.email"
internal fun mailSameFolder(a:String,b:String)=a==b||a.equals("INBOX",true)&&b.equals("INBOX",true)
internal fun mailId()=UUID.randomUUID().toString()
internal fun mailAccess(c:Context,cap:String="storage.private")=runCatching{val rows=AppSdk.sync(c,JSONObject().put("action","list")) as JSONArray;(0 until rows.length()).any{val r=rows.getJSONObject(it);r.optBoolean("installed")&&r.optJSONObject("manifest")?.optString("id")==MAIL_APP&&r.optJSONArray("grants")?.let{a->(0 until a.length()).any{a.optString(it)==cap}}==true}}.getOrDefault(false)
internal data class MailAccount(val id:String=mailId(),val provider:String="Other",val email:String="",val name:String="",val imap:String="",val imapPort:Int=993,val smtp:String="",val smtpPort:Int=465,val smtpTls:Boolean=false,val user:String=email,val smtpUser:String=user,val signature:String="",val enabled:Boolean=true,val notify:Boolean=true){
 fun json()=JSONObject().put("id",id).put("provider",provider).put("email",email).put("name",name).put("imap",imap).put("imapPort",imapPort).put("smtp",smtp).put("smtpPort",smtpPort).put("smtpTls",smtpTls).put("user",user).put("smtpUser",smtpUser).put("signature",signature).put("enabled",enabled).put("notify",notify)
 fun validate(){require(id.matches(Regex("[a-f0-9-]{36}")));MailMime.addresses(email).also{require(it.size==1)};require(name.length<=120&&name.none{it=='\r'||it=='\n'});require(provider in listOf("GMX","Gmail","Yahoo","Other"));for(h in listOf(imap,smtp))require(h.length in 1..253&&h.matches(Regex("[A-Za-z0-9][A-Za-z0-9.-]*[A-Za-z0-9]|[A-Za-z0-9]"))){"Enter a mail server hostname"};require(imapPort in 1..65535&&smtpPort in 1..65535);require(user.isNotBlank()&&smtpUser.isNotBlank()&&listOf(user,smtpUser).all{it.length<=320&&it.none{c->c=='\r'||c=='\n'}});require(signature.length<=4000)}
 fun withAddress(value:String):MailAccount {val old=preset(provider,email);val next=preset(provider,value);return copy(email=value,user=if(user==old.user||user==email)next.user else user,smtpUser=if(smtpUser==old.smtpUser||smtpUser==email)next.smtpUser else smtpUser,imap=if(imap==old.imap)next.imap else imap,smtp=if(smtp==old.smtp)next.smtp else smtp)}
 companion object{
  fun parse(j:JSONObject)=MailAccount(j.getString("id"),j.getString("provider"),j.getString("email"),j.optString("name"),j.getString("imap"),j.optInt("imapPort",993),j.getString("smtp"),j.optInt("smtpPort",465),j.optBoolean("smtpTls"),j.optString("user",j.getString("email")),j.optString("smtpUser",j.optString("user",j.getString("email"))),j.optString("signature"),j.optBoolean("enabled",true),j.optBoolean("notify",true)).also{it.validate()}
  fun preset(provider: String, email: String = ""): MailAccount {
   val (imap, smtp) = when (provider) {
    "GMX" -> {
     val domain = email.substringAfterLast('@').lowercase()
     val server = if (domain in setOf("gmx.de", "gmx.net")) "gmx.net" else "gmx.com"
     "imap.$server" to "mail.$server"
    }
    "Gmail" -> "imap.gmail.com" to "smtp.gmail.com"
    "Yahoo" -> "imap.mail.yahoo.com" to "smtp.mail.yahoo.com"
    "Other" -> "" to ""
    else -> throw IllegalArgumentException("Unsupported email provider")
   }
   return MailAccount(provider = provider, email = email, imap = imap, smtp = smtp)
  }
 }
}
internal fun updatedMailCredentials(provider: String, saved: JSONObject?, incoming: String, outgoing: String): JSONObject? {
 if (incoming.isBlank() && outgoing.isBlank() && saved != null) return null
 require(incoming.isNotBlank() || saved != null) {
  if (provider in setOf("Gmail", "Yahoo")) "Enter your app password" else "Enter your mail password"
 }
 fun password(value: String): String {
  if (provider != "Gmail") return value
  val compact = value.filterNot(Char::isWhitespace)
  return if (compact.matches(Regex("[A-Za-z0-9]{16}"))) compact else value
 }
 return JSONObject()
  .put("password", if (incoming.isBlank()) saved!!.getString("password") else password(incoming))
  .put("smtpPassword", password(outgoing))
}
internal data class MailFolder(val path:String,val name:String,val role:String="",val selectable:Boolean=true){fun json()=JSONObject().put("path",path).put("name",name).put("role",role).put("selectable",selectable);companion object{fun parse(j:JSONObject)=MailFolder(j.getString("path"),j.getString("name"),j.optString("role"),j.optBoolean("selectable",true))}}
internal data class MailHeader(val account:String,val folder:String,val validity:Long,val uid:Long,val from:String,val to:String,val subject:String,val date:Long,val seen:Boolean,val starred:Boolean,val attachment:Boolean=false,val messageId:String="",val replyTo:String="",val cc:String="",val references:List<String> = emptyList()){
 val key get()="$account:$folder:$validity:$uid"
 fun json()=JSONObject().put("account",account).put("folder",folder).put("validity",validity).put("uid",uid).put("from",from).put("to",to).put("subject",subject).put("date",date).put("seen",seen).put("starred",starred).put("attachment",attachment).put("messageId",messageId).put("replyTo",replyTo).put("cc",cc).put("references",JSONArray(references.take(20)))
 companion object{fun parse(j:JSONObject)=MailHeader(j.getString("account"),j.getString("folder"),j.getLong("validity"),j.getLong("uid"),j.getString("from"),j.getString("to"),j.getString("subject"),j.getLong("date"),j.getBoolean("seen"),j.getBoolean("starred"),j.optBoolean("attachment"),j.optString("messageId"),j.optString("replyTo"),j.optString("cc"),j.optJSONArray("references")?.let{a->require(a.length()<=20);List(a.length()){a.getString(it)}.also{require(it.all{v->mailMessageIds(v)==listOf(v)})}}?:emptyList())}
}
internal data class MailAttachment(val part:String,val name:String,val type:String,val size:Long){fun json()=JSONObject().put("part",part).put("name",name).put("type",type).put("size",size);companion object{fun parse(j:JSONObject)=MailAttachment(j.getString("part"),j.getString("name"),j.getString("type"),j.getLong("size"))}}
internal data class MailBody(val text:String="",val html:String="",val attachments:List<MailAttachment> = emptyList()){fun json()=JSONObject().put("text",text).put("html",html).put("attachments",JSONArray(attachments.map{it.json()}));companion object{fun parse(j:JSONObject)=MailBody(j.optString("text"),j.optString("html"),j.optJSONArray("attachments")?.let{a->List(a.length()){MailAttachment.parse(a.getJSONObject(it))}}?:emptyList())}}
internal data class MailRule(val id:String=mailId(),val name:String="",val account:String="",val field:String="from",val contains:String="",val action:String="star",val folder:String="",val enabled:Boolean=true){
 fun matches(h:MailHeader)=enabled&&(account.isEmpty()||account==h.account)&&when(field){"from"->h.from;"to"->h.to;else->h.subject}.contains(contains,true)
 fun validate(){require(name.isNotBlank()&&name.length<=100&&contains.isNotBlank()&&contains.length<=300);require(field in listOf("from","to","subject")&&action in listOf("star","read","move"));require(action!="move"||account.isNotBlank()&&folder.isNotBlank()){ "Choose an account and destination folder" }}
 fun json()=JSONObject().put("id",id).put("name",name).put("account",account).put("field",field).put("contains",contains).put("action",action).put("folder",folder).put("enabled",enabled)
 companion object{fun parse(j:JSONObject)=MailRule(j.getString("id"),j.getString("name"),j.optString("account"),j.getString("field"),j.getString("contains"),j.getString("action"),j.optString("folder"),j.optBoolean("enabled",true)).also{it.validate()}}
}
internal data class MailDraft(val id:String=mailId(),val account:String="",val to:String="",val cc:String="",val bcc:String="",val subject:String="",val doc:WriteDocument=WriteDocument(),val files:List<MailAttachment> = emptyList(),val replyId:String="",val phase:String="draft",val modified:Long=System.currentTimeMillis()){
 fun json()=JSONObject().put("id",id).put("account",account).put("to",to).put("cc",cc).put("bcc",bcc).put("subject",subject).put("doc",doc.json()).put("files",JSONArray(files.map{it.json()})).put("replyId",replyId).put("phase",phase).put("modified",modified)
 companion object{fun parse(j:JSONObject)=MailDraft(j.getString("id"),j.getString("account"),j.optString("to"),j.optString("cc"),j.optString("bcc"),j.optString("subject"),WriteDocument.parse(j.getJSONObject("doc")),j.optJSONArray("files")?.let{a->List(a.length()){MailAttachment.parse(a.getJSONObject(it))}}?:emptyList(),j.optString("replyId"),j.optString("phase","draft"),j.optLong("modified"))}
}
/** Account metadata, credentials, messages and drafts are encrypted at rest with a device-bound key. */
internal class MailDisk(private val c:Context,private val keys:SecretKeys=AndroidSecretKeys()){
 private val root get() = mailDirectory(c)
 private val alias="onklav.email.data.v1"
 private fun path(name:String):File{require(name.matches(Regex("[A-Za-z0-9._-]{1,160}")));return File(root,name)}
 @Synchronized fun read(name:String):JSONObject?{val f=path(name);val a=AtomicFile(f);if(!f.exists()&&!File(f.path+".bak").exists())return null;val b=a.openRead().use{MailMime.readBounded(it,16_000_000)};require(b.size>=29&&b[0]==1.toByte());val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,keys.load(alias,false),GCMParameterSpec(128,b.copyOfRange(1,13)));cipher.updateAAD(name.toByteArray());val plain=cipher.doFinal(b.copyOfRange(13,b.size));return try{JSONObject(String(plain,Charsets.UTF_8))}finally{plain.fill(0)}}
 @Synchronized fun write(name:String,j:JSONObject){val f=path(name);if(f.exists()||File(f.path+".bak").exists())read(name);root.mkdirs();val plain=j.toString().toByteArray();require(plain.size<=15_999_900){"Mailbox cache is full. Clear downloaded mail in Settings."};val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,keys.load(alias,root.listFiles()?.none{it.isFile&&!it.name.endsWith(".new")}!=false));cipher.updateAAD(name.toByteArray());val bytes=try{byteArrayOf(1)+cipher.iv+cipher.doFinal(plain)}finally{plain.fill(0)};val a=AtomicFile(f);val stream=a.startWrite();try{stream.write(bytes);a.finishWrite(stream)}catch(e:Exception){a.failWrite(stream);throw e}}
 @Synchronized fun remove(name:String)=AtomicFile(path(name)).delete()
 fun writeBlob(name:String,input:java.io.InputStream,limit:Long,progress:(Long)->Unit):Long{val f=path(name);root.mkdirs();val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,keys.load(alias,root.listFiles()?.none{it.isFile&&!it.name.endsWith(".new")}!=false));cipher.updateAAD(name.toByteArray());val a=AtomicFile(f);val out=a.startWrite();try{out.write(byteArrayOf(1)+cipher.iv);val buffer=ByteArray(65536);var total=0L;while(true){val n=input.read(buffer);if(n<0)break;total+=n;require(total<=limit){"Attachments exceed 25 MB. Remove one or share a link."};check(root.usableSpace>16_000_000){"Not enough storage for this attachment"};progress(total);out.write(cipher.update(buffer,0,n))};out.write(cipher.doFinal());a.finishWrite(out);return total}catch(e:Exception){a.failWrite(out);throw e}}
 fun source(name:String,type:String,fileName:String)=object:javax.activation.DataSource{
  override fun getInputStream():java.io.InputStream{val input=AtomicFile(path(name)).openRead();try{val header=ByteArray(13);var count=0;while(count<13){val n=input.read(header,count,13-count);require(n>0);count+=n};require(header[0]==1.toByte());val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,keys.load(alias,false),GCMParameterSpec(128,header.copyOfRange(1,13)));cipher.updateAAD(name.toByteArray());return javax.crypto.CipherInputStream(input,cipher)}catch(e:Exception){input.close();throw e}}
  override fun getOutputStream():java.io.OutputStream=throw java.io.IOException("Read only")
  override fun getContentType()=type
  override fun getName()=fileName
 }
 fun discardAttachment(id:String){if(drafts().none{d->d.files.any{it.part==id}})remove("attach-$id")}
 fun clearBodies(account:String){root.listFiles()?.filter{it.name.startsWith("body-$account-")}?.forEach{check(!it.exists()||it.delete())}}
 fun accounts()=read("accounts")?.optJSONArray("items")?.let{a->List(a.length()){MailAccount.parse(a.getJSONObject(it))}}?:emptyList()
 fun saveAccounts(items:List<MailAccount>)=synchronized(accountLock){items.forEach{it.validate()};require(items.size<=20){"Use up to 20 email accounts"};require(items.map{it.email.lowercase()}.distinct().size==items.size){"This account is already added"};write("accounts",JSONObject().put("items",JSONArray(items.map{it.json()})))}
 fun headers(a:String)=read("headers-$a")?.optJSONArray("items")?.let{j->List(j.length()){MailHeader.parse(j.getJSONObject(it))}}?:emptyList()
 fun saveHeaders(a:String,h:List<MailHeader>)=write("headers-$a",JSONObject().put("items",JSONArray(h.take(5000).map{it.json()})))
 fun folders(a:String)=read("folders-$a")?.optJSONArray("items")?.let{j->List(j.length()){MailFolder.parse(j.getJSONObject(it))}}?:emptyList()
 fun saveFolders(a:String,f:List<MailFolder>)=write("folders-$a",JSONObject().put("items",JSONArray(f.map{it.json()})))
 fun drafts()=read("drafts")?.optJSONArray("items")?.let{j->List(j.length()){MailDraft.parse(j.getJSONObject(it))}}?:emptyList()
 fun saveDrafts(d:List<MailDraft>)=write("drafts",JSONObject().put("items",JSONArray(d.map{it.json()})))
 fun rules()=read("rules")?.optJSONArray("items")?.let{j->List(j.length()){MailRule.parse(j.getJSONObject(it))}}?:emptyList()
 fun saveRules(d:List<MailRule>)=write("rules",JSONObject().put("items",JSONArray(d.map{it.json()})))
 fun bodyName(h:MailHeader)="body-${h.account}-"+java.security.MessageDigest.getInstance("SHA-256").digest(h.key.toByteArray()).joinToString(""){"%02x".format(it)}
 fun clearCache(){root.listFiles()?.filter{it.name.startsWith("body-")||it.name.startsWith("headers-")||it.name.startsWith("folders-")}?.forEach{it.delete()}}
 companion object {
  private val accountLock = Any()

  private fun mailDirectory(context: Context): File {
   // Android can give the app's private storage path through an alias.
   val directory = File(context.noBackupFilesDir.canonicalFile, "email")
   check(directory.canonicalFile == directory) { "OnKlav couldn’t access its private email storage." }
   return directory
  }

  fun clear(c: Context) {
   val directory = mailDirectory(c)
   check(!directory.exists() || directory.deleteRecursively())
   AndroidSecretKeys().deletePrefix("onklav.email.")
   c.getSharedPreferences("email-sync", 0).edit().clear().commit()
  }
 }
}

/** Reply All uses visible To/Cc only; Bcc is deliberately never retained in a header. */
internal fun mailReplyAllCc(h:MailHeader,own:String,to:String):String {
 val primary=MailMime.addresses(to).map{it.address.lowercase(java.util.Locale.ROOT)}.toSet()+own.lowercase(java.util.Locale.ROOT)
 return (MailMime.addresses(h.to)+MailMime.addresses(h.cc)).map{it.address}.filterNot{it.lowercase(java.util.Locale.ROOT) in primary}.distinctBy{it.lowercase(java.util.Locale.ROOT)}.joinToString(", ")
}
