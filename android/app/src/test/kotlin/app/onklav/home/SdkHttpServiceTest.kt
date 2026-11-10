package app.onklav.home

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Timeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class SdkHttpServiceTest {
    private fun operation(url:String="https://api.example.com/items")=JSONObject().put("service","http.request").put("owner",JSONObject().put("id","org.example.http").put("publisher","a".repeat(64))).put("origins",JSONArray().put("https://api.example.com")).put("args",JSONObject().put("url",url))
    private class FakeCall(private val input:Request,private val response:()->Response):Call{
        var cancelled=false;var executed=false
        override fun request()=input
        override fun execute():Response{executed=true;return response()}
        override fun enqueue(responseCallback:Callback){throw UnsupportedOperationException()}
        override fun cancel(){cancelled=true}
        override fun isExecuted()=executed
        override fun isCanceled()=cancelled
        override fun clone():Call=FakeCall(input,response)
        override fun timeout()=Timeout()
    }
    private fun response(request:Request,text:String,status:Int=200)=Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(status).message("Fixture").body(text.toResponseBody("application/json".toMediaType())).build()
    @Test fun requestPolicyRejectsOtherOriginsCredentialsAndUnsafeUrls(){
        for(url in listOf("http://api.example.com/","https://evil.example.com/","https://api.example.com@evil.example.com/","https://127.0.0.1/","https://api.example.com/#token"))assertTrue(url,runCatching{SdkHttpPolicy.request(operation(url))}.isFailure)
        val secret=operation();secret.getJSONObject("args").put("headers",JSONObject().put("Authorization","Bearer private"))
        assertTrue(runCatching{SdkHttpPolicy.request(secret)}.isFailure)
        assertEquals("api.example.com",SdkHttpPolicy.request(operation()).url.host)
        val post=operation();post.getJSONObject("args").put("method","POST").put("body","{}")
        assertTrue(SdkHttpPolicy.request(post).body!!.isOneShot())
    }
    @Test fun boundedResponsesKeepHttpErrorsButStripCookiesAndCrossOriginRedirects()=runBlocking{
        val op=operation();var call:FakeCall?=null;var checks=0
        val value=SdkHttpService.fetch("fixture-response",{checks++;op}){request->FakeCall(request){response(request,"{\"error\":\"missing\"}",404).newBuilder().header("Set-Cookie","private=value").header("Location","https://evil.example.com/path").header("ETag","fixture").build()}.also{call=it}}
        assertEquals(404,value.getInt("status"));assertEquals("fixture",value.getJSONObject("headers").getString("etag"));assertFalse(value.getJSONObject("headers").has("set-cookie"));assertFalse(value.getJSONObject("headers").has("location"));assertTrue(checks>=3);assertTrue(call!!.cancelled)
    }
    @Test fun oversizedResponsesAndAuthorizationChangesNeverReturnPayloads()=runBlocking{
        val op=operation();var calls=0
        assertTrue(runCatching{SdkHttpService.fetch("fixture-size",{op}){request->calls++;FakeCall(request){response(request,"x".repeat(131073))}}}.isFailure)
        assertEquals(1,calls)
        var checks=0;var cancelled=false
        assertTrue(runCatching{SdkHttpService.fetch("fixture-revoked",{if(++checks>=3)throw IllegalStateException("revoked");op}){request->object:Call by FakeCall(request,{response(request,"private result")}){override fun cancel(){cancelled=true}}}}.isFailure)
        assertTrue(cancelled)
    }
    @Test fun transportFailuresDoNotExposeRequestUrlsOrProviderMessages()=runBlocking{
        val op=operation("https://api.example.com/items?private=hidden")
        val error=runCatching{SdkHttpService.fetch("fixture-failure",{op}){request->FakeCall(request){throw IOException("private=hidden provider detail")}}}.exceptionOrNull()!!
        assertFalse(error.message.orEmpty().contains("hidden"));assertFalse(error.message.orEmpty().contains("provider detail"))
    }
    @Test fun perAppConcurrentRequestsAreBounded()=runBlocking{
        kotlinx.coroutines.withTimeout(5000){
            val op=operation();op.getJSONObject("owner").put("id","org.example.httpconcurrent")
            val both=kotlinx.coroutines.CompletableDeferred<Unit>();val release=kotlinx.coroutines.CompletableDeferred<Unit>();val arrived=java.util.concurrent.atomic.AtomicInteger()
            val jobs=(0..1).map{index->async{
                var checks=0
                SdkHttpService.fetch("concurrent-$index",{if(++checks==2){if(arrived.incrementAndGet()==2)both.complete(Unit);release.await()};op}){request->FakeCall(request){response(request,"ok")}}
            }}
            try{
                both.await()
                assertTrue(runCatching{SdkHttpService.fetch("concurrent-extra",{op}){error("Third request reached the network")}}.isFailure)
            }finally{release.complete(Unit)}
            jobs.forEach{it.await()}
        }
    }

}
