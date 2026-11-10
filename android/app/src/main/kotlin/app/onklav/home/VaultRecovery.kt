package app.onklav.home

import java.io.*
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

/** A second, answer-derived encrypted key wrap, carried inside the existing opaque Vault header.
 * Questions are public; answers and their derived secret are never persisted. */
internal object VaultRecovery {
 val questions=listOf(
  listOf("What is your favorite snack food or candy?","What is your favorite food?","What is your least favorite food?"),
  listOf("What is your favorite movie?","What is your favorite book?","What is your favorite musician/band?"),
  listOf("What was your first car?","What was your first pet's name?","What was your childhood best friend's name?")
 )
 private val magic="OKVREC01".toByteArray(Charsets.US_ASCII)
 data class Bundle(val choices:List<Int>,val password:ByteArray,val recovery:FolderVaultCrypto.Header)
 fun choices(value:List<Int>){require(value.size==3&&value.all{it in 0..2}){"Choose all three questions"}}
 private fun normalized(value:CharArray)=Normalizer.normalize(String(value),Normalizer.Form.NFKC).trim().lowercase(Locale.ROOT).replace(Regex("\\s+")," ")
 fun validAnswer(value:String)=value.length in 1..256&&value.isNotBlank()
 fun secret(selected:List<Int>,answers:List<CharArray>):CharArray {
  choices(selected);require(answers.size==3&&answers.all{it.size in 1..256&&it.any{c->!c.isWhitespace()}}){"Answer all three questions"}
  val bytes=ByteArrayOutputStream();DataOutputStream(bytes).use{out->out.writeUTF("OnKlav Vault recovery v1");selected.forEachIndexed{i,q->out.writeInt(q);out.writeUTF(normalized(answers[i]))}}
  val raw=bytes.toByteArray();return try{MessageDigest.getInstance("SHA-256").digest(raw).joinToString(""){"%02x".format(it)}.toCharArray()}finally{raw.fill(0)}
 }
 fun read(h:FolderVaultCrypto.Header):Bundle? {
  if(!h.wrappedKey.take(magic.size).toByteArray().contentEquals(magic))return null
  val input=DataInputStream(ByteArrayInputStream(h.wrappedKey));val signature=ByteArray(magic.size);input.readFully(signature)
  val selected=List(3){input.readInt()};choices(selected)
  fun field():ByteArray{val n=input.readInt();require(n in 32..16384);return ByteArray(n).also(input::readFully)}
  val password=field();val raw=field();val stream=ByteArrayInputStream(raw);val recovery=FolderVaultCrypto.readHeader(stream)
  require(stream.available()==0&&input.available()==0&&recovery.folder==h.folder){"Invalid recovery header"}
  return Bundle(selected,password,recovery)
 }
 fun passwordHeader(h:FolderVaultCrypto.Header)=read(h)?.let{h.copy(wrappedKey=it.password)}?:h
 fun pack(h:FolderVaultCrypto.Header,b:Bundle):FolderVaultCrypto.Header {
  choices(b.choices);val bytes=ByteArrayOutputStream();DataOutputStream(bytes).use{out->out.write(magic);b.choices.forEach(out::writeInt);out.writeInt(b.password.size);out.write(b.password);val r=FolderVaultCrypto.headerBytes(b.recovery);out.writeInt(r.size);out.write(r)}
  return h.copy(wrappedKey=bytes.toByteArray()).also{require(it.wrappedKey.size<=16384)}
 }
 fun add(h:FolderVaultCrypto.Header,key:ByteArray,selected:List<Int>,answers:List<CharArray>):FolderVaultCrypto.Header {
  val secret=secret(selected,answers)
  return try{val plain=passwordHeader(h);val recovery=FolderVaultCrypto.changePassword(plain,key,secret);pack(plain,Bundle(selected.toList(),plain.wrappedKey,recovery))}finally{secret.fill('\u0000')}
 }
 fun rewrap(h:FolderVaultCrypto.Header,key:ByteArray,password:CharArray):FolderVaultCrypto.Header {
  val bundle=read(h);val next=FolderVaultCrypto.changePassword(passwordHeader(h),key,password)
  return if(bundle==null)next else pack(next,bundle.copy(password=next.wrappedKey))
 }
}
