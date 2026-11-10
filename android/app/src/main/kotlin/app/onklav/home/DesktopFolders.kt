package app.onklav.home

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal data class DesktopFolder(val id:String,val name:String,val apps:List<String>){
 val letter get()=name.trim().codePoints().findFirst().orElse('?'.code).let{String(Character.toChars(it)).uppercase(java.util.Locale.getDefault())}
}
/** Folder membership is independent of the currently installed/visible catalog. */
internal data class DesktopFolders(val folders:List<DesktopFolder>){
 fun desktop(catalog:List<String>)=folders.map{it.id}+catalog.filter{app->folders.none{app in it.apps}}
 fun add(name:String):DesktopFolders {val n=validName(name);require(folders.size<200){"Too many folders"};return copy(folders=folders+DesktopFolder("folder-"+UUID.randomUUID().toString().replace("-",""),n,emptyList()))}
 fun rename(id:String,name:String)=copy(folders=folders.map{if(it.id==id)it.copy(name=validName(name))else it})
 fun delete(id:String)=copy(folders=folders.filterNot{it.id==id})
 fun put(folder:String,app:String,index:Int=Int.MAX_VALUE):DesktopFolders {
  require(validApp(app)&&folders.any{it.id==folder})
  return copy(folders=folders.map{f->val rest=f.apps.filterNot{it==app};if(f.id==folder)f.copy(apps=rest.toMutableList().apply{add(index.coerceIn(0,size),app)})else f.copy(apps=rest)})
 }
 fun takeOut(app:String)=copy(folders=folders.map{it.copy(apps=it.apps.filterNot{n->n==app})})
 fun encode()=JSONObject().put("version",1).put("folders",JSONArray().apply{folders.forEach{f->put(JSONObject().put("id",f.id).put("name",f.name).put("apps",JSONArray(f.apps)))}}).toString()
 companion object {
  private val idPattern=Regex("[a-z][a-z0-9_-]{0,64}")
  private fun validApp(id:String)=idPattern.matches(id)&&!id.startsWith("folder-")
  fun validName(name:String):String=name.trim().also{require(it.isNotEmpty()&&it.length<=40&&it.none{c->c.isISOControl()}){"Enter a folder name (1–40 characters)"}}
  val utilityMoves=listOf("calc","vault","convert","reader","walkie","flashlight","power")
  fun defaults()=DesktopFolders(listOf(
   DesktopFolder("folder-utilities","Utilities",listOf("compress","docuview","authentic","formatswap")+utilityMoves),
   DesktopFolder("folder-create","Create",listOf("videoeditor","audioeditor","photo","voicerecorder","screenrecorder","codeedit"))
  ))
  fun read(raw:String?):DesktopFolders {
   if(raw==null)return defaults()
   require(raw.length<=262144){"Folder data is too large"};val json=JSONObject(raw);require(json.getInt("version")==1);val list=json.getJSONArray("folders");require(list.length()<=200)
   val seen=mutableSetOf<String>();val ids=mutableSetOf<String>();val result=mutableListOf<DesktopFolder>()
   for(i in 0 until list.length()){val f=list.getJSONObject(i);val id=f.getString("id");require(idPattern.matches(id)&&id.startsWith("folder-")&&ids.add(id));val members=f.getJSONArray("apps");require(members.length()<=960);val apps=mutableListOf<String>();for(j in 0 until members.length()){val app=members.getString(j);require(validApp(app));if(app!="makemusic"&&seen.add(app))apps+=app};result+=DesktopFolder(id,validName(f.getString("name")),apps)}
   return DesktopFolders(result)
  }
 }
}
