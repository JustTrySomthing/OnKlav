package app.onklav.home

import java.net.URI
import java.util.Base64

/** Bounded reader for the Google Authenticator migration wire format; unsupported enums reject. */
internal object AuthenticMigration{
 data class Part(val batch:Long,val size:Int,val index:Int,val accounts:List<AuthenticAccount>)
 private class Wire(val bytes:ByteArray){var at=0
  fun number():Long{var n=0L;for(i in 0..9){require(at<bytes.size);val b=bytes[at++].toInt()and 255;require(i<9||b<=1);n=n or((b and 127).toLong()shl(i*7));if(b<128)return n};error("Invalid protobuf")}
  fun small():Int{val n=number();require(n in 0..Int.MAX_VALUE.toLong());return n.toInt()}
  fun data():ByteArray{val n=number();require(n in 0..200000&&n<=bytes.size-at);return bytes.copyOfRange(at,at+n.toInt()).also{at+=n.toInt()}}
  fun fields(action:(Int,Int)->Unit){while(at<bytes.size){val tag=number();require(tag>=8&&tag<=Int.MAX_VALUE);action((tag shr 3).toInt(),(tag and 7).toInt())}}
  fun skip(type:Int){when(type){0->number();2->data();1->{require(at+8<=bytes.size);at+=8};5->{require(at+4<=bytes.size);at+=4};else->error("Unsupported protobuf")}}
 }
 fun parse(input:String):Part{
  try{require(input.length<=100000);val u=URI(input.trim());require(u.scheme=="otpauth-migration"&&u.host=="offline"&&u.fragment==null&&u.userInfo==null&&u.port==-1);val encoded=AuthenticOtp.parameters(u)["data"]?:error("Missing data");val bytes=runCatching{Base64.getDecoder().decode(encoded)}.getOrElse{Base64.getUrlDecoder().decode(encoded)};require(bytes.size<=100000);val w=Wire(bytes);val accounts=mutableListOf<AuthenticAccount>();var version=0;var size=1;var index=0;var batch=0L
   w.fields{field,type->when(field){1->{require(type==2&&accounts.size<500);accounts+=account(w.data())};2->{require(type==0);version=w.small()};3->{require(type==0);size=w.small()};4->{require(type==0);index=w.small()};5->{require(type==0);batch=w.number()};else->w.skip(type)}}
   require(version==1&&size in 1..100&&index in 0 until size&&accounts.isNotEmpty());return Part(batch,size,index,accounts)
  }catch(e:Exception){throw IllegalArgumentException("Unsupported or damaged Google Authenticator transfer QR. Scan a fresh export QR.")}
 }
 private fun account(bytes:ByteArray):AuthenticAccount{val w=Wire(bytes);var key=byteArrayOf();var name="";var issuer="";var algorithm=0;var digits=0;var type=0;var counter=0L
  try{w.fields{field,wire->when(field){1->{require(wire==2);key=w.data();require(key.size in 10..128)};2->{require(wire==2);name=w.data().toString(Charsets.UTF_8)};3->{require(wire==2);issuer=w.data().toString(Charsets.UTF_8)};4->{require(wire==0);algorithm=w.small()};5->{require(wire==0);digits=w.small()};6->{require(wire==0);type=w.small()};7->{require(wire==0);counter=w.number()};else->w.skip(wire)}}
   require(algorithm in 1..3&&digits in 1..2&&type in 1..2);if(issuer.isNotBlank()&&name.startsWith("$issuer:"))name=name.removePrefix("$issuer:").trim()else if(issuer.isBlank()&&':' in name){issuer=name.substringBefore(':').trim();name=name.substringAfter(':').trim()}
   return AuthenticAccount(issuer=issuer,account=name,secret=AuthenticOtp.encode(key),algorithm=listOf("","SHA1","SHA256","SHA512")[algorithm],digits=if(digits==1)6 else 8,type=if(type==1)"hotp"else"totp",counter=counter).also{it.validate()}
  }finally{key.fill(0)}
 }
}
