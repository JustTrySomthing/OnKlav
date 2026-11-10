package app.onklav.home

import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.URLEncoder
import java.io.ByteArrayOutputStream
import java.util.Base64

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class AuthenticOtpTest{
 private val secret="12345678901234567890".toByteArray()
 @Test fun rfc4226AllTenHotpVectors(){listOf("755224","287082","359152","969429","338314","254676","287922","162583","399871","520489").forEachIndexed{i,code->assertEquals(code,AuthenticOtp.hotp(secret,i.toLong()))}}
 @Test fun rfc6238AllAlgorithmsAndPost2038Times(){val times=listOf(59L,1111111109,1111111111,1234567890,2000000000,20000000000);val expected=mapOf("SHA1" to listOf("94287082","07081804","14050471","89005924","69279037","65353130"),"SHA256" to listOf("46119246","68084774","67062674","91819424","90698825","77737706"),"SHA512" to listOf("90693936","25091201","99943326","93441116","38618901","47863826"));expected.forEach{(algo,codes)->val key=("1234567890".repeat(7)).take(when(algo){"SHA1"->20;"SHA256"->32;else->64}).toByteArray();times.forEachIndexed{i,time->assertEquals(codes[i],AuthenticOtp.hotp(key,time/30,algo,8))}}}
 @Test fun base32RoundTripAndNonCanonicalInputs(){for(size in 10..128){val bytes=ByteArray(size){(it*17).toByte()};assertArrayEquals(bytes,AuthenticOtp.base32(AuthenticOtp.encode(bytes)))};assertArrayEquals(byteArrayOf(72,101,108,108,111,33,-34,-83,-66,-17),AuthenticOtp.base32("JBSWY3DPEHPK3PXP")) }
 @Test fun invalidBase32AndUnsupportedUriParametersAreRejected(){for(s in listOf("0ABC","A","ABC=DE","MZ","ABC123"))assertTrue(s,runCatching{AuthenticOtp.base32(s)}.isFailure);for(p in listOf("algorithm=MD5","digits=7","period=0","digits=bad","secret=AAAA")){assertTrue(runCatching{AuthenticOtp.parse("otpauth://totp/Test:me?secret=${AuthenticOtp.encode(secret)}&issuer=Test&$p")}.isFailure)}}
 @Test fun uriRoundTripRetainsAllOtpParametersAndPlusLabels(){val a=AuthenticAccount(issuer="A+B Company",account="a+b@example.com",secret=AuthenticOtp.encode(secret),algorithm="SHA256",digits=8,period=60);val b=AuthenticOtp.parse(AuthenticOtp.uri(a));assertEquals(a.copy(id=b.id),b);assertEquals(60,AuthenticOtp.remaining(a,120));assertEquals(1,AuthenticOtp.remaining(a,179));val h=a.copy(type="hotp",counter=97,period=30);val parsed=AuthenticOtp.parse(AuthenticOtp.uri(h));assertEquals(h.copy(id=parsed.id),parsed)}
 @Test fun duplicatesDoNotRollHotpCountersBackward(){val a=AuthenticAccount(issuer="Service",account="me",secret=AuthenticOtp.encode(secret),type="hotp",counter=40);assertEquals(40,AuthenticOtp.merge(listOf(a),listOf(a.copy(counter=1))).single().counter);assertEquals(44,AuthenticOtp.merge(listOf(a),listOf(a.copy(counter=44))).single().counter);assertEquals(1,AuthenticOtp.merge(listOf(a),listOf(a.copy(issuer="Renamed",period=60))).size)}
 private fun number(n:Long)=ByteArrayOutputStream().apply{var x=n;do{var b=(x and 127).toInt();x=x ushr 7;if(x!=0L)b=b or 128;write(b)}while(x!=0L)}.toByteArray()
 private fun field(n:Int,bytes:ByteArray)=number((n*8+2).toLong())+number(bytes.size.toLong())+bytes
 private fun integer(n:Int,x:Long)=number((n*8).toLong())+number(x)
 private fun migration(algorithm:Int=1,size:Int=2,index:Int=0):String{val account=field(1,secret)+field(2,"Test:me".toByteArray())+field(3,"Test".toByteArray())+integer(4,algorithm.toLong())+integer(5,1)+integer(6,2);val payload=field(1,account)+integer(2,1)+integer(3,size.toLong())+integer(4,index.toLong())+integer(5,10);return "otpauth-migration://offline?data="+URLEncoder.encode(Base64.getEncoder().encodeToString(payload),"UTF-8")}
 @Test fun googleTransferParsesBatchAndRejectsUnsupportedAlgorithms(){val p=AuthenticMigration.parse(migration());assertEquals(2,p.size);assertEquals(0,p.index);assertEquals(10,p.batch);assertEquals("me",p.accounts.single().account);assertEquals("Test",p.accounts.single().issuer);assertEquals(AuthenticOtp.encode(secret),p.accounts.single().secret);assertTrue(runCatching{AuthenticMigration.parse(migration(4))}.isFailure);assertTrue(runCatching{AuthenticMigration.parse(migration(size=2,index=2))}.isFailure)}
 @Test fun completeGoogleTransferWaitsForAllQrFragmentsAndExplicitConfirmation(){AuthenticMigrationFlowFixture.exercise()}
 @Test fun malformedTransferAndHostileUrisNeverExposeInputInErrors(){for(s in listOf("otpauth://evil/user?secret=VERYPRIVATE","otpauth://totp/TEST:me?secret=VERYPRIVATE&issuer=OTHER","otpauth-migration://offline?data=BADPRIVATE","otpauth://totp/%?secret=PRIVATE")){val e=runCatching{if(s.startsWith("otpauth-migration"))AuthenticMigration.parse(s)else AuthenticOtp.parse(s)}.exceptionOrNull()!!;assertFalse(e.message.orEmpty().contains("PRIVATE"))}}
}
