package app.onklav.home

import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import okio.Timeout

internal class SdkServiceCall(private val input:Request,private val result:()->Response):Call{
 @Volatile var cancelled=false;var executed=false
 override fun request()=input
 override fun execute():Response{executed=true;check(!cancelled);return result()}
 override fun enqueue(callback:Callback){error("Synchronous fixture only")}
 override fun cancel(){cancelled=true}
 override fun isExecuted()=executed
 override fun isCanceled()=cancelled
 override fun clone():Call=SdkServiceCall(input,result)
 override fun timeout()=Timeout()
}
internal fun sdkResponse(request:Request,bytes:ByteArray,status:Int=200)=Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(status).message("Fixture").body(bytes.toResponseBody("application/octet-stream".toMediaType())).build()
