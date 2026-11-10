package app.onklav.home
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.ByteArrayOutputStream
@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class SdkHttpUploadTest{
 private val c=ApplicationProvider.getApplicationContext<Context>()
 private fun operation():JSONObject{val handle="a".repeat(32);val f=File(c.filesDir,"app-sdk/data/org.example.upload/private-blobs-v1/$handle.data");f.parentFile!!.mkdirs();f.writeBytes(ByteArray(140000){(it%255).toByte()});return JSONObject().put("owner",JSONObject().put("id","org.example.upload")).put("args",JSONObject().put("upload",handle)).put("upload_file",JSONObject().put("id",handle).put("path",f.absolutePath).put("size",f.length()))}
 @Test fun privateUploadStreamsExactBytesAndRejectsForeignPath(){val op=operation();val f=SdkHttpUpload.source(c.filesDir,op);val out=ByteArrayOutputStream();var checks=0;SdkHttpUpload.copy(f,f.length(),out){checks++};assertArrayEquals(f.readBytes(),out.toByteArray());assertTrue(checks>=4);op.getJSONObject("owner").put("id","org.example.foreign");assertThrows(Exception::class.java){SdkHttpUpload.source(c.filesDir,op)}}
 @Test fun revocationStopsLaterChunksAndLengthMismatchFails(){val op=operation();val f=SdkHttpUpload.source(c.filesDir,op);val out=ByteArrayOutputStream();var checks=0;assertThrows(Exception::class.java){SdkHttpUpload.copy(f,f.length(),out){if(++checks==3)error("revoked")}};assertTrue(out.size()<=65536);assertThrows(Exception::class.java){SdkHttpUpload.copy(f,1,ByteArrayOutputStream()) {}}}
}
