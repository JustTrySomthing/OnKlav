package app.onklav.home
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Timeout
@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class SdkRadioTest {
 private fun row()=JSONObject().put("stationuuid","aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa").put("name","Public Jazz").put("codec","MP3").put("hls",0).put("lastcheckok",1).put("url_resolved","https://stream.example.org/live").put("favicon","https://tracking.example.org/logo").put("countrycode","US")
 @Test fun directoryProjectionIsBoundedAndNeverReturnsCredentialsArtworkOrStreamUrls(){val r=row();val result=SdkRadio.station(r)!!;assertEquals("Public Jazz",result.getString("name"));assertNotNull(SdkRadio.station(JSONObject(r.toString()).put("url_resolved","https://stream.example.org:8443/live")));assertNotNull(SdkRadio.station(JSONObject(r.toString()).put("url_resolved","http://stream.example.org/live")));assertNotNull(SdkRadio.station(JSONObject(r.toString()).put("codec","OPUS")));assertNotNull(SdkRadio.station(JSONObject(r.toString()).put("hls",1)));for(k in listOf("url","url_resolved","favicon","account","api_key"))assertFalse(result.has(k));for(change in listOf("url_resolved" to "https://127.0.0.1/live","lastcheckok" to 0)){val bad=JSONObject(r.toString()).put(change.first,change.second);assertNull(SdkRadio.station(bad))}}
 private class CallStub(val req:Request,val reply:()->Response):Call {var cancelled=false;override fun request()=req;override fun execute()=reply();override fun enqueue(responseCallback:Callback)=error("unused");override fun cancel(){cancelled=true};override fun isExecuted()=true;override fun isCanceled()=cancelled;override fun clone():Call=CallStub(req,reply);override fun timeout()=Timeout()}
 private fun response(req:Request,code:Int,type:String="audio/mpeg",location:String?=null)=Response.Builder().request(req).protocol(Protocol.HTTP_1_1).message("Fixture").code(code).body("fixture".toResponseBody()).header("Content-Type",type).apply{if(location!=null)header("Location",location)}.build()
 @Test fun redirectResolutionValidatesEveryTargetAndDoesNotAddCredentials(){val calls=mutableListOf<CallStub>();val hosts=mutableListOf<String>();val result=SdkRadio.resolve("test","https://stream.example.org/live",{},connect={req->assertNull(req.header("Authorization"));assertNull(req.header("Cookie"));CallStub(req){if(req.url.host=="stream.example.org")response(req,302,location="https://cdn.example.org/audio")else response(req,200)}.also{calls.add(it)}},lookup={hosts.add(it)});assertEquals("https://cdn.example.org/audio",result);assertEquals(listOf("stream.example.org","cdn.example.org"),hosts);assertTrue(calls.all{it.cancelled})}
 @Test fun signInPagesDowngradesPrivateTargetsAndRevocationFailClosed(){for(target in listOf("file:///private/audio","https://127.0.0.1/private")){assertThrows(Exception::class.java){SdkRadio.resolve("bad","https://stream.example.org/live",{},connect={req->CallStub(req){response(req,302,location=target)}},lookup={})}};assertThrows(Exception::class.java){SdkRadio.resolve("html","https://stream.example.org/live",{},connect={req->CallStub(req){response(req,200,"text/html")}},lookup={})};var reached=false;assertThrows(Exception::class.java){SdkRadio.resolve("revoked","https://stream.example.org/live",{error("revoked")},connect={reached=true;error("transport")},lookup={})};assertFalse(reached)}
}
