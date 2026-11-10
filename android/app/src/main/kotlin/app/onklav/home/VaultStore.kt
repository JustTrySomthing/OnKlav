package app.onklav.home

import android.content.Context
import android.util.AtomicFile
import java.io.ByteArrayOutputStream
import java.io.File
import org.json.JSONObject

/** One host-private encrypted document. Passwords, answers and decrypted content
 * are never persisted. Password/recovery wraps protect the same Tink keyset. */
internal class VaultStore(c:Context) {
 private val file=AtomicFile(File(c.filesDir,"vault.bin"))
 private var header:FolderVaultCrypto.Header?=null
 private var key:ByteArray?=null
 @Synchronized fun exists()=file.baseFile.exists()||File(file.baseFile.path+".bak").exists()
 @Synchronized fun create(password:CharArray):WriteDocument {
  check(!exists()){ "Vault already exists" }
  val(h,k)=FolderVaultCrypto.create(IDENTITY,password)
  val doc=WriteDocument(title="Vault")
  try{write(doc,h,k);header=h;key=k;return doc}catch(e:Exception){k.fill(0);throw e}
 }
 @Synchronized fun unlock(password:CharArray):WriteDocument {
  close()
  file.openRead().use{input->
   require(file.baseFile.length()<=8_100_000){"Vault exceeds its size limit"}
   val h=FolderVaultCrypto.readHeader(input);require(h.folder==IDENTITY){"Invalid Vault identity"}
   val k=FolderVaultCrypto.unlock(VaultRecovery.passwordHeader(h),password)
   try{
    val output=object:ByteArrayOutputStream(){
     override fun write(b:ByteArray,off:Int,len:Int){require(count+len<=MAX_DOCUMENT){"Vault exceeds 8 MB"};super.write(b,off,len)}
     override fun write(b:Int){require(count<MAX_DOCUMENT);super.write(b)}
     fun wipe(){buf.fill(0);reset()}
    }
    // Reopen to authenticate the entire stream, including the header identity.
    file.openRead().use{FolderVaultCrypto.decrypt(it,output,k,IDENTITY)}
    val bytes=output.toByteArray()
    val doc=try{require(bytes.size<=MAX_DOCUMENT);WriteDocument.parse(JSONObject(bytes.toString(Charsets.UTF_8)))}finally{bytes.fill(0);output.wipe()}
    header=h;key=k;return doc
   }catch(e:Exception){k.fill(0);throw e}
  }
 }
 @Synchronized fun save(doc:WriteDocument){val h=FolderVaultCrypto.nextPayload(checkNotNull(header));write(doc,h,checkNotNull(key));header=h}
 private fun write(doc:WriteDocument,h:FolderVaultCrypto.Header,k:ByteArray){
  val bytes=doc.json().toString().toByteArray(Charsets.UTF_8)
  try{require(bytes.size<=MAX_DOCUMENT){"Vault is full (8 MB). Remove an image or shorten the text."}
   val out=file.startWrite()
   try{FolderVaultCrypto.seal(out,h,k){it.write(bytes)};file.finishWrite(out)}catch(e:Exception){file.failWrite(out);throw e}
  }finally{bytes.fill(0)}
 }
 @Synchronized fun changePassword(old:CharArray,replacement:CharArray,doc:WriteDocument){
  val h=checkNotNull(header);val check=FolderVaultCrypto.unlock(VaultRecovery.passwordHeader(h),old)
  try{val next=VaultRecovery.rewrap(h,checkNotNull(key),replacement);write(doc,next,checkNotNull(key));header=next}finally{check.fill(0)}
 }
 @Synchronized fun recoveryQuestions():List<Int>?=if(!exists())null else file.openRead().use{val h=FolderVaultCrypto.readHeader(it);require(h.folder==IDENTITY){"Invalid Vault identity"};VaultRecovery.read(h)?.choices}
 @Synchronized fun createWithRecovery(password:CharArray,selected:List<Int>,answers:List<CharArray>){
  check(!exists()||recoveryQuestions()==null){ "Vault setup is already complete" };close();val(h,k)=FolderVaultCrypto.create(IDENTITY,password)
  try{val next=VaultRecovery.add(h,k,selected,answers);write(WriteDocument(title="Vault"),next,k)}finally{k.fill(0);close()}
 }
 @Synchronized fun completeRecovery(selected:List<Int>,answers:List<CharArray>,doc:WriteDocument){
  val next=VaultRecovery.add(checkNotNull(header),checkNotNull(key),selected,answers)
  write(doc,next,checkNotNull(key));header=next;close()
 }
 @Synchronized fun resetPassword(answers:List<CharArray>,replacement:CharArray){
  close();val h=file.openRead().use{FolderVaultCrypto.readHeader(it)};val b=checkNotNull(VaultRecovery.read(h)){"Recovery is not configured"};val secret=VaultRecovery.secret(b.choices,answers)
  val k=try{FolderVaultCrypto.unlock(b.recovery,secret)}finally{secret.fill('\u0000')}
  try{
   // Authenticate and parse the complete document before changing anything.
   val output=object:ByteArrayOutputStream(){override fun write(b:ByteArray,off:Int,len:Int){require(count+len<=MAX_DOCUMENT);super.write(b,off,len)};override fun write(b:Int){require(count<MAX_DOCUMENT);super.write(b)};fun wipe(){buf.fill(0);reset()}}
   try{file.openRead().use{FolderVaultCrypto.decrypt(it,output,k,IDENTITY)};val bytes=output.toByteArray();val doc=try{WriteDocument.parse(JSONObject(bytes.toString(Charsets.UTF_8)))}finally{bytes.fill(0)}
    write(doc,VaultRecovery.rewrap(h,k,replacement),k)
   }finally{output.wipe()}
  }finally{k.fill(0);close()}
 }
 @Synchronized fun close(){key?.fill(0);key=null;header=null}
 companion object{const val IDENTITY="6f6e6b6c61767661756c74646f637631";const val MAX_DOCUMENT=8_000_000}
}
