package app.onklav.home

import java.io.InputStream
import java.io.OutputStream
import java.security.*
import java.security.cert.Certificate
import java.util.Collections
import java.util.Date
import org.junit.rules.ExternalResource

/** Desktop cleanup fixture only. It cannot create, store or decrypt secrets.
 * Vault crypto/isolation use SdkSecretsTest; real Keystore acceptance uses Android hardware. */
class EmptyKeyStoreRule:ExternalResource(){
 private val provider=object:Provider("OnKlavEmptyCleanupStore",1.0,"Empty desktop cleanup fixture"){
  init{put("KeyStore.AndroidKeyStore",EmptyCleanupKeyStore::class.java.name)}
 }
 override fun before(){check(Security.getProvider(provider.name)==null);check(Security.addProvider(provider)>0)}
 override fun after(){Security.removeProvider(provider.name)}
}

class EmptyCleanupKeyStore:KeyStoreSpi(){
 override fun engineLoad(stream:InputStream?,password:CharArray?){}
 override fun engineAliases():java.util.Enumeration<String> = Collections.emptyEnumeration()
 override fun engineContainsAlias(alias:String)=false
 override fun engineSize()=0
 override fun engineDeleteEntry(alias:String){}
 override fun engineGetKey(alias:String,password:CharArray?):Key?=null
 override fun engineGetCertificateChain(alias:String):Array<Certificate>?=null
 override fun engineGetCertificate(alias:String):Certificate?=null
 override fun engineGetCreationDate(alias:String):Date?=null
 override fun engineIsKeyEntry(alias:String)=false
 override fun engineIsCertificateEntry(alias:String)=false
 override fun engineGetCertificateAlias(cert:Certificate):String?=null
 override fun engineSetKeyEntry(alias:String,key:Key,password:CharArray?,chain:Array<out Certificate>?){error("Cleanup fixture cannot store keys")}
 override fun engineSetKeyEntry(alias:String,key:ByteArray,chain:Array<out Certificate>?){error("Cleanup fixture cannot store keys")}
 override fun engineSetCertificateEntry(alias:String,cert:Certificate){error("Cleanup fixture cannot store certificates")}
 override fun engineStore(stream:OutputStream?,password:CharArray?){error("Cleanup fixture cannot export keys")}
}
