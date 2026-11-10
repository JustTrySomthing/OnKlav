package app.onklav.home

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class CloudAccountRepairTest {
 private val c get()=ApplicationProvider.getApplicationContext<Application>()
 private val secret=KeyGenerator.getInstance("AES").apply{init(256)}.generateKey()
 private val armed=AtomicBoolean(false)
 private val entered=CountDownLatch(1)
 private val release=CountDownLatch(1)
 private val models=mutableListOf<CloudModel>()
 private val keys=object:SecretKeys{
  override fun load(alias:String,create:Boolean):SecretKey {
   if(armed.compareAndSet(true,false)){entered.countDown();check(release.await(5,TimeUnit.SECONDS)){"Fixture commit gate timed out"}}
   return secret
  }
  override fun deletePrefix(prefix:String){}
 }
 @Before fun clean(){File(c.noBackupFilesDir,"cloud-files").deleteRecursively()}
 @After fun stop(){release.countDown();models.forEach{it.stop()}}
 private fun pumpUntil(done:()->Boolean){val end=System.nanoTime()+10_000_000_000L;do{shadowOf(android.os.Looper.getMainLooper()).idle();if(done())return;Thread.sleep(10)}while(System.nanoTime()<end);fail("Cloud operation timed out")}
 private fun idle(m:CloudModel)=pumpUntil{!m.busy}
 private fun model(store:CloudStore)=CloudModel(c,store){a->CloudDav(a){r->cloudResponse(r,207,cloudXml(cloudRow("/dav/","Root",true)))}}.also{models+=it;idle(it);assertTrue(it.loaded)}
 @Test fun connectMergesAnAccountAddedAfterThisWindowLoaded(){
  val store=CloudStore(c,keys);val a=cloudAccount();val b=cloudAccount();val added=cloudAccount();store.save(listOf(a));val m=model(store)
  CloudStore(c,keys).save(listOf(a,b));m.connect(added){};idle(m)
  assertTrue(m.error,m.error.isEmpty());assertEquals(setOf(a.id,b.id,added.id),store.load().map{it.id}.toSet());assertEquals(3,m.accounts.size)
 }
 @Test fun disconnectPreservesAnAccountAddedByAnotherWindow(){
  val store=CloudStore(c,keys);val a=cloudAccount();val b=cloudAccount();store.save(listOf(a));val m=model(store)
  CloudStore(c,keys).save(listOf(a,b));m.disconnect(a);idle(m)
  assertTrue(m.error,m.error.isEmpty());assertEquals(listOf(b.id),store.load().map{it.id});assertEquals(listOf(b.id),m.accounts.map{it.id})
 }
 @Test fun cancelDuringConnectCommitStillAcknowledgesTheCommittedAccount(){
  val store=CloudStore(c,keys);val a=cloudAccount();val added=cloudAccount();store.save(listOf(a));val m=model(store)
  try{armed.set(true);m.connect(added){};pumpUntil{entered.count==0L};m.stop();release.countDown();idle(m)
   assertEquals(setOf(a.id,added.id),store.load().map{it.id}.toSet());assertEquals(store.load(),m.accounts);assertEquals(added.id,m.account?.id)
  }finally{release.countDown()}
 }
 @Test fun cancelDuringDisconnectCommitDoesNotLeaveARemovedAccountInMemory(){
  val store=CloudStore(c,keys);val a=cloudAccount();store.save(listOf(a));val m=model(store)
  try{armed.set(true);m.disconnect(a);pumpUntil{entered.count==0L};m.stop();release.countDown();idle(m)
   assertTrue(store.load().isEmpty());assertTrue(m.accounts.isEmpty());assertNull(m.account)
  }finally{release.countDown()}
 }
 @Test fun separateStoreInstancesSerializeTheirReadModifyWriteTransactions(){
  val store=CloudStore(c,keys);val other=CloudStore(c,keys);val a=cloudAccount();val b=cloudAccount();store.save(emptyList())
  val firstEntered=CountDownLatch(1);val firstRelease=CountDownLatch(1);val secondStarted=CountDownLatch(1);val secondEntered=CountDownLatch(1);val pool=Executors.newFixedThreadPool(2)
  try{val first=pool.submit(Callable{store.update{firstEntered.countDown();check(firstRelease.await(5,TimeUnit.SECONDS));it+a}})
   assertTrue(firstEntered.await(2,TimeUnit.SECONDS))
   val second=pool.submit(Callable{secondStarted.countDown();other.update{secondEntered.countDown();it+b}})
   assertTrue(secondStarted.await(2,TimeUnit.SECONDS));assertFalse(secondEntered.await(100,TimeUnit.MILLISECONDS));firstRelease.countDown();first.get(5,TimeUnit.SECONDS);second.get(5,TimeUnit.SECONDS)
   assertEquals(setOf(a.id,b.id),store.load().map{it.id}.toSet())
  }finally{firstRelease.countDown();pool.shutdownNow()}
 }
}
