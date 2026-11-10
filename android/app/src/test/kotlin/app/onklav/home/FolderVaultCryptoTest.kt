package app.onklav.home

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream

class FolderVaultCryptoTest{
 private val id="a".repeat(32)
 @Test fun authenticatedStreamingRoundTripRejectsWrongPasswordTamperingAndTruncation(){
  val(h,key)=FolderVaultCrypto.create(id,"correct horse battery".toCharArray())
  val plain=ByteArray(2_200_000){(it*31).toByte()};val out=ByteArrayOutputStream();FolderVaultCrypto.seal(out,h,key){it.write(plain)};val encrypted=out.toByteArray()
  val unlocked=FolderVaultCrypto.unlock(FolderVaultCrypto.readHeader(encrypted.inputStream()),"correct horse battery".toCharArray())
  val restored=ByteArrayOutputStream();FolderVaultCrypto.decrypt(encrypted.inputStream(),restored,unlocked,id);assertArrayEquals(plain,restored.toByteArray())
  assertThrows(Exception::class.java){FolderVaultCrypto.unlock(h,"wrong password".toCharArray())}
  for(bytes in listOf(encrypted.copyOf(encrypted.size-1),encrypted.copyOf().apply{this[lastIndex-35]=(this[lastIndex-35].toInt() xor 1).toByte()}))assertThrows(Exception::class.java){FolderVaultCrypto.decrypt(bytes.inputStream(),ByteArrayOutputStream(),key,id)}
  assertThrows(Exception::class.java){FolderVaultCrypto.decrypt(encrypted.inputStream(),ByteArrayOutputStream(),key,"b".repeat(32))}
  val wrongGeneration=ByteArrayOutputStream();FolderVaultCrypto.writeHeader(wrongGeneration,FolderVaultCrypto.nextPayload(h));wrongGeneration.write(encrypted.copyOfRange(FolderVaultCrypto.headerBytes(h).size,encrypted.size));assertThrows(Exception::class.java){FolderVaultCrypto.decrypt(wrongGeneration.toByteArray().inputStream(),ByteArrayOutputStream(),key,id)}
  key.fill(0);unlocked.fill(0)
 }
 @Test fun passwordChangeKeepsPayloadAndRejectsOldPassword(){
  val(h,key)=FolderVaultCrypto.create(id,"old password".toCharArray());val out=ByteArrayOutputStream();FolderVaultCrypto.seal(out,h,key){it.write("private photo".toByteArray())}
  val replacement=FolderVaultCrypto.changePassword(h,key,"new password".toCharArray());val changed=ByteArrayOutputStream();FolderVaultCrypto.replaceHeader(out.toByteArray().inputStream(),changed,replacement)
  assertThrows(Exception::class.java){FolderVaultCrypto.unlock(replacement,"old password".toCharArray())}
  val newKey=FolderVaultCrypto.unlock(replacement,"new password".toCharArray());val restored=ByteArrayOutputStream();FolderVaultCrypto.decrypt(changed.toByteArray().inputStream(),restored,newKey,id);assertEquals("private photo",restored.toString("UTF-8"));key.fill(0);newKey.fill(0)
  assertThrows(Exception::class.java){FolderVaultCrypto.create(id,"short".toCharArray())}
  assertThrows(Exception::class.java){FolderVaultCrypto.readHeader(ByteArray(200).inputStream())}
 }
}
