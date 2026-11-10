package app.onklav.home

import android.content.Context
import org.json.JSONObject

internal class PdfPositionWriteException:java.io.IOException("PDF page position could not be saved")
internal object SdkPdfPositions {
 private fun prefs(c:Context)=c.getSharedPreferences("sdk-pdf-positions",0)
 private fun prefix(id:String)=AppSdk.desktopId(id)+"."
 private fun key(d:JSONObject)=prefix(d.getJSONObject("owner").getString("id"))+d.getJSONObject("owner").getString("publisher")+"."+d.getJSONObject("file").getString("sha256")
 fun read(c:Context,d:JSONObject,pages:Int):Int=runCatching{JSONObject(prefs(c).getString(key(d),null)?:return 0).getInt("page").takeIf{it in 0 until pages}?:0}.getOrDefault(0)
 fun save(c:Context,d:JSONObject,page:Int,pages:Int,authorize:()->Unit)=AppSdk.carrierEgress{
  require(page in 0 until pages);authorize();val p=prefs(c);val id=d.getJSONObject("owner").getString("id");val own=p.all.filterKeys{it.startsWith(prefix(id))};val edit=p.edit()
  if(key(d)!in own&&own.size>=64){val oldest=own.minByOrNull{(_,v)->runCatching{JSONObject(v as String).getLong("time")}.getOrDefault(0)};oldest?.let{edit.remove(it.key)}}
  if(!edit.putString(key(d),JSONObject().put("page",page).put("time",System.currentTimeMillis()).toString()).commit())throw PdfPositionWriteException()
 }
 fun clearApp(c:Context,id:String)=AppSdk.carrierEgress{val p=prefs(c);val e=p.edit();p.all.keys.filter{it.startsWith(prefix(id))}.forEach{e.remove(it)};check(e.commit())}
}
