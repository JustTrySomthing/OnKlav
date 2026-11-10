package app.onklav.home

import android.content.Context
import android.util.AtomicFile
import org.json.*
import java.io.File
import java.util.UUID
import kotlin.math.*

internal data class MapsPlace(val id:String,val name:String,val lat:Double,val lon:Double,val address:String=""){
    init{require(name.isNotBlank()&&name.length<=240&&lat.isFinite()&&lon.isFinite()&&lat in -85.05112878..85.05112878&&lon in -180.0..180.0)}
    fun json()=JSONObject().put("id",id).put("name",name).put("lat",lat).put("lon",lon).put("address",address)
    companion object{fun parse(j:JSONObject)=MapsPlace(j.getString("id").take(160),j.getString("name").take(240),j.getDouble("lat"),j.getDouble("lon"),j.optString("address").take(500))}
}
internal data class MapsStep(val text:String,val distance:Double,val lat:Double,val lon:Double){fun json()=JSONObject().put("text",text).put("distance",distance).put("lat",lat).put("lon",lon)}
internal data class MapsRoute(val id:String,val name:String,val from:MapsPlace,val to:MapsPlace,val mode:String,val distance:Double,val seconds:Double,val points:List<Pair<Double,Double>>,val steps:List<MapsStep>,val savedAt:Long=System.currentTimeMillis()){
    fun json()=JSONObject().put("id",id).put("name",name).put("from",from.json()).put("to",to.json()).put("mode",mode).put("distance",distance).put("seconds",seconds).put("points",JSONArray().apply{points.forEach{put(JSONArray(listOf(it.first,it.second)))}}).put("steps",JSONArray().apply{steps.forEach{put(it.json())}}).put("saved_at",savedAt)
    companion object{fun parse(j:JSONObject):MapsRoute{
        val p=j.getJSONArray("points");val s=j.getJSONArray("steps");require(p.length() in 2..12000&&s.length()<=2000)
        val points=(0 until p.length()).map{val a=p.getJSONArray(it);MapsMath.valid(a.getDouble(0),a.getDouble(1));a.getDouble(0) to a.getDouble(1)}
        val steps=(0 until s.length()).map{val a=s.getJSONObject(it);MapsMath.valid(a.getDouble("lat"),a.getDouble("lon"));MapsStep(a.getString("text").take(500),a.getDouble("distance").also{v->require(v.isFinite()&&v>=0)},a.getDouble("lat"),a.getDouble("lon"))}
        val mode=j.getString("mode");require(mode in listOf("Drive","Walk","Cycle"))
        return MapsRoute(j.getString("id").take(160),j.getString("name").take(120),MapsPlace.parse(j.getJSONObject("from")),MapsPlace.parse(j.getJSONObject("to")),mode,j.getDouble("distance").also{require(it.isFinite()&&it>=0)},j.getDouble("seconds").also{require(it.isFinite()&&it>=0)},points,steps,j.getLong("saved_at"))
    }}
}
internal data class MapsCamera(val lat:Double=39.5,val lon:Double=-98.35,val zoom:Double=3.0)
internal object MapsMath{
    fun valid(lat:Double,lon:Double){require(lat.isFinite()&&lon.isFinite()&&lat in -85.05112878..85.05112878&&lon in -180.0..180.0){"This location is outside the map area"}}
    fun unproject(x:Double,y:Double,z:Int):Pair<Double,Double>{val world=2.0.pow(z);return Math.toDegrees(atan(sinh(PI*(1-2*y.coerceIn(0.0,world)/world)))) to ((x/world*360)%360+360)%360-180}
    fun move(c:MapsCamera,dx:Double,dy:Double,zoomFactor:Double=1.0):MapsCamera{val z=floor(c.zoom).toInt();val p=SdkMapTiles.project(c.lat,c.lon,z);val tile=256*2.0.pow(c.zoom-z);val ll=unproject(p.first-dx/tile,p.second-dy/tile,z);return MapsCamera(ll.first,ll.second,(c.zoom+ln(zoomFactor.coerceIn(.5,2.0))/ln(2.0)).coerceIn(2.0,19.0))}
    fun distance(a:MapsPlace,b:MapsPlace):Double{val p1=Math.toRadians(a.lat);val p2=Math.toRadians(b.lat);val d1=p2-p1;val d2=Math.toRadians(b.lon-a.lon);return 6371000*2*asin(sqrt((sin(d1/2).pow(2)+cos(p1)*cos(p2)*sin(d2/2).pow(2)).coerceIn(0.0,1.0)))}
    fun fit(points:List<Pair<Double,Double>>,width:Int=360,height:Int=400):MapsCamera{if(points.isEmpty())return MapsCamera();val anchor=points.first().second;val lon=points.map{anchor+SdkMapTiles.wrappedDelta(it.second,anchor,360.0)};val mid=(lon.min()+lon.max())/2;val lat=(points.minOf{it.first}+points.maxOf{it.first})/2
        for(z in 18 downTo 2){val p=points.map{SdkMapTiles.project(it.first,it.second,z)};val xs=p.map{SdkMapTiles.wrappedDelta(it.first,SdkMapTiles.project(lat,mid,z).first,2.0.pow(z))};if((xs.max()-xs.min())*256<width*.7&&(p.maxOf{it.second}-p.minOf{it.second})*256<height*.7)return MapsCamera(lat,((mid+180)%360+360)%360-180,z.toDouble())};return MapsCamera(lat,((mid+180)%360+360)%360-180,2.0)}
    fun distanceText(m:Double,metric:Boolean)=if(metric){if(m<1000)"${m.roundToInt()} m"else "${"%.1f".format(m/1000)} km"}else{if(m<160.934)"${(m*3.28084).roundToInt()} ft"else "${"%.1f".format(m/1609.344)} mi"}
    fun durationText(s:Double):String{val min=max(1,(s/60).roundToInt());return if(min<60)"$min min"else "${min/60} hr ${min%60} min"}
}
internal object MapsParser{
    fun photon(j:JSONObject):List<MapsPlace>{val features=j.getJSONArray("features");require(features.length()<=100);return (0 until features.length()).mapNotNull{runCatching{val f=features.getJSONObject(it);val p=f.getJSONObject("properties");val c=f.getJSONObject("geometry").getJSONArray("coordinates");val address=listOf("housenumber","street","city","state","postcode","country").map{p.optString(it)}.filter{it.isNotBlank()}.distinct().joinToString(", ");val name=p.optString("name").ifBlank{p.optString("street").ifBlank{p.optString("city").ifBlank{"Selected place"}}};MapsPlace("${p.optString("osm_type")}${p.optLong("osm_id")}:${c.getDouble(1)},${c.getDouble(0)}",name.take(240),c.getDouble(1),c.getDouble(0),address.take(500))}.getOrNull()}.distinctBy{it.id}}
    fun route(j:JSONObject,from:MapsPlace,to:MapsPlace,mode:String):MapsRoute{
        require(j.optString("code")=="Ok"){"No route found. Try a nearby start or a different travel mode."};val r=j.getJSONArray("routes").getJSONObject(0);val coords=r.getJSONObject("geometry").getJSONArray("coordinates");require(coords.length() in 2..12000){"This route is too detailed. Try a shorter route."}
        val points=(0 until coords.length()).map{val a=coords.getJSONArray(it);val lat=a.getDouble(1);val lon=a.getDouble(0);MapsMath.valid(lat,lon);lat to lon};val steps=mutableListOf<MapsStep>();val legs=r.getJSONArray("legs")
        for(l in 0 until legs.length()){val a=legs.getJSONObject(l).getJSONArray("steps");for(i in 0 until a.length()){require(steps.size<2000){"This route has too many instructions. Try a shorter route."};val s=a.getJSONObject(i);val m=s.getJSONObject("maneuver");val at=m.getJSONArray("location");MapsMath.valid(at.getDouble(1),at.getDouble(0));val type=m.optString("type");val modifier=m.optString("modifier").replace("_"," ");val road=s.optString("name").ifBlank{s.optString("ref")};val action=when(type){"depart"->"Start";"arrive"->"Arrive at your destination";"roundabout","rotary"->if(m.optInt("exit",0)>0)"At the roundabout, take exit ${m.getInt("exit")}"else "Enter the roundabout";"roundabout turn"->"At the roundabout, turn $modifier";"exit roundabout","exit rotary"->"Exit the roundabout";"merge"->"Merge $modifier";"fork"->"Keep $modifier";"off ramp"->"Take the $modifier exit";"on ramp"->"Take the $modifier ramp";"turn","end of road"->"Turn $modifier";"continue"->if(modifier=="uturn")"Make a U-turn"else "Continue $modifier";else->"Continue $modifier"}.trim();val text=action+(if(road.isNotBlank()&&type!="arrive")" onto $road"else "");steps.add(MapsStep(text.take(500),s.getDouble("distance").also{require(it.isFinite()&&it>=0)},at.getDouble(1),at.getDouble(0)))}}
        val metres=r.getDouble("distance");val seconds=r.getDouble("duration");require(metres.isFinite()&&seconds.isFinite()&&metres>=0&&seconds>=0);return MapsRoute(UUID.randomUUID().toString(),"${from.name} → ${to.name}".take(120),from,to,mode,metres,seconds,points,steps)
    }
}
internal data class MapsSaved(val places:List<MapsPlace> = emptyList(),val routes:List<MapsRoute> = emptyList())
internal object MapsStore{
    fun read(c:Context):MapsSaved{val f=AtomicFile(File(c.filesDir,"maps/saved.json"));if(!f.baseFile.exists()&&!File(f.baseFile.path+".bak").exists())return MapsSaved();val j=f.openRead().use{input->require(input.available()<=12_000_000);JSONObject(input.readBytes().toString(Charsets.UTF_8))};val p=j.optJSONArray("places")?:JSONArray();val r=j.optJSONArray("routes")?:JSONArray();require(p.length()<=300&&r.length()<=50);return MapsSaved((0 until p.length()).map{MapsPlace.parse(p.getJSONObject(it))},(0 until r.length()).map{MapsRoute.parse(r.getJSONObject(it))})}
    fun save(c:Context,s:MapsSaved){check(mapsAccess(c));require(s.places.size<=300&&s.routes.size<=50){"Saved list is full. Remove an item first."};val j=JSONObject().put("places",JSONArray().apply{s.places.forEach{put(it.json())}}).put("routes",JSONArray().apply{s.routes.forEach{put(it.json())}});val bytes=j.toString().toByteArray();require(bytes.size<=12_000_000){"Saved routes are full. Remove an older route first."};val file=AtomicFile(File(c.filesDir,"maps/saved.json"));file.baseFile.parentFile!!.mkdirs();val out=file.startWrite();try{out.write(bytes);file.finishWrite(out)}catch(e:Exception){file.failWrite(out);throw e}}
}
internal object MapsOffline{
    @Volatile private var data:JSONObject?=null
    fun search(c:Context,query:String):List<MapsPlace>{val q=query.trim();if(q.length<2)return emptyList();q.split(',').takeIf{it.size==2}?.let{a->val lat=a[0].trim().toDoubleOrNull();val lon=a[1].trim().toDoubleOrNull();if(lat!=null&&lon!=null){return runCatching{listOf(MapsPlace("coordinates","${"%.5f".format(lat)}, ${"%.5f".format(lon)}",lat,lon))}.getOrDefault(emptyList())}}
        val j=data?:synchronized(this){data?:c.assets.open("maps/places.json").bufferedReader().use{JSONObject(it.readText())}.also{data=it}}
        if(q.matches(Regex("\\d{5}(-\\d{4})?"))){val zip=q.take(5);return j.getString("postal").lineSequence().firstOrNull{it.startsWith("$zip\t")}?.split('\t')?.let{a->listOf(MapsPlace("zip-$zip","${a[1]}, ${a[2]} $zip",a[3].toDouble(),a[4].toDouble(),"ZIP code area"))}?:emptyList()}
        val tokens=q.lowercase().split(Regex("[ ,]+")).filter{it.isNotBlank()};return j.getString("cities").lineSequence().filter{line->val a=line.split('\t');a.size==10&&tokens.all{t->listOf(1,2,3,4,8,9).any{a[it].contains(t,true)}}}.take(12).map{line->val a=line.split('\t');MapsPlace("city-${a[0]}","${a[1]}, ${a[9]} · ${a[3]}",a[5].toDouble(),a[6].toDouble())}.toList()
    }
}
