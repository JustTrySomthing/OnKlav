package app.onklav.home

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

internal interface SecretKeys {
 fun load(alias:String,create:Boolean):SecretKey
 fun deletePrefix(prefix:String)
}
internal class AndroidSecretKeys:SecretKeys {
 private fun store()=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
 override fun load(alias:String,create:Boolean):SecretKey {
  val store=store();(store.getKey(alias,null) as? SecretKey)?.let{return it}
  check(create){"The encryption key is unavailable. Existing secrets were kept."}
  val generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
  generator.init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
   .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build())
  return generator.generateKey()
 }
 override fun deletePrefix(prefix:String){val store=store();val names=store.aliases().toList().filter{it.startsWith(prefix)};names.forEach{store.deleteEntry(it)}}
}

/** Ciphertext only in this directory. No software-key/plaintext fallback. */
internal class SdkSecretStore(directory:File,private val keys:SecretKeys,private val namespace:String="vault") {
 init{require(namespace in setOf("vault","accounts"))}
 private val valueLimit=if(namespace=="accounts")262144 else MAX_VALUE
 private val root=directory.canonicalFile
 companion object {const val MAX_BYTES=600000;const val MAX_ENTRIES=32;const val MAX_VALUE=4096}
 private fun hash(value:String)=MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
 private fun app(id:String):String{require(id.length<=128&&id.matches(Regex("[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)+"))){"Invalid secret owner"};return hash(id)}
 private fun location(id:String,publisher:String):Pair<File,String>{
  require(publisher.matches(Regex("[0-9a-f]{64}"))){"Invalid secret publisher"}
  val owner=app(id);return File(File(root,owner),"$publisher.bin") to "onklav.$namespace.$owner.$publisher"
 }
 private fun regular(path:File){check(path.canonicalFile==path.absoluteFile){"Invalid secure storage location"};check(!path.exists()||path.isFile){"Invalid secure storage entry"}}
 private fun read(file:AtomicFile):ByteArray=file.openRead().use{input->
  val output=ByteArrayOutputStream();val block=ByteArray(8192)
  while(true){val count=input.read(block);if(count<0)break;check(output.size()+count<=MAX_BYTES+64){"Encrypted storage exceeds its limit"};output.write(block,0,count)};output.toByteArray()
 }
 private fun decode(path:File,alias:String,aad:ByteArray):JSONObject {
  regular(path);regular(File(path.path+".bak"));regular(File(path.path+".new"))
  if(!path.exists()&&!File(path.path+".bak").exists())return JSONObject()
  val bytes=read(AtomicFile(path));check(bytes.size>=29&&bytes[0]==1.toByte()){ "Unsupported secure storage format" }
  val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,keys.load(alias,false),GCMParameterSpec(128,bytes.copyOfRange(1,13)));cipher.updateAAD(aad)
  val plain=cipher.doFinal(bytes.copyOfRange(13,bytes.size))
  try {check(plain.size<=MAX_BYTES);val value=JSONObject(String(plain,Charsets.UTF_8));validate(value);return value}finally{plain.fill(0)}
 }
 private fun validate(value:JSONObject){
  check(value.length()<=MAX_ENTRIES){"Store up to32 secret values per app"}
  for(key in value.keys()){
   require(key.length in 1..64&&key.matches(Regex("[A-Za-z0-9._-]+"))){"Invalid secret key"}
   require(value.opt(key) is String && value.getString(key).length<=valueLimit){"Secret values are limited to4096 UTF-16 units"}
  }
 }
 @Synchronized fun run(id:String,publisher:String,service:String,args:JSONObject,authorize:()->Unit):JSONObject {
  require(service in setOf("secrets.get","secrets.set","secrets.delete")){"Unsupported secure storage operation"}
  val key=args.getString("key");require(key.length in 1..64&&key.matches(Regex("[A-Za-z0-9._-]+"))){"Invalid secret key"}
  require(args.keys().asSequence().all{it in if(service=="secrets.set")setOf("key","value")else setOf("key")}){"Unsupported secret argument"}
  if(service=="secrets.set")require(args.opt("value") is String&&args.getString("value").length<=valueLimit){"Secret value exceeds its limit"}
  val (path,alias)=location(id,publisher);authorize();val aad=("onklav.${if(namespace=="vault")"secrets" else "accounts"}.v1\u0000$id\u0000$publisher").toByteArray(Charsets.UTF_8);val values=decode(path,alias,aad)
  if(service=="secrets.get"){authorize();return JSONObject().put("value",if(values.has(key))values.getString(key)else JSONObject.NULL)}
  val existed=values.has(key)
  if(service=="secrets.set")values.put(key,args.getString("value"))else values.remove(key)
  validate(values)
  if(service=="secrets.delete"&&!existed){authorize();return JSONObject().put("deleted",false)}
  val plain=values.toString().toByteArray(Charsets.UTF_8);check(plain.size<=MAX_BYTES){"Secret storage limit reached"}
  authorize();check(root.mkdirs()||root.isDirectory);check(path.parentFile!!.mkdirs()||path.parentFile!!.isDirectory)
  check(root.canonicalFile==root.absoluteFile&&path.parentFile!!.canonicalFile==path.parentFile!!.absoluteFile){"Invalid secure storage folder"}
  val encrypted=try{
   val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,keys.load(alias,!path.exists()&&!File(path.path+".bak").exists()));check(cipher.iv.size==12);cipher.updateAAD(aad)
   byteArrayOf(1)+cipher.iv+cipher.doFinal(plain)
  }finally{plain.fill(0)}
  authorize();check(root.mkdirs()||root.isDirectory);check(path.parentFile!!.mkdirs()||path.parentFile!!.isDirectory)
  check(root.canonicalFile==root.absoluteFile&&path.parentFile!!.canonicalFile==path.parentFile!!.absoluteFile){"Invalid secure storage folder"}
  regular(path);val atomic=AtomicFile(path);val stream=atomic.startWrite()
  try{stream.write(encrypted);authorize();atomic.finishWrite(stream)}catch(error:Exception){atomic.failWrite(stream);throw error}
  return if(service=="secrets.set")JSONObject().put("stored",true)else JSONObject().put("deleted",existed)
 }
 @Synchronized fun deleteApp(id:String){
  val owner=app(id);val directory=File(root,owner)
  check(directory.canonicalFile==directory.absoluteFile){"Invalid secure storage folder"}
  keys.deletePrefix("onklav.$namespace.$owner.")
  if(directory.exists())check(directory.deleteRecursively()){"Secret cleanup remains pending"}
 }
}

internal object SdkSecrets {
 private val keys=AndroidSecretKeys()
 private fun store(c:Context)=SdkSecretStore(File(c.noBackupFilesDir,"sdk-vault"),keys)
 fun execute(c:Context,authorize:()->JSONObject):JSONObject=AppSdk.carrierEgress {
  val request=authorize();val owner=request.getJSONObject("owner");val service=request.getString("service");val args=request.getJSONObject("args")
  try{store(c).run(owner.getString("id"),owner.getString("publisher"),service,args){
   val live=authorize();check(live.getJSONObject("owner").toString()==owner.toString()&&live.getString("service")==service&&live.getJSONObject("args").toString()==args.toString()){"Secure storage request changed"}
  }}catch(error:Exception){throw IllegalStateException("Secure storage could not complete. Existing secrets were kept; check device access and available space.")}
 }
 fun deleteApp(c:Context,id:String)=AppSdk.carrierEgress{store(c).deleteApp(id)}
}
