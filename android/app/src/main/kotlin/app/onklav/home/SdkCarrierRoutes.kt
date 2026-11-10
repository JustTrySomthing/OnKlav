package app.onklav.home

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** App/publisher routing is not sender authentication; Android supplies the originating number. */
internal object SdkCarrierRoutes {
 fun route(owner:JSONObject):String{
  val id=owner.getString("id");val publisher=owner.getString("publisher")
  require(id.length<=128&&id.matches(Regex("[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)+"))&&publisher.matches(Regex("[a-f0-9]{64}")))
  val digest=MessageDigest.getInstance("SHA-256").digest("onklav.carrier.v2\u0000$id\u0000$publisher".toByteArray())
  val hex="0123456789abcdef"
  return buildString(32){for(index in 0 until 16){val byte=digest[index].toInt() and 255;append(hex[byte ushr 4]);append(hex[byte and 15])}}
 }
 fun owners(c:Context):List<JSONObject>{val rows=AppSdk.sync(c,JSONObject().put("action","walkie_routes"))as JSONArray;return List(rows.length()){rows.getJSONObject(it)}}
 fun legacyOwner(c:Context):JSONObject{
  val rows=AppSdk.sync(c,JSONObject().put("action","list"))as JSONArray
  val manifest=(0 until rows.length()).map{rows.getJSONObject(it).getJSONObject("manifest")}.firstOrNull{it.getString("id")=="app.onklav.walkie"}?:error("Walkie identity is unavailable")
  return JSONObject().put("id",manifest.getString("id")).put("publisher",manifest.getString("publisher"))
 }
 fun builtin(c:Context)=owners(c).firstOrNull{it.getString("id")=="app.onklav.walkie"}?:error("Walkie access is off")
 fun encode(owner:JSONObject,wire:String):String{
  require(wire.startsWith("!OKW1|")||wire.startsWith("!OKV1|"))
  if(owner.getString("id")=="app.onklav.walkie")return wire
  return (if(wire.startsWith("!OKW1|"))"!OKW2|"else"!OKV2|")+route(owner)+"|"+wire.substring(6)
 }
 fun decode(raw:String,owners:List<JSONObject>):Pair<JSONObject,String>?{
  val wire=if(listOf("!OKW1@","!OKW2@","!OKV1@","!OKV2@").any(raw::startsWith))
   raw.replace('@','|') else raw
  if(wire.length>420)return null
  if(wire.startsWith("!OKW1|")||wire.startsWith("!OKV1|"))return owners.firstOrNull{it.getString("id")=="app.onklav.walkie"}?.let{it to wire}
  if(wire.length>420||(!wire.startsWith("!OKW2|")&&!wire.startsWith("!OKV2|")))return null
  val parts=wire.split('|',limit=3);if(parts.size!=3||!parts[1].matches(Regex("[a-f0-9]{32}")))return null
  val matches=owners.filter{route(it)==parts[1]};if(matches.size!=1)return null
  return matches.single()to(if(parts[0]=="!OKW2")"!OKW1|"else"!OKV1|")+parts[2]
 }
}
