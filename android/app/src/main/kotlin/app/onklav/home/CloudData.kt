package app.onklav.home

import android.content.Context
import android.util.AtomicFile
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.*
import java.io.File
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

internal data class CloudProvider(val name:String,val endpoint:String,val signup:String,val help:String,val instructions:String)
internal val cloudProviders=listOf(
 CloudProvider("pCloud · US","https://webdav.pcloud.com/","https://www.pcloud.com/","https://help.pcloud.com/article/connect-to-pcloud-using-webdav-and-rsync","Use your pCloud email and password. Choose the region where your account is stored. If two-step verification is enabled, approve the new connection in pCloud’s email, then retry. Encrypted Crypto folders are not available here."),
 CloudProvider("pCloud · EU","https://ewebdav.pcloud.com/","https://www.pcloud.com/","https://help.pcloud.com/article/connect-to-pcloud-using-webdav-and-rsync","Use your pCloud email and password. Choose Europe if that is where your account is stored. Approve a new connection in pCloud’s email if asked. Encrypted Crypto folders are not available here."),
 CloudProvider("Koofr","https://app.koofr.net/dav/Koofr/","https://app.koofr.net/signup","https://app.koofr.net/app/admin/preferences/password","1. Open Koofr → Preferences → Password.\n2. Generate an app password named OnKlav.\n3. Return here and use your email and that app password.\n\nFree accounts restrict some file types, including ZIP and APK, and some sharing features.")
)
internal data class CloudAccount(val id:String=UUID.randomUUID().toString(),val name:String,val endpoint:String,val user:String,val password:String){
 override fun toString()="CloudAccount($id, credentials redacted)"
 fun validate(){require(id.matches(Regex("[a-f0-9-]{36}")));require(name.isNotBlank()&&name.length<=80);CloudPaths.base(endpoint);require(user.isNotBlank()&&user.length<=320&&':' !in user&&user.none{it.isISOControl()});require(password.isNotEmpty()&&password.length<=4096&&password.none{it=='\r'||it=='\n'})}
 fun json()=JSONObject().put("id",id).put("name",name).put("endpoint",endpoint).put("user",user).put("password",password)
 companion object{fun parse(j:JSONObject)=CloudAccount(j.getString("id"),j.getString("name"),j.getString("endpoint"),j.getString("user"),j.getString("password")).also{it.validate()}}
}
internal object CloudPaths{
 fun base(raw:String):HttpUrl{require(raw.length<=4096);val u=raw.toHttpUrl();require(u.scheme=="https"&&u.username.isEmpty()&&u.password.isEmpty()&&u.query==null&&u.fragment==null){"Use an HTTPS storage address without embedded credentials or query parameters."};return if(u.encodedPath.endsWith('/'))u else u.newBuilder().addPathSegment("").build()}
 fun name(raw:String):String=raw.trim().also{require(it.isNotBlank()&&it.length<=180&&it !in setOf(".","..")&&it.none{c->c=='/'||c=='\\'||c.isISOControl()}){"Enter a name without slashes or control characters."}}
 fun child(parent:HttpUrl,name:String,folder:Boolean=false)=parent.newBuilder().addPathSegment(name(name)).apply{if(folder)addPathSegment("")}.build()
 fun safe(base:HttpUrl,url:HttpUrl):Boolean=base.scheme==url.scheme&&base.host==url.host&&base.port==url.port&&url.username.isEmpty()&&url.password.isEmpty()&&url.query==null&&url.fragment==null&&url.encodedPath.startsWith(base.encodedPath)&&url.pathSegments.all{it !in setOf(".","..")&&it.none{c->c=='/'||c=='\\'||c.isISOControl()}&&!Regex("(?i)%2e|%2f|%5c|%25").containsMatchIn(it)}
 fun href(base:HttpUrl,parent:HttpUrl,raw:String):HttpUrl?{
  if(raw.length>8192||Regex("(?i)%2f|%5c|%2e").containsMatchIn(raw))return null
  val u=parent.resolve(raw)?:return null
  return u.takeIf{safe(base,it)}
 }
 fun direct(parent:HttpUrl,url:HttpUrl):Boolean{if(!url.encodedPath.startsWith(parent.encodedPath))return false;return url.pathSegments.filter{it.isNotEmpty()}.size==parent.pathSegments.filter{it.isNotEmpty()}.size+1}
}
internal class CloudStore(c:Context,private val keys:SecretKeys=AndroidSecretKeys()){
 companion object {const val MAX_ENVELOPE=1024*1024;private val lock=Any()}
 private val file=File(c.noBackupFilesDir,"cloud-files/accounts.bin");private val alias="onklav.cloud-files.accounts.v1";private val aad="cloud-files/accounts/v1".toByteArray()
 fun load():List<CloudAccount> = synchronized(lock){val a=AtomicFile(file);if(!file.exists()&&!File(file.path+".bak").exists())return@synchronized emptyList();val bytes=a.openRead().use{it.readBytesBounded(MAX_ENVELOPE)};require(bytes.size>=29&&bytes[0]==1.toByte());val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,keys.load(alias,false),GCMParameterSpec(128,bytes.copyOfRange(1,13)));cipher.updateAAD(aad);val clear=cipher.doFinal(bytes.copyOfRange(13,bytes.size));try{val list=JSONObject(String(clear,Charsets.UTF_8)).getJSONArray("accounts");require(list.length()<=20);List(list.length()){CloudAccount.parse(list.getJSONObject(it))}.also{require(it.map{a->a.id}.distinct().size==it.size)}}finally{clear.fill(0)}}
 fun save(accounts:List<CloudAccount>) = synchronized(lock){require(accounts.size<=20){"Use up to 20 cloud accounts."};accounts.forEach{it.validate()};require(accounts.map{it.id}.distinct().size==accounts.size);load();file.parentFile!!.mkdirs();val plain=JSONObject().put("accounts",JSONArray(accounts.map{it.json()})).toString().toByteArray();val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,keys.load(alias,!file.exists()&&!File(file.path+".bak").exists()));cipher.updateAAD(aad);val encoded=try{byteArrayOf(1)+cipher.iv+cipher.doFinal(plain)}finally{plain.fill(0)};require(encoded.size<=MAX_ENVELOPE){"Cloud account settings are too large. Existing accounts were kept."};val a=AtomicFile(file);val out=a.startWrite();try{out.write(encoded);a.finishWrite(out)}catch(e:Exception){a.failWrite(out);throw e}}
 /** Read, merge and save under the same process-wide lock across model instances. */
 fun update(change:(List<CloudAccount>)->List<CloudAccount>):List<CloudAccount> = synchronized(lock){
  val next=change(load());save(next);next
 }

}
internal fun java.io.InputStream.readBytesBounded(limit:Int):ByteArray{val out=java.io.ByteArrayOutputStream();val b=ByteArray(8192);while(true){val n=read(b);if(n<0)break;if(n==0)continue;require(out.size().toLong()+n<=limit){"Response is too large."};out.write(b,0,n)};return out.toByteArray()}

internal fun cloudMime(name:String)=android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.',"").lowercase(java.util.Locale.ROOT))?:"application/octet-stream"
