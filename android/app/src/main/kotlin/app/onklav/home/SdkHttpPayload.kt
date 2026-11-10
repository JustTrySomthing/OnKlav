package app.onklav.home
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
/** Shared wire conversion after the signed-origin transport's bounded stream read. */
internal object SdkHttpPayload{
 fun result(status:Int,bytes:ByteArray,headers:JSONObject,binary:Boolean):JSONObject{
  require(status in 100..599&&bytes.size<=if(binary)262144 else 131072)
  val result=JSONObject().put("status",status).put("headers",headers)
  if(binary){val digits="0123456789abcdef";val out=CharArray(bytes.size*2);for(i in bytes.indices){val b=bytes[i].toInt()and 255;out[i*2]=digits[b ushr 4];out[i*2+1]=digits[b and 15]};result.put("hex",String(out))}
  else{val text=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();require(text.none{it.isISOControl()&&it !in "\n\r\t"}){"HTTP response is not supported UTF-8 text"};result.put("text",text)}
  return result
 }
}
