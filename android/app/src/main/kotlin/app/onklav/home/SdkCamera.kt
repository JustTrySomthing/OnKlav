package app.onklav.home

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File
import org.json.JSONObject

class SdkCameraProvider:FileProvider()

internal class SdkCameraContract:ActivityResultContracts.TakePicture(){
 override fun createIntent(context:Context,input:Uri):Intent=super.createIntent(context,input).apply{
  addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
  clipData=ClipData.newRawUri("Camera capture",input)
 }
}
internal object SdkCamera {
 private fun folder(c:Context)=File(c.cacheDir.canonicalFile,"sdk-camera")
 internal fun file(c:Context,token:String):File{
  require(token.matches(Regex("[a-f0-9]{32}"))){"Invalid camera request"}
  val file=File(folder(c),"$token.jpg");check(file.canonicalFile==file.absoluteFile){"Camera storage is unavailable"};return file
 }
 fun prepare(c:Context,token:String,authorize:()->Unit):Uri{
  authorize();val directory=folder(c);check(directory.mkdirs()||directory.isDirectory);check(directory.canonicalFile==directory.absoluteFile)
  directory.listFiles().orEmpty().filter{it.isFile&&System.currentTimeMillis()-it.lastModified()>24*60*60*1000L}.forEach{it.delete()}
  check(directory.listFiles().orEmpty().size<8){"Finish or cancel pending camera captures first"}
  val target=file(c,token);check(target.createNewFile()){"Camera capture already exists. Start a new request."}
  return FileProvider.getUriForFile(c,c.packageName+".sdk-camera",target)
 }
 fun read(c:Context,token:String,authorize:()->Unit):ByteArray{
  authorize();val target=file(c,token);check(target.isFile&&target.length() in 1..20*1024*1024L){"Camera output is empty or exceeds20MB"}
  return target.inputStream().use{SdkDrawingImages.normalize(SdkDrawingImages.read(it,authorize))}.also{authorize()}
 }
 fun commit(c:Context,bytes:ByteArray,authorize:()->Unit,commit:()->JSONObject):JSONObject=AppSdk.carrierEgress{
  require(bytes.size<=600*1024){"Camera image exceeds its normalized limit"};authorize()
  val staged=File(c.filesDir.canonicalFile,"content-import.tmp");check(staged.canonicalFile==staged.absoluteFile){"Camera staging is unavailable"}
  try{staged.writeBytes(bytes);authorize();commit()}finally{staged.delete()}
 }
 fun cleanup(c:Context,token:String){
  val target=file(c,token);runCatching{c.revokeUriPermission(FileProvider.getUriForFile(c,c.packageName+".sdk-camera",target),Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)}
  target.delete()
 }
}
