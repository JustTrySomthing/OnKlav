package app.onklav.home

import android.content.Context
import android.util.AtomicFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** Password-protected ciphertext only. Reuses OnKlav's authenticated Tink envelope, with a distinct identity. */
internal class AuthenticStore(c:Context){
 private val file=AtomicFile(File(c.filesDir,"authentic.bin"))
 private var header:FolderVaultCrypto.Header?=null
 private var key:ByteArray?=null
 companion object{const val ID="61757468656e7469632d7661756c7431";const val LIMIT=600000
  fun validate(rows:List<AuthenticAccount>){require(rows.size<=500&&rows.map{it.id}.toSet().size==rows.size);rows.forEach{it.validate()};require(rows.map{it.fingerprint()}.toSet().size==rows.size){"Duplicate accounts in backup."}}
  private fun document(rows:List<AuthenticAccount>):ByteArray{validate(rows);return JSONObject().put("format","OnKlav Authentic").put("version",1).put("accounts",JSONArray().apply{rows.forEach{put(it.json())}}).toString().toByteArray().also{require(it.size<=LIMIT)}}
  private fun decode(bytes:ByteArray,password:CharArray):Triple<List<AuthenticAccount>,FolderVaultCrypto.Header,ByteArray>{
   require(bytes.size in 100..LIMIT+20000){"Invalid Authentic backup size."};val h=FolderVaultCrypto.readHeader(ByteArrayInputStream(bytes));require(h.folder==ID){"This is not an Authentic backup."};val k=FolderVaultCrypto.unlock(VaultRecovery.passwordHeader(h),password)
   return decodeKey(bytes,h,k)
  }
  private fun decodeKey(bytes:ByteArray,h:FolderVaultCrypto.Header,k:ByteArray):Triple<List<AuthenticAccount>,FolderVaultCrypto.Header,ByteArray>{
   try{val output=object:ByteArrayOutputStream(){override fun write(b:ByteArray,off:Int,len:Int){require(count+len<=LIMIT);super.write(b,off,len)};override fun write(b:Int){require(count<LIMIT);super.write(b)};fun wipe(){buf.fill(0)}}
    try{FolderVaultCrypto.decrypt(ByteArrayInputStream(bytes),output,k,ID);val plain=output.toByteArray();val rows=try{val j=JSONObject(plain.toString(Charsets.UTF_8));require(j.getString("format")=="OnKlav Authentic"&&j.getInt("version")==1);val a=j.getJSONArray("accounts");require(a.length()<=500);List(a.length()){AuthenticAccount.parse(a.getJSONObject(it))}.also(::validate)}finally{plain.fill(0)};return Triple(rows,h,k)}finally{output.wipe()}
   }catch(e:Exception){k.fill(0);throw e}
  }
 }
 @Synchronized fun exists()=file.baseFile.exists()||File(file.baseFile.path+".bak").exists()
 @Synchronized fun bytes():ByteArray{require(exists());return file.openRead().use{val out=ByteArrayOutputStream();FilesBroker.copyBoundedLimit(it,out,(LIMIT+20000).toLong()){};out.toByteArray()}}
 @Synchronized fun create(password:CharArray):List<AuthenticAccount>{check(!exists()){ "Authentic is already set up." };val(h,k)=FolderVaultCrypto.create(ID,password);try{write(emptyList(),h,k);header=h;key=k;return emptyList()}catch(e:Exception){k.fill(0);throw e}}
 @Synchronized fun unlock(password:CharArray):List<AuthenticAccount>{close();val(rows,h,k)=decode(bytes(),password);header=h;key=k;return rows}
 @Synchronized fun erase(){close();file.delete()}
 @Synchronized fun close(){key?.fill(0);key=null;header=null}
 @Synchronized fun save(rows:List<AuthenticAccount>){val h=FolderVaultCrypto.nextPayload(checkNotNull(header){"Unlock Authentic first."});write(rows,h,checkNotNull(key));header=h}
 private fun write(rows:List<AuthenticAccount>,h:FolderVaultCrypto.Header,k:ByteArray){val plain=document(rows);try{val out=file.startWrite();try{FolderVaultCrypto.seal(out,h,k){it.write(plain)};file.finishWrite(out)}catch(t:Throwable){file.failWrite(out);throw t}}finally{plain.fill(0)}}
 @Synchronized fun inspectBackup(bytes:ByteArray,password:CharArray):List<AuthenticAccount>{val(rows,_,k)=decode(bytes,password);k.fill(0);return rows}
 @Synchronized fun restoreNew(bytes:ByteArray,password:CharArray):List<AuthenticAccount>{check(!exists());val(rows,h,k)=decode(bytes,password);try{val out=file.startWrite();try{out.write(bytes);file.finishWrite(out)}catch(t:Throwable){file.failWrite(out);throw t};header=h;key=k;return rows}catch(t:Throwable){k.fill(0);throw t}}
 @Synchronized fun changePassword(password:CharArray,replacement:CharArray,rows:List<AuthenticAccount>){val(_,_,check)=decode(bytes(),password);check.fill(0);val h=VaultRecovery.rewrap(checkNotNull(header),checkNotNull(key),replacement);write(rows,h,checkNotNull(key));header=h}
 @Synchronized fun verify(password:CharArray){val(_,_,k)=decode(bytes(),password);k.fill(0)}
 @Synchronized fun recoveryQuestions():List<Int>?=if(!exists())null else file.openRead().use{VaultRecovery.read(FolderVaultCrypto.readHeader(it))?.choices}
 @Synchronized fun createWithRecovery(password:CharArray,choices:List<Int>,answers:List<CharArray>){
  check(!exists());val(h,k)=FolderVaultCrypto.create(ID,password)
  try{write(emptyList(),VaultRecovery.add(h,k,choices,answers),k)}finally{k.fill(0);close()}
 }
 @Synchronized fun enrollRecovery(choices:List<Int>,answers:List<CharArray>,rows:List<AuthenticAccount>){
  val next=VaultRecovery.add(checkNotNull(header),checkNotNull(key),choices,answers)
  write(rows,next,checkNotNull(key));close()
 }
 @Synchronized fun resetPassword(answers:List<CharArray>,replacement:CharArray){
  close();val bytes=bytes();val h=FolderVaultCrypto.readHeader(ByteArrayInputStream(bytes));require(h.folder==ID)
  val bundle=checkNotNull(VaultRecovery.read(h));val secret=VaultRecovery.secret(bundle.choices,answers)
  val k=try{FolderVaultCrypto.unlock(bundle.recovery,secret)}finally{secret.fill('\u0000')}
  try{val(rows,_,_)=decodeKey(bytes,h,k);write(rows,VaultRecovery.rewrap(h,k,replacement),k)}finally{k.fill(0);bytes.fill(0);close()}
 }

}
