package app.onklav.home

import android.content.*
import android.graphics.*
import android.os.*
import android.os.Message as OsMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap

internal data class PdfReply(val data:Bundle,val bitmap:Bitmap?=null)
internal object SdkPdfClient {
 private val queue=Mutex()
 private val jobs=ConcurrentHashMap<String,MutableSet<Job>>()
 fun cancelApp(id:String){jobs.remove(id)?.forEach{it.cancel()}}
 internal fun source(c:Context,description:JSONObject):File{
  val owner=description.getJSONObject("owner");val file=description.getJSONObject("file");val id=owner.getString("id");require(id.matches(Regex("[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)+")))
  val handle=file.getString("id");require(handle.matches(Regex("[a-f0-9]{32}")))
  val expected=File(c.filesDir,"app-sdk/data/$id/private-blobs-v1/$handle.data");val actual=File(file.getString("path"))
  require(actual.absoluteFile==expected.absoluteFile&&actual.canonicalFile==actual.absoluteFile&&!java.nio.file.Files.isSymbolicLink(actual.toPath())&&actual.isFile&&actual.length()==file.getLong("size")&&actual.length() in 1..SdkPdfProtocol.MAX_FILE)
  val header=ByteArray(5);actual.inputStream().use{require(it.read(header)==5&&header.contentEquals("%PDF-".toByteArray())){"This private file is not a PDF"}}
  return actual
 }
 suspend fun execute(c:Context,description:JSONObject,a:Bundle,authorize:()->Unit):PdfReply=withTimeout(15000){
  val owner=description.getJSONObject("owner").getString("id");val job=currentCoroutineContext()[Job]!!;val set=jobs.computeIfAbsent(owner){ConcurrentHashMap.newKeySet()};set.add(job)
  try{queue.withLock{authorize();val file=source(c,description);request(c,file,a,authorize)}}finally{set.remove(job)}
 }
 /** Read-only host selection uses the same isolated renderer and limits as SDK PDFs. */
 suspend fun selected(c:Context,file:File,a:Bundle,authorize:()->Unit):PdfReply=withTimeout(15000){queue.withLock{authorize();require(file.length() in 1..SdkPdfProtocol.MAX_FILE);request(c,file,a,authorize)}}
 private suspend fun request(c:Context,file:File,a:Bundle,authorize:()->Unit):PdfReply=coroutineScope{
  val connected=CompletableDeferred<Messenger>();val response=CompletableDeferred<Bundle>()
  val reply=Messenger(object:Handler(Looper.getMainLooper()){override fun handleMessage(msg:OsMessage){response.complete(msg.data)}})
  val connection=object:ServiceConnection{
   override fun onServiceConnected(name:ComponentName,binder:IBinder){connected.complete(Messenger(binder))}
   override fun onServiceDisconnected(name:ComponentName){val e=IllegalStateException("PDF worker stopped. Try the request again.");connected.completeExceptionally(e);response.completeExceptionally(e)}
   override fun onBindingDied(name:ComponentName){onServiceDisconnected(name)}
   override fun onNullBinding(name:ComponentName){onServiceDisconnected(name)}
  }
  var bound=false;var source:ParcelFileDescriptor?=null;var pipe:Array<ParcelFileDescriptor>?=null
  try{
   bound=c.bindService(Intent(c,SdkPdfService::class.java),connection,Context.BIND_AUTO_CREATE);check(bound){"PDF worker is unavailable"};val remote=connected.await();authorize()
   source=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY)
   val request=Bundle(a);request.putParcelable("source",source)
   val bytes=if(a.getString("op")=="render"){
    pipe=ParcelFileDescriptor.createPipe();request.putParcelable("output",pipe!![1]);val read=pipe!![0]
    async(Dispatchers.IO){ParcelFileDescriptor.AutoCloseInputStream(read).use{input->val out=ByteArrayOutputStream();val buffer=ByteArray(65536);while(true){ensureActive();val n=input.read(buffer);if(n<0)break;check(out.size()+n<=SdkPdfProtocol.MAX_PNG);out.write(buffer,0,n)};out.toByteArray()}}
   }else null
   AppSdk.carrierEgress{authorize();remote.send(OsMessage.obtain(null,1).apply{data=request;replyTo=reply})};source?.close();source=null;pipe?.get(1)?.close()
   val result=response.await();check(result.getBoolean("ok")){result.getString("error")?:"PDF request failed"};val png=bytes?.await();authorize()
   val bitmap=if(png!=null)withContext(Dispatchers.Default){val options=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeByteArray(png,0,png.size,options);require(options.outWidth in 1..1600&&options.outHeight in 1..4096&&options.outWidth.toLong()*options.outHeight<=SdkPdfProtocol.MAX_PIXELS);BitmapFactory.decodeByteArray(png,0,png.size)?:error("PDF page could not be decoded")}else null
   PdfReply(result,bitmap)
  }finally{source?.close();pipe?.forEach{runCatching{it.close()}};if(bound)runCatching{c.unbindService(connection)}}
 }
}
