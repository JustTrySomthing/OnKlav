package app.onklav.home

import android.content.*
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID

/** Private, read-only copied exports with a ten-minute revocable signed-owner lease. */
class SdkHandoffProvider:ContentProvider(){
 override fun onCreate()=true
 private fun checked(uri:Uri)=inspect(requireNotNull(context),uri)
 internal fun inspect(c:Context,uri:Uri,now:Long=System.currentTimeMillis(),ownerCheck:(Context,JSONObject)->Boolean=::eligible)=AppSdk.carrierEgress{val id=uri.pathSegments.singleOrNull()?:throw FileNotFoundException();require(uri.scheme=="content"&&uri.authority==c.packageName+".sdk-handoff"&&id.matches(Regex("[a-f0-9]{32}"))&&uri.query==null)
  val row=prefs(c).getString(id,null)?.let(::JSONObject)?:throw FileNotFoundException()
  if(row.optLong("epoch",-1)!=epochs(c).getLong(row.getJSONObject("owner").getString("id"),0)||row.getLong("expires")<=now||!ownerCheck(c,row.getJSONObject("owner"))){erase(c,id);throw FileNotFoundException("Handoff expired or access changed")}
  val file=file(c,id);if(!file.isFile||file.length()!=row.getLong("size"))throw FileNotFoundException();row to file
 }
 override fun getType(uri:Uri)=checked(uri).first.getString("mime")
 override fun openFile(uri:Uri,mode:String):ParcelFileDescriptor=AppSdk.carrierEgress{if(mode!="r")throw FileNotFoundException("Read-only handoff");ParcelFileDescriptor.open(checked(uri).second,ParcelFileDescriptor.MODE_READ_ONLY)}
 override fun query(uri:Uri,projection:Array<out String>?,selection:String?,selectionArgs:Array<out String>?,sortOrder:String?):Cursor{val(row,file)=checked(uri);val columns=projection?:arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE);return MatrixCursor(columns).apply{addRow(columns.map{when(it){OpenableColumns.DISPLAY_NAME->row.getString("name");OpenableColumns.SIZE->file.length();else->null}})}}
 override fun insert(uri:Uri,values:ContentValues?):Uri=throw UnsupportedOperationException()
 override fun update(uri:Uri,values:ContentValues?,selection:String?,selectionArgs:Array<out String>?):Int=throw UnsupportedOperationException()
 override fun delete(uri:Uri,selection:String?,selectionArgs:Array<out String>?):Int=throw UnsupportedOperationException()
 companion object{
  private const val TTL=600000L
  private fun epochs(c:Context)=c.getSharedPreferences("sdk-handoff-epochs",0)
  private fun prefs(c:Context)=c.getSharedPreferences("sdk-handoffs",0)
  private fun directory(c:Context)=File(c.cacheDir,"sdk-handoffs").also{check(it.mkdirs()||it.isDirectory);check(!java.nio.file.Files.isSymbolicLink(it.toPath()))}
  private fun file(c:Context,id:String):File{require(id.matches(Regex("[a-f0-9]{32}")));val dir=directory(c);return File(dir,id).also{require(!java.nio.file.Files.isSymbolicLink(it.toPath())&&it.canonicalFile.parentFile==dir.canonicalFile)}}
  internal fun eligible(c:Context,owner:JSONObject):Boolean{
   val rows=AppSdk.sync(c,JSONObject().put("action","list")) as JSONArray
   return eligibleRows(rows,owner)
  }
  internal fun eligibleRows(rows:JSONArray,owner:JSONObject):Boolean{
   return (0 until rows.length()).map{rows.getJSONObject(it)}.any{r->val m=r.getJSONObject("manifest");val grants=r.optJSONArray("grants")?:JSONArray();r.optBoolean("installed")&&m.optString("id")==owner.getString("id")&&m.optString("publisher")==owner.getString("publisher")&&r.optString("hash")==owner.getString("hash")&&setOf("apps.handoff","storage.private","files.export").all{cap->(0 until grants.length()).any{grants.optString(it)==cap}}}
  }
  private fun uri(c:Context,id:String)=Uri.Builder().scheme("content").authority(c.packageName+".sdk-handoff").appendPath(id).build()
  private fun erase(c:Context,id:String){c.revokeUriPermission(uri(c,id),Intent.FLAG_GRANT_READ_URI_PERMISSION);val target=file(c,id);check(!target.exists()||target.delete()){"Temporary handoff cleanup must be retried"};check(prefs(c).edit().remove(id).commit())}
  fun discard(c:Context,uri:Uri)=AppSdk.carrierEgress{require(uri.authority==c.packageName+".sdk-handoff");erase(c,uri.pathSegments.single())}
  fun clearApp(c:Context,id:String)=AppSdk.carrierEgress{val e=epochs(c);check(e.edit().putLong(id,Math.addExact(e.getLong(id,0),1)).commit());prefs(c).all.forEach{(key,value)->if(value is String&&runCatching{JSONObject(value).getJSONObject("owner").getString("id")==id}.getOrDefault(false))erase(c,key)}}
  internal fun stage(c:Context,owner:JSONObject,source:JSONObject,authorize:()->Unit,now:Long=System.currentTimeMillis()):Uri=AppSdk.carrierEgress{
   authorize();val name=source.getString("name");val mime=source.getString("mime");val size=source.getLong("size")
   require(name.isNotBlank()&&name.length<=128&&!name.any{it.isISOControl()||it=='/'||it=='\\'}&&SdkHandoffs.mime(mime)&&size in 0..20L*1024*1024)
   require(owner.getString("id").matches(Regex("[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)+"))&&owner.getString("publisher").matches(Regex("[a-f0-9]{64}"))&&owner.getString("hash").matches(Regex("[a-f0-9]{64}")))
   val p=prefs(c);p.all.forEach{(key,value)->if(value is String&&runCatching{JSONObject(value).getLong("expires")<=now}.getOrDefault(true))erase(c,key)}
   directory(c).listFiles()?.filter{it.name.matches(Regex("[a-f0-9]{32}"))&&!p.contains(it.name)}?.forEach{val orphan=file(c,it.name);check(!orphan.exists()||orphan.delete()){"Temporary handoff recovery must be retried"}}
   val rows=p.all.values.filterIsInstance<String>().map(::JSONObject);check(rows.size<16&&rows.count{it.getJSONObject("owner").getString("id")==owner.getString("id")}<4&&rows.sumOf{it.getLong("size")}+size<=40L*1024*1024){"Too many temporary handoffs. Wait for an older handoff to expire."}
   val original=File(source.getString("path"));val expected=File(c.filesDir,"app-sdk/data/${owner.getString("id")}/private-blobs-v1/${source.getString("id")}.data");require(original.absoluteFile==expected.absoluteFile);require(original.isFile&&!java.nio.file.Files.isSymbolicLink(original.toPath())&&original.canonicalFile==original.absoluteFile&&original.length()==size)
   val id=UUID.randomUUID().toString().replace("-","");val target=file(c,id)
   try{original.inputStream().use{input->target.outputStream().use{out->val buffer=ByteArray(65536);var total=0L;while(true){authorize();val n=input.read(buffer);if(n<0)break;total+=n;check(total<=size);out.write(buffer,0,n)};check(total==size);out.fd.sync()}};authorize();val record=JSONObject().put("owner",JSONObject(owner.toString())).put("name",name).put("mime",mime).put("size",size).put("expires",Math.addExact(now,TTL)).put("epoch",epochs(c).getLong(owner.getString("id"),0));check(p.edit().putString(id,record.toString()).commit());uri(c,id)}catch(e:Exception){target.delete();throw e}
  }
 }
}
