package app.onklav.home

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.util.Locale
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject

internal data class AuthenticAccount(val id:String=UUID.randomUUID().toString(),val issuer:String,val account:String,val secret:String,val type:String="totp",val algorithm:String="SHA1",val digits:Int=6,val period:Int=30,val counter:Long=0,val pinned:Boolean=false){
 override fun toString()="AuthenticAccount(redacted)"
 fun validate(){require(id.length in 1..64&&issuer.length<=128&&account.length in 1..128&&!(issuer+account).any{it.isISOControl()||it==':'}){"Use a short issuer and account name."};require(type in listOf("totp","hotp")&&algorithm in listOf("SHA1","SHA256","SHA512")&&digits in listOf(6,8)&&period in 1..3600&&counter in 0 until Long.MAX_VALUE){"Unsupported account settings."};val bytes=AuthenticOtp.base32(secret);try{require(bytes.size in 10..128){"Setup keys must contain 10–128 bytes."}}finally{bytes.fill(0)}}
 fun json()=JSONObject().put("id",id).put("issuer",issuer).put("account",account).put("secret",secret).put("type",type).put("algorithm",algorithm).put("digits",digits).put("period",period).put("counter",counter).put("pinned",pinned)
 fun fingerprint():String{val key=AuthenticOtp.base32(secret);return try{val hash=java.security.MessageDigest.getInstance("SHA-256").digest(key);val alphabet="0123456789abcdef";val hex=CharArray(hash.size*2);for(i in hash.indices){val n=hash[i].toInt()and 255;hex[i*2]=alphabet[n ushr 4];hex[i*2+1]=alphabet[n and 15]};String(hex)+":$type:$algorithm:$digits:${if(type=="hotp")0 else period}"}finally{key.fill(0)}}
 companion object{fun parse(j:JSONObject)=AuthenticAccount(j.getString("id"),j.getString("issuer"),j.getString("account"),j.getString("secret"),j.getString("type"),j.getString("algorithm"),j.getInt("digits"),j.getInt("period"),j.getLong("counter"),j.optBoolean("pinned")).also{it.validate()}}
}
internal object AuthenticOtp{
 private const val alphabet="ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
 fun base32(value:String):ByteArray{
  val s=value.filterNot{it.isWhitespace()||it=='-'}.uppercase(Locale.ROOT);require(s.length in 1..256){"Invalid Base32 setup key."};val raw=s.trimEnd('=');require('=' !in raw&&raw.length%8 in listOf(0,2,4,5,7)&&raw.all{it in alphabet}){"Invalid Base32 setup key."};if(raw.length!=s.length)require(s.length%8==0&&s.length-raw.length<8){"Invalid Base32 padding."}
  val out=java.io.ByteArrayOutputStream();var bits=0;var buffer=0
  raw.forEach{buffer=(buffer shl 5)or alphabet.indexOf(it);bits+=5;if(bits>=8){bits-=8;out.write((buffer shr bits)and 255)}};require(bits==0||buffer and ((1 shl bits)-1)==0){"Invalid Base32 trailing bits."};return out.toByteArray()
 }
 fun encode(bytes:ByteArray):String{val out=StringBuilder();var buffer=0;var bits=0;bytes.forEach{buffer=(buffer shl 8)or(it.toInt()and 255);bits+=8;while(bits>=5){bits-=5;out.append(alphabet[(buffer shr bits)and 31])}};if(bits>0)out.append(alphabet[(buffer shl(5-bits))and 31]);return out.toString()}
 fun hotp(key:ByteArray,counter:Long,algorithm:String="SHA1",digits:Int=6):String{
  require(counter>=0&&algorithm in listOf("SHA1","SHA256","SHA512")&&digits in listOf(6,8));val mac=Mac.getInstance("Hmac$algorithm");mac.init(SecretKeySpec(key,"Hmac$algorithm"));val hash=mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array())
  try{val offset=hash.last().toInt()and 15;val value=((hash[offset].toInt()and 127)shl 24)or((hash[offset+1].toInt()and 255)shl 16)or((hash[offset+2].toInt()and 255)shl 8)or(hash[offset+3].toInt()and 255);return(value%if(digits==8)100000000 else 1000000).toString().padStart(digits,'0')}finally{hash.fill(0)}
 }
 fun code(a:AuthenticAccount,seconds:Long):String{val key=base32(a.secret);return try{hotp(key,if(a.type=="hotp")a.counter else seconds.coerceAtLeast(0)/a.period,a.algorithm,a.digits)}finally{key.fill(0)}}
 fun remaining(a:AuthenticAccount,seconds:Long)=a.period-(seconds.coerceAtLeast(0)%a.period).toInt()
 private fun decode(s:String)=URLDecoder.decode(s.replace("+","%2B"),"UTF-8")
 private fun enc(s:String)=URLEncoder.encode(s,"UTF-8").replace("+","%20")
 internal fun parameters(uri:URI):Map<String,String>{val result=linkedMapOf<String,String>();uri.rawQuery.orEmpty().split('&').filter{it.isNotEmpty()}.forEach{part->val p=part.split('=',limit=2);val k=decode(p[0]);require(k !in result){"Duplicate setup parameter."};result[k]=decode(p.getOrElse(1){""})};return result}
 fun parse(input:String):AuthenticAccount{
  try{require(input.length<=8192);val u=URI(input.trim());require(u.scheme=="otpauth"&&u.host in listOf("totp","hotp")&&u.userInfo==null&&u.port==-1&&u.fragment==null){"Use an otpauth setup QR or link."};val p=parameters(u);val label=decode(u.rawPath.orEmpty().removePrefix("/"));require(label.isNotBlank()){"Account label is missing."};val prefix=if(':' in label)label.substringBefore(':').trim()else"";val name=if(':' in label)label.substringAfter(':').trim()else label.trim();val issuer=p["issuer"]?.trim()?:prefix;require(prefix.isEmpty()||issuer==prefix){"Issuer labels do not match."};val secret=encode(base32(p["secret"]?:error("Setup key is missing.")));val counter=if(u.host=="hotp")p["counter"]?.toLongOrNull()?:error("HOTP requires a counter.")else 0L
   return AuthenticAccount(issuer=issuer,account=name,secret=secret,type=u.host,algorithm=(p["algorithm"]?:"SHA1").uppercase(Locale.ROOT),digits=p["digits"]?.toIntOrNull()?:if("digits" in p)error("Invalid digit count.")else 6,period=p["period"]?.toIntOrNull()?:if("period" in p)error("Invalid period.")else 30,counter=counter).also{it.validate()}
  }catch(e:Exception){throw IllegalArgumentException("Invalid or unsupported authenticator setup. Check the key, type, algorithm and issuer.")}
 }
 fun uri(a:AuthenticAccount):String{a.validate();val label=if(a.issuer.isBlank())a.account else "${a.issuer}:${a.account}";return "otpauth://${a.type}/${enc(label)}?secret=${enc(a.secret)}&issuer=${enc(a.issuer)}&algorithm=${a.algorithm}&digits=${a.digits}"+if(a.type=="hotp")"&counter=${a.counter}"else"&period=${a.period}"}
 fun merge(existing:List<AuthenticAccount>,incoming:List<AuthenticAccount>):List<AuthenticAccount>{val out=linkedMapOf<String,AuthenticAccount>();existing.forEach{out[it.fingerprint()]=it};incoming.forEach{a->a.validate();val fp=a.fingerprint();val previous=out[fp];if(previous==null)out[fp]=a.copy(id=UUID.randomUUID().toString())else if(a.type=="hotp"&&a.counter>previous.counter)out[fp]=previous.copy(counter=a.counter)};require(out.size<=500){"Authentic supports up to 500 accounts."};return out.values.toList()}
}
