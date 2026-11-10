package app.onklav.home

import com.google.crypto.tink.*
import com.google.crypto.tink.streamingaead.PredefinedStreamingAeadParameters
import com.google.crypto.tink.streamingaead.StreamingAeadConfig
import java.io.*
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Password-wrapped Tink streaming keyset. All file names and folder metadata
 * live inside the authenticated stream. No unencrypted keyset is written. */
internal object FolderVaultCrypto {
 const val ITERATIONS=600_000
 const val MAX_BYTES=256L*1024*1024
 private val magic="OKVAULT1".toByteArray(Charsets.US_ASCII)
 private val random=SecureRandom()
 init{StreamingAeadConfig.register()}
 data class Header(val folder:String,val salt:ByteArray,val wrapNonce:ByteArray,val wrappedKey:ByteArray,val generation:ByteArray)
 private fun bytes(n:Int)=ByteArray(n).also(random::nextBytes)
 private fun validate(h:Header){require(h.folder.matches(Regex("[a-f0-9]{32}"))&&h.salt.size==16&&h.wrapNonce.size==12&&h.wrappedKey.size in 32..16384&&h.generation.size==16){"Invalid encrypted folder header"}}
 private fun keyCipher(mode:Int,key:ByteArray,nonce:ByteArray,folder:String)=Cipher.getInstance("AES/GCM/NoPadding").apply{
  init(mode,SecretKeySpec(key,"AES"),GCMParameterSpec(128,nonce));updateAAD("OnKlav folder key v1:$folder".toByteArray(Charsets.UTF_8))
 }
 private fun passwordKey(password:CharArray,salt:ByteArray):ByteArray{
  require(password.size in 8..256){"Use a password of 8–256 characters"}
  val spec=PBEKeySpec(password,salt,ITERATIONS,256)
  return try{SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded}finally{spec.clearPassword()}
 }
 private fun primitive(key:ByteArray):StreamingAead{
  require(key.size in 32..16368)
  return TinkProtoKeysetFormat.parseKeyset(key,InsecureSecretKeyAccess.get()).getPrimitive(RegistryConfiguration.get(),StreamingAead::class.java)
 }
 fun create(folder:String,password:CharArray):Pair<Header,ByteArray>{
  require(folder.matches(Regex("[a-f0-9]{32}")))
  val key=TinkProtoKeysetFormat.serializeKeyset(KeysetHandle.generateNew(PredefinedStreamingAeadParameters.AES256_GCM_HKDF_1MB),InsecureSecretKeyAccess.get())
  return try{wrap(folder,key,password,bytes(16)) to key}catch(e:Exception){key.fill(0);throw e}
 }
 private fun wrap(folder:String,key:ByteArray,password:CharArray,generation:ByteArray):Header{
  val salt=bytes(16);val nonce=bytes(12);val derived=passwordKey(password,salt)
  return try{Header(folder,salt,nonce,keyCipher(Cipher.ENCRYPT_MODE,derived,nonce,folder).doFinal(key),generation)}finally{derived.fill(0)}
 }
 fun nextPayload(h:Header)=h.copy(generation=bytes(16)).also(::validate)
 fun changePassword(h:Header,key:ByteArray,password:CharArray)=wrap(h.folder,key,password,h.generation)
 fun unlock(h:Header,password:CharArray):ByteArray{
  validate(h);val derived=passwordKey(password,h.salt)
  return try{keyCipher(Cipher.DECRYPT_MODE,derived,h.wrapNonce,h.folder).doFinal(h.wrappedKey).also{primitive(it)}}finally{derived.fill(0)}
 }
 fun writeHeader(output:OutputStream,h:Header){validate(h);DataOutputStream(output).apply{
  write(magic);write(h.folder.toByteArray(Charsets.US_ASCII));write(h.salt);write(h.wrapNonce);writeInt(h.wrappedKey.size);write(h.wrappedKey);write(h.generation);writeInt(ITERATIONS);flush()
 }}
 fun headerBytes(h:Header)=ByteArrayOutputStream().also{writeHeader(it,h)}.toByteArray()
 fun readHeader(input:InputStream):Header{
  val data=DataInputStream(input)
  fun read(n:Int)=ByteArray(n).also{data.readFully(it)}
  require(read(8).contentEquals(magic)){"This is not an OnKlav encrypted folder"}
  val folder=read(32).toString(Charsets.US_ASCII);val salt=read(16);val nonce=read(12);val size=data.readInt();require(size in 32..16384)
  val h=Header(folder,salt,nonce,read(size),read(16));require(data.readInt()==ITERATIONS){"Unsupported folder encryption version"};validate(h);return h
 }
 private fun aad(h:Header)="OnKlav folder data v1:${h.folder}:".toByteArray(Charsets.UTF_8)+h.generation
 private class NonClosing(out:OutputStream):FilterOutputStream(out){override fun close(){flush()};override fun write(b:ByteArray,off:Int,len:Int){out.write(b,off,len)}}
 private class Limited(out:OutputStream):FilterOutputStream(out){var count=0L
  override fun write(value:Int){require(++count<=MAX_BYTES){"Encrypted folder exceeds 256 MB"};out.write(value)}
  override fun write(b:ByteArray,off:Int,len:Int){require(count+len<=MAX_BYTES){"Encrypted folder exceeds 256 MB"};out.write(b,off,len);count+=len}
 }
 /** The caller owns the atomic file transaction; failure must not replace it. */
 fun seal(output:OutputStream,h:Header,key:ByteArray,writer:(OutputStream)->Unit){
  val bounded=Limited(NonClosing(output));writeHeader(bounded,h)
  primitive(key).newEncryptingStream(NonClosing(bounded),aad(h)).use{body->writer(NonClosing(body))}
 }
 /** Caller must use an untrusted temporary, drain to EOF, and publish only
  * after authentication of the final stream segment has succeeded. */
 fun decrypt(input:InputStream,output:OutputStream,key:ByteArray,expectedFolder:String):Header{
  val h=readHeader(input);require(h.folder==expectedFolder){"Encrypted folder identity changed"}
  primitive(key).newDecryptingStream(input,aad(h)).use{body->copy(body,output)};output.flush();return h
 }
 fun copy(input:InputStream,output:OutputStream,limit:Long=MAX_BYTES):Long{
  val buffer=ByteArray(64*1024);var total=0L
  while(true){val n=input.read(buffer);if(n<0)break;if(n==0)continue;total+=n;require(total<=limit){"Folder exceeds the supported size"};output.write(buffer,0,n)};return total
 }
 fun replaceHeader(input:InputStream,output:OutputStream,h:Header){
  val old=readHeader(input);require(old.folder==h.folder&&old.generation.contentEquals(h.generation)){"Folder encryption identity changed"}
  writeHeader(output,h);copy(input,output);output.flush()
 }
}
