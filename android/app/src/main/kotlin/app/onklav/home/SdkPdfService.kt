package app.onklav.home

import android.app.Service
import android.content.Intent
import android.os.*
import android.os.Message as OsMessage
import java.io.OutputStream

/** Only descriptor-based PDF work runs here; this isolated service inherits no host grants. */
class SdkPdfService:Service(){
 private lateinit var thread:HandlerThread
 private lateinit var messenger:Messenger
 private val main=Handler(Looper.getMainLooper())
 override fun onCreate(){super.onCreate();thread=HandlerThread("OnKlav-PDF");thread.start();messenger=Messenger(object:Handler(thread.looper){override fun handleMessage(msg:OsMessage){work(msg)}})}
 override fun onBind(intent:Intent):IBinder=messenger.binder
 override fun onUnbind(intent:Intent):Boolean{stopSelf();return false}
 override fun onDestroy(){thread.quitSafely();super.onDestroy()}
 @Suppress("DEPRECATION") private fun work(message:OsMessage){
  val deadline=Runnable{android.os.Process.killProcess(android.os.Process.myPid())};main.postDelayed(deadline,12000)
  val request=message.data;val source=request.getParcelable<ParcelFileDescriptor>("source");val sink=request.getParcelable<ParcelFileDescriptor>("output")
  val response=Bundle()
  try{
   require(source!=null);source.use{input->
    if(sink!=null){ParcelFileDescriptor.AutoCloseOutputStream(sink).use{raw->var written=0L;val bounded=object:OutputStream(){override fun write(b:Int){check(++written<=SdkPdfProtocol.MAX_PNG);raw.write(b)};override fun write(b:ByteArray,off:Int,len:Int){written+=len;check(written<=SdkPdfProtocol.MAX_PNG);raw.write(b,off,len)}};response.putAll(SdkPdfProtocol.execute(input,request,bounded))}}
    else response.putAll(SdkPdfProtocol.execute(input,request,null))
   };response.putBoolean("ok",true)
  }catch(e:Exception){response.putBoolean("ok",false);response.putString("error",if(e is SecurityException)"Password-protected PDF is unsupported"else"PDF could not be processed. It may be unsupported, damaged, or too large.")}
  finally{runCatching{source?.close()};runCatching{sink?.close()};main.removeCallbacks(deadline)}
  runCatching{message.replyTo?.send(OsMessage.obtain(null,1).apply{data=response})};stopSelf()
 }
}
