package app.onklav.home

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Host-private account records. The guest receives only opaque handles/provider labels. */
internal class SdkAccountVault(private val store:SdkSecretStore){
 private fun identity(request:JSONObject)=request.getJSONObject("owner").let{it.getString("id") to it.getString("publisher")}
 private fun canonical(v:Any?):String=when(v){is JSONObject->v.keys().asSequence().toList().sorted().joinToString(",","{","}"){JSONObject.quote(it)+":"+canonical(v.get(it))};is JSONArray->(0 until v.length()).joinToString(",","[","]"){canonical(v.get(it))};is String->JSONObject.quote(v);is Number->JSONObject.numberToString(v);is Boolean->v.toString();else->if(v==null||v===JSONObject.NULL)"null"else error("Unsupported account metadata")}
 private fun digest(provider:JSONObject)=MessageDigest.getInstance("SHA-256").digest(canonical(provider).toByteArray()).joinToString(""){"%02x".format(it)}
 private fun read(request:JSONObject,authorize:()->Unit):JSONObject {
  val(id,publisher)=identity(request);val value=store.run(id,publisher,"secrets.get",JSONObject().put("key","accounts"),authorize)
  return if(value.isNull("value"))JSONObject()else JSONObject(value.getString("value"))
 }
 private fun write(request:JSONObject,records:JSONObject,authorize:()->Unit){val(id,publisher)=identity(request);store.run(id,publisher,"secrets.set",JSONObject().put("key","accounts").put("value",records.toString()),authorize)}
 private fun publicRow(handle:String,row:JSONObject)=JSONObject().put("account",handle).put("provider",row.getString("provider")).put("label",row.getString("label"))
 fun connect(request:JSONObject,provider:JSONObject,tokens:JSONObject,authorize:()->Unit):JSONObject=AppSdk.carrierEgress{
  authorize();val signed=request.getJSONArray("providers");check((0 until signed.length()).any{canonical(signed.getJSONObject(it))==canonical(provider)}){"Account provider changed"}
  val records=read(request,authorize);check(records.length()<8){"Keep up to eight connected accounts per app"}
  val handle=UUID.randomUUID().toString();val row=JSONObject().put("provider",provider.getString("id")).put("label",provider.getString("label")).put("policy",digest(provider)).put("tokens",JSONObject(tokens.toString()))
  records.put(handle,row);write(request,records,authorize);publicRow(handle,row)
 }
 fun list(request:JSONObject,authorize:()->Unit):JSONArray=AppSdk.carrierEgress{
  authorize();val records=read(request,authorize);val signed=request.getJSONArray("providers");val providers=(0 until signed.length()).map{signed.getJSONObject(it)}.associateBy{it.getString("id")}
  val out=JSONArray();var changed=false
  for(handle in records.keys().asSequence().toList()){
   val row=records.getJSONObject(handle);val p=providers[row.getString("provider")]
   if(p==null||row.getString("policy")!=digest(p)){records.remove(handle);changed=true}else out.put(publicRow(handle,row))
  }
  if(changed)write(request,records,authorize);authorize();out
 }
 internal fun get(request:JSONObject,authorize:()->Unit):JSONObject=AppSdk.carrierEgress{
  authorize();val h=request.getJSONObject("args").getString("account");require(h.matches(Regex("[a-f0-9-]{36}")))
  val row=read(request,authorize).optJSONObject(h)?:error("Connect this account again")
  val providers=request.getJSONArray("providers");val p=(0 until providers.length()).map{providers.getJSONObject(it)}.singleOrNull{it.getString("id")==row.getString("provider")}?:error("Account provider changed")
  check(row.getString("policy")==digest(p)){"Account provider changed; connect again"};authorize();JSONObject(row.toString())
 }
 internal fun refresh(request:JSONObject,previous:JSONObject,tokens:JSONObject,authorize:()->Unit):JSONObject=AppSdk.carrierEgress{
  val live=get(request,authorize);check(live.toString()==previous.toString()){"Account changed during refresh"}
  val records=read(request,authorize);val h=request.getJSONObject("args").getString("account");live.put("tokens",tokens);records.put(h,live);write(request,records,authorize);publicRow(h,live)
 }
 internal fun revocationProvider(request:JSONObject,row:JSONObject):JSONObject?{
  val providers=request.getJSONArray("providers");return (0 until providers.length()).map{providers.getJSONObject(it)}.firstOrNull{it.getString("id")==row.getString("provider")&&digest(it)==row.getString("policy")}
 }
 internal fun disconnect(request:JSONObject,authorize:()->Unit):JSONObject?=AppSdk.carrierEgress{
  authorize();val records=read(request,authorize);val h=request.getJSONObject("args").getString("account");val removed=records.optJSONObject(h)
  if(removed!=null){records.remove(h);write(request,records,authorize)};authorize();removed
 }
 fun deleteApp(id:String)=store.deleteApp(id)
 companion object {
  private val keys=AndroidSecretKeys()
  fun forContext(c:Context)=SdkAccountVault(SdkSecretStore(File(c.noBackupFilesDir,"sdk-accounts"),keys,"accounts"))
  fun clear(c:Context,id:String)=AppSdk.carrierEgress{forContext(c).deleteApp(id)}
 }
}
