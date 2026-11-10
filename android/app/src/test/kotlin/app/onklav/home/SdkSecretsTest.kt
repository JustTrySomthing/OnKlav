package app.onklav.home

import java.io.File
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class SdkSecretsTest {
 private class Keys(private val shared:SecretKey?=null):SecretKeys {
  val values=mutableMapOf<String,SecretKey>()
  override fun load(alias:String,create:Boolean):SecretKey=values[alias]?:run{check(create){"Missing key"};(shared?:KeyGenerator.getInstance("AES").apply{init(256)}.generateKey()).also{values[alias]=it}}
  override fun deletePrefix(prefix:String){values.keys.filter{it.startsWith(prefix)}.forEach{values.remove(it)}}
 }
 private val publisher="a".repeat(64)
 private fun run(store:SdkSecretStore,operation:String,key:String="token",value:String?=null,app:String="org.example.one",pub:String=publisher,authorize:()->Unit={}):JSONObject{
  val args=JSONObject().put("key",key);if(value!=null)args.put("value",value)
  return store.run(app,pub,"secrets.$operation",args,authorize)
 }
 @Test fun encryptedRoundTripUsesFreshNoncesAndDoesNotCreateDataOnMissingReads(){
  val root=kotlin.io.path.createTempDirectory("vault").toFile();try{val keys=Keys();val store=SdkSecretStore(root,keys)
   assertTrue(run(store,"get").isNull("value"));assertTrue(keys.values.isEmpty());assertTrue(root.listFiles()!!.isEmpty())
   run(store,"set",value="private-秘密-🙂");val file=root.walk().single{it.isFile};val first=file.readBytes();assertFalse(String(first,Charsets.ISO_8859_1).contains("private-"));assertEquals("private-秘密-🙂",run(store,"get").getString("value"))
   run(store,"set",value="private-秘密-🙂");assertFalse(first.contentEquals(file.readBytes()));assertTrue(run(store,"delete").getBoolean("deleted"));assertTrue(run(store,"get").isNull("value"));assertFalse(run(store,"delete").getBoolean("deleted"))
  }finally{root.deleteRecursively()}
 }
 @Test fun publisherAndAppNamespacesStayIsolatedAndSwappedCiphertextFailsClosed(){
  val root=kotlin.io.path.createTempDirectory("vault").toFile();try{val sharedKey=KeyGenerator.getInstance("AES").apply{init(256)}.generateKey();val store=SdkSecretStore(root,Keys(sharedKey));run(store,"set",value="one");val first=root.walk().single{it.isFile};run(store,"set",value="two",app="org.example.two");assertTrue(run(store,"get",pub="b".repeat(64)).isNull("value"));val second=root.walk().single{it.isFile&&it!=first};val old=second.readBytes();second.writeBytes(first.readBytes());assertThrows(Exception::class.java){run(store,"get",app="org.example.two")};second.writeBytes(old);assertEquals("one",run(store,"get").getString("value"));assertEquals("two",run(store,"get",app="org.example.two").getString("value"))}finally{root.deleteRecursively()}
 }
 @Test fun corruptDataAndLostKeysNeverResetOrOverwriteExistingSecrets(){
  val root=kotlin.io.path.createTempDirectory("vault").toFile();try{val keys=Keys();val store=SdkSecretStore(root,keys);run(store,"set",value="keep");val file=root.walk().single{it.isFile};val original=file.readBytes();keys.values.clear();assertThrows(Exception::class.java){run(store,"get")};assertThrows(Exception::class.java){run(store,"set",value="replace")};assertArrayEquals(original,file.readBytes());store.deleteApp("org.example.one");assertTrue(run(store,"get").isNull("value"));run(store,"set",value="new");val bytes=file.readBytes();bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte();file.writeBytes(bytes);assertThrows(Exception::class.java){run(store,"get")};assertThrows(Exception::class.java){run(store,"set",value="replace")};assertArrayEquals(bytes,file.readBytes())}finally{root.deleteRecursively()}
 }
 @Test fun boundsAndLateRevocationKeepThePreviousCiphertext(){
  val root=kotlin.io.path.createTempDirectory("vault").toFile();try{val store=SdkSecretStore(root,Keys());run(store,"set",value="keep");val file=root.walk().single{it.isFile};val original=file.readBytes();assertThrows(Exception::class.java){run(store,"set",value="x".repeat(4097))};assertThrows(Exception::class.java){run(store,"get",key="../escape")};var calls=0;assertThrows(Exception::class.java){run(store,"set",value="blocked",authorize={if(++calls>=4)error("Revoked")})};assertArrayEquals(original,file.readBytes());assertEquals("keep",run(store,"get").getString("value"));for(i in 1..31)run(store,"set",key="item$i",value="value");assertThrows(Exception::class.java){run(store,"set",key="overflow",value="blocked")};assertEquals("keep",run(store,"get").getString("value"))}finally{root.deleteRecursively()}
 }
 @Test fun deletionRemovesOnlyThatAppsCiphertextAndKeys(){
  val root=kotlin.io.path.createTempDirectory("vault").toFile();try{val keys=Keys();val store=SdkSecretStore(root,keys);run(store,"set",value="one");run(store,"set",value="two",app="org.example.two");store.deleteApp("org.example.one");assertEquals(1,keys.values.size);assertTrue(run(store,"get").isNull("value"));assertEquals("two",run(store,"get",app="org.example.two").getString("value"))}finally{root.deleteRecursively()}
 }
 @Test fun deletionAlsoRemovesKeysAfterCiphertextWasLost(){
  val root=kotlin.io.path.createTempDirectory("vault").toFile();try{
   val keys=Keys();val store=SdkSecretStore(root,keys)
   run(store,"set",value="one");run(store,"set",value="two",app="org.example.two")
   // Identify the first owner's folder from the deterministic app hash.
   val hash=java.security.MessageDigest.getInstance("SHA-256").digest("org.example.one".toByteArray()).joinToString(""){"%02x".format(it)}
   assertTrue(File(root,hash).deleteRecursively());assertEquals(2,keys.values.size)
   store.deleteApp("org.example.one");assertEquals(1,keys.values.size)
   assertEquals("two",run(store,"get",app="org.example.two").getString("value"))
  }finally{root.deleteRecursively()}
 }
}
