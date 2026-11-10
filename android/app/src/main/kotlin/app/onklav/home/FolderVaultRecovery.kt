package app.onklav.home

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Temporary, device-bound recovery wrapper while a folder is open. Every
 * successful lock destroys its session key, leaving only password protection. */
internal interface FolderVaultRecovery{
 fun wrap(folder:String,bytes:ByteArray):JSONObject
 fun unwrap(folder:String,value:JSONObject):ByteArray
 fun erase(folder:String,value:JSONObject)
}
internal object AndroidFolderVaultRecovery:FolderVaultRecovery{
 private fun store()=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
 private fun alias(folder:String,value:JSONObject):String{
  require(folder.matches(Regex("[a-f0-9]{32}")))
  return value.getString("alias").also{require(it.matches(Regex("onklav\\.folder\\.session\\.$folder\\.[a-f0-9]{32}")))}
 }
 override fun wrap(folder:String,bytes:ByteArray):JSONObject{
  require(folder.matches(Regex("[a-f0-9]{32}")));val alias="onklav.folder.session.$folder.${UUID.randomUUID().toString().replace("-","")}"
  try{
   val generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
   generator.init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
   val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,generator.generateKey());cipher.updateAAD(folder.toByteArray())
   return JSONObject().put("alias",alias).put("nonce",Base64.encodeToString(cipher.iv,Base64.NO_WRAP)).put("cipher",Base64.encodeToString(cipher.doFinal(bytes),Base64.NO_WRAP))
  }catch(e:Exception){runCatching{store().deleteEntry(alias)};throw e}
 }
 override fun unwrap(folder:String,value:JSONObject):ByteArray{
  val name=alias(folder,value);require(value.getString("cipher").length<=24000)
  val nonce=Base64.decode(value.getString("nonce"),Base64.NO_WRAP);require(nonce.size==12)
  val key=store().getKey(name,null) as? SecretKey?:error("Session recovery key is unavailable; enter the folder password")
  val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key,GCMParameterSpec(128,nonce));cipher.updateAAD(folder.toByteArray())
  return cipher.doFinal(Base64.decode(value.getString("cipher"),Base64.NO_WRAP))
 }
 override fun erase(folder:String,value:JSONObject){store().deleteEntry(alias(folder,value))}
}
