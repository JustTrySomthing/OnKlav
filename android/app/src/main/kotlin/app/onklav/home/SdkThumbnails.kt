package app.onklav.home
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.*
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
/** Provider CDN only; bounded input, decode, work and per-app cache. */
internal object SdkThumbnails {
 private val work=Semaphore(2)
 private val calls=ConcurrentHashMap<String,Call>()
 private val memory=object:LruCache<String,Bitmap>(3*1024*1024) { override fun sizeOf(key:String,value:Bitmap)=value.byteCount }
 private val cacheLock=Any()
 private val client=OkHttpClient.Builder().connectTimeout(4,TimeUnit.SECONDS).readTimeout(4,TimeUnit.SECONDS).callTimeout(8,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).dns(object:Dns {override fun lookup(hostname:String)=SdkFeedService.publicAddresses(hostname)}).build()
 fun url(id:String):String { require(Regex("[A-Za-z0-9_-]{11}").matches(id));return "https://i.ytimg.com/vi/"+id+"/mqdefault.jpg" }
 fun read(input:InputStream,check:()->Unit):ByteArray { val out=ByteArrayOutputStream();val buffer=ByteArray(4096);while(true) { check();val n=input.read(buffer);if(n<0) break;require(out.size()+n<=98304);out.write(buffer,0,n) };return out.toByteArray() }
 fun decode(bytes:ByteArray):Bitmap? { if(bytes.size>98304) return null;val b=BitmapFactory.Options().apply { inJustDecodeBounds=true };BitmapFactory.decodeByteArray(bytes,0,bytes.size,b);if(b.outWidth !in 1..640 || b.outHeight !in 1..480) return null;return BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply { inSampleSize=if(b.outWidth>320) 2 else 1 }) }
 private fun directory(context:Context,app:String)=File(context.cacheDir,"sdk-thumbnails/"+AppSdk.desktopId(app))
 fun trimMemory() { synchronized(cacheLock){memory.evictAll()} }
 fun cancelAll() { calls.values.forEach { it.cancel() };calls.clear() }
 fun cancelApp(app:String) { val prefix=AppSdk.desktopId(app)+":";calls.keys.filter { it.startsWith(prefix) }.forEach { calls.remove(it)?.cancel() } }
 fun clear(context:Context,app:String) { cancelApp(app);synchronized(cacheLock) { val prefix=AppSdk.desktopId(app)+":";memory.snapshot().keys.filter { it.startsWith(prefix) }.forEach { memory.remove(it) };directory(context,app).listFiles()?.forEach { check(!it.exists()||it.delete()) {"Thumbnail cleanup could not finish"} } } }
 private fun trim(dir:File) { val files=dir.listFiles()?.sortedBy { it.lastModified() } ?: return;var total=files.sumOf { it.length() };var count=files.size;for(f in files) {if(total<=2*1024*1024 && count<=40) break;val bytes=f.length();if(f.delete()) { total-=bytes;count-- } } }
 suspend fun load(context:Context,app:String,id:String,authorize:()->Boolean):Bitmap? = withContext(Dispatchers.IO) {
  val target=url(id);val key=AppSdk.desktopId(app)+":"+id
  try { work.withPermit {
   ensureActive();val download=authorize()
   synchronized(cacheLock) { memory.get(key) }?.let { return@withPermit it }
   val dir=directory(context,app);val file=File(dir,id+".jpg")
   var bytes=synchronized(cacheLock) { if(file.isFile && file.length()<=98304) runCatching { file.readBytes().also { file.setLastModified(System.currentTimeMillis()) } }.getOrNull() else null }
   if(bytes==null && download) {
    val call=client.newCall(Request.Builder().url(target).build());calls[key]=call
    try { call.execute().use { response ->if(response.code!=200) return@withPermit null;bytes=read(response.body?.byteStream() ?: return@withPermit null) { ensureActive();check(authorize()) } } } finally { calls.remove(key,call);call.cancel() }
   }
   val data=bytes ?: return@withPermit null;authorize();ensureActive();val bitmap=decode(data) ?: return@withPermit null
   synchronized(cacheLock) { authorize();dir.mkdirs();val temp=File.createTempFile("image-",".tmp",dir);try { temp.writeBytes(data);check(temp.renameTo(file));trim(dir);memory.put(key,bitmap) } finally { temp.delete() } };bitmap
  } } catch(cancel:CancellationException) { throw cancel } catch(_:Exception) { null }
 }
}
