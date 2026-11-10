package app.onklav.home
import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
/** Owner-resolved immutable input; caller URLs/paths are never accepted as upload sources. */
internal object SdkHttpUpload{
 fun source(files:File,operation:JSONObject):File{val id=operation.getJSONObject("owner").getString("id");val handle=operation.getJSONObject("args").getString("upload");val meta=operation.getJSONObject("upload_file");require(id.matches(Regex("[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)+"))&&handle.matches(Regex("[a-f0-9]{32}"))&&meta.getString("id")==handle);val expected=File(files,"app-sdk/data/$id/private-blobs-v1/$handle.data");val actual=File(meta.getString("path"));require(actual.absoluteFile==expected.absoluteFile&&actual.canonicalFile==actual.absoluteFile&&!java.nio.file.Files.isSymbolicLink(actual.toPath())&&actual.isFile&&actual.length()==meta.getLong("size")&&actual.length()<=10485760);return actual}
 fun copy(file:File,size:Long,out:OutputStream,authorize:()->Unit){require(size in 0..10485760);authorize();file.inputStream().use{input->val buffer=ByteArray(65536);var total=0L;while(true){authorize();val n=input.read(buffer);if(n<0)break;require(total+n<=size);out.write(buffer,0,n);total+=n};require(total==size)};authorize()}
}
