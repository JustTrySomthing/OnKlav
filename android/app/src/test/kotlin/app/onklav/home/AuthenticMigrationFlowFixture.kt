package app.onklav.home
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Base64
import java.net.URLEncoder
import java.io.File

internal object AuthenticMigrationFlowFixture{
 private fun number(n:Long):ByteArray{var x=n;val out=java.io.ByteArrayOutputStream();do{var b=(x and 127).toInt();x=x ushr 7;if(x!=0L)b=b or 128;out.write(b)}while(x!=0L);return out.toByteArray()}
 private fun field(n:Int,b:ByteArray)=number((n*8+2).toLong())+number(b.size.toLong())+b
 private fun integer(n:Int,v:Long)=number((n*8).toLong())+number(v)
 private fun part(index:Int,batch:Long=10):String{val a=field(1,ByteArray(20){(65+index).toByte()})+field(2,"Example:person$index".toByteArray())+field(3,"Example".toByteArray())+integer(4,1)+integer(5,1)+integer(6,2);val b=field(1,a)+integer(2,1)+integer(3,2)+integer(4,index.toLong())+integer(5,batch);return "otpauth-migration://offline?data="+URLEncoder.encode(Base64.getEncoder().encodeToString(b),"UTF-8")}
 private fun waitFor(test:()->Boolean){val end=System.nanoTime()+20_000_000_000;while(System.nanoTime()<end){shadowOf(android.os.Looper.getMainLooper()).idle();if(test())return;Thread.sleep(10)};fail("Authentic model did not finish its bounded operation")}
 fun exercise(){
  val c=ApplicationProvider.getApplicationContext<Application>();File(c.filesDir,"authentic.bin").delete();val m=AuthenticModel(c)
  try{m.unlock("migration-password".toCharArray(),true);waitFor{m.unlocked&&!m.busy};m.accept(part(0));assertTrue(m.pending.isEmpty());m.importPending();assertTrue(m.accounts.isEmpty());m.accept(part(0));assertTrue(m.pending.isEmpty());assertTrue(m.batchStatus.startsWith("1 of 2"));m.accept(part(1,99));assertTrue(m.status.contains("another transfer"));assertTrue(m.pending.isEmpty());m.accept(part(1));assertEquals(2,m.pending.size);assertTrue(m.accounts.isEmpty());m.importPending();waitFor{!m.busy&&m.accounts.size==2};m.lock();assertTrue(m.accounts.isEmpty());m.unlock("migration-password".toCharArray());waitFor{m.unlocked&&!m.busy};assertEquals(setOf("person0","person1"),m.accounts.map{it.account}.toSet())}finally{m.lock()}
 }
}
