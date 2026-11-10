package app.onklav.home

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.*
import android.os.*
import android.content.pm.PackageManager
import android.Manifest
import androidx.core.content.ContextCompat
import androidx.compose.runtime.*
import androidx.lifecycle.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*
import okhttp3.*
import org.json.*
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.math.*
import kotlin.coroutines.resume

internal fun mapsAccess(c:Context,cap:String="storage.private"):Boolean=runCatching{val rows=AppSdk.sync(c,JSONObject().put("action","list")) as JSONArray;(0 until rows.length()).any{val r=rows.getJSONObject(it);r.optBoolean("installed")&&r.optJSONObject("manifest")?.optString("id")=="app.onklav.maps"&&r.optJSONArray("grants")?.let{a->(0 until a.length()).any{a.optString(it)==cap}}==true}}.getOrDefault(false)
internal object MapsNet{
    val origins=listOf("https://tile.openstreetmap.org","https://photon.komoot.io","https://routing.openstreetmap.de")
    private var cached:OkHttpClient?=null
    private fun client(c:Context):OkHttpClient=synchronized(this){cached?:OkHttpClient.Builder().cache(Cache(File(c.cacheDir,"maps-http"),32L*1024*1024)).connectTimeout(10,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS).callTimeout(35,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).dns(object:Dns{override fun lookup(hostname:String)=SdkFeedService.publicAddresses(hostname)}).build().also{cached=it}}
    private val gates=origins.associateWith{Mutex()};private val last=mutableMapOf<String,Long>()
    suspend fun fetch(c:Context,url:String,limit:Int=2_000_000,tile:Boolean=false):ByteArray=withContext(Dispatchers.IO){
        fun authorize(){check(mapsAccess(c)&&mapsAccess(c,"network.http")){"Maps network access is off. Restore it in Settings > Apps > Maps."}}
        authorize();val origin=SdkHttpPolicy.origin(url);require(origin in origins)
        if(!tile)gates.getValue(origin).withLock{val wait=(last[origin]?:0)+1100-SystemClock.elapsedRealtime();if(wait>0)delay(wait);last[origin]=SystemClock.elapsedRealtime()}
        val operation=JSONObject().put("owner",JSONObject().put("id","app.onklav.maps")).put("origins",JSONArray(origins)).put("args",JSONObject().put("url",url))
        val req=SdkHttpPolicy.request(operation).newBuilder().header("User-Agent","OnKlav/0.95 (app.onklav.maps; https://onklav.app)").build();val call=client(c).newCall(req)
        coroutineScope{val watcher=launch(start=CoroutineStart.UNDISPATCHED){try{awaitCancellation()}finally{call.cancel()}}
            try{call.execute().use{response->authorize();require(response.isSuccessful){when(response.code){429->"The map service is busy. Wait a moment and try again.";503,502,504->"The map service is temporarily unavailable. Try again.";else->"Unable to load map data (${response.code}). Try again."}}
                val body=requireNotNull(response.body);require(body.contentLength()<=limit){"This response is too large. Try a shorter route."};val bytes=java.io.ByteArrayOutputStream();body.byteStream().use{input->val b=ByteArray(16384);var at=0L;while(true){ensureActive();val now=System.nanoTime();if(now-at>500_000_000){authorize();at=now};val n=input.read(b);if(n<0)break;require(bytes.size()+n<=limit){"This response is too large. Try a shorter route."};bytes.write(b,0,n)}};authorize();bytes.toByteArray()}
            }finally{watcher.cancel();call.cancel()}
        }
    }
    suspend fun json(c:Context,url:String,ttl:Long=86400000):JSONObject{
        check(mapsAccess(c));val dir=File(c.filesDir,"maps/queries");val key=MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString(""){"%02x".format(it)};val f=File(dir,"$key.json")
        withContext(Dispatchers.IO){if(f.exists()&&System.currentTimeMillis()-f.lastModified() in 0..ttl)runCatching{JSONObject(f.readText())}.getOrNull()?.let{return@withContext it} else null}?.let{return it}
        val j=JSONObject(fetch(c,url).toString(Charsets.UTF_8));withContext(Dispatchers.IO){check(mapsAccess(c));dir.mkdirs();val tmp=File(dir,"$key.tmp");tmp.writeText(j.toString());check(tmp.renameTo(f));dir.listFiles()?.sortedByDescending{it.lastModified()}?.drop(80)?.forEach{it.delete()}};return j
    }
    fun searchUrl(q:String)=HttpUrl.Builder().scheme("https").host("photon.komoot.io").addPathSegment("api").addPathSegment("").addQueryParameter("q",q).addQueryParameter("limit","12").addQueryParameter("lang","en").build().toString()
    val categories=linkedMapOf("Gas" to "osm.amenity.fuel","Hotels" to "osm.tourism.hotel","Restaurants" to "osm.amenity.restaurant","Cafés" to "osm.amenity.cafe","Groceries" to "osm.shop.supermarket","Pharmacy" to "osm.amenity.pharmacy","Parking" to "osm.amenity.parking","EV charging" to "osm.amenity.charging_station")
    fun nearbyUrl(category:String,center:MapsPlace,radius:Int):String{val lat=radius/111.0;val lon=radius/(111.0*cos(Math.toRadians(center.lat)).coerceAtLeast(.02));val builder=HttpUrl.Builder().scheme("https").host("photon.komoot.io").addPathSegment("api").addPathSegment("").addQueryParameter("include",categories.getValue(category)).addQueryParameter("lat",center.lat.toString()).addQueryParameter("lon",center.lon.toString()).addQueryParameter("limit","40").addQueryParameter("lang","en")
        if(center.lon-lon>= -180&&center.lon+lon<=180)builder.addQueryParameter("bbox","${center.lon-lon},${(center.lat-lat).coerceAtLeast(-85.0)},${center.lon+lon},${(center.lat+lat).coerceAtMost(85.0)}")
        return builder.build().toString()}
    fun routeUrl(from:MapsPlace,to:MapsPlace,mode:String):String{val profile=when(mode){"Drive"->"car";"Walk"->"foot";"Cycle"->"bike";else->error("Choose a travel mode")};return "https://routing.openstreetmap.de/routed-$profile/route/v1/driving/${from.lon},${from.lat};${to.lon},${to.lat}?steps=true&geometries=geojson&overview=simplified&alternatives=false"}
    fun clear(c:Context){synchronized(this){client(c).cache?.evictAll()};File(c.filesDir,"maps/queries").deleteRecursively()}
}
internal data class MapsTile(val z:Int,val x:Int,val y:Int)
internal class MapsModel(app:Application):AndroidViewModel(app){
    var camera by mutableStateOf(MapsCamera());private set
    var selected by mutableStateOf<MapsPlace?>(null);private set
    var results by mutableStateOf<List<MapsPlace>>(emptyList());private set
    var resultTitle by mutableStateOf("Search results");private set
    var saved by mutableStateOf(MapsSaved());private set
    var route by mutableStateOf<MapsRoute?>(null);private set
    var from by mutableStateOf<MapsPlace?>(null);private set
    var to by mutableStateOf<MapsPlace?>(null);private set
    var mode by mutableStateOf("Drive");private set
    var current by mutableStateOf<MapsPlace?>(null);private set
    var accuracy by mutableStateOf(0f);private set
    var tiles by mutableStateOf<Map<MapsTile,Bitmap>>(emptyMap());private set
    var tileMessage by mutableStateOf("");private set
    var busy by mutableStateOf(false);private set
    var status by mutableStateOf("");private set
    var failed by mutableStateOf(false);private set
    var metric by mutableStateOf(app.getSharedPreferences("maps",0).getBoolean("metric",false));private set
    var radius by mutableIntStateOf(app.getSharedPreferences("maps",0).getInt("radius",10));private set
    private val images=android.util.LruCache<MapsTile,Bitmap>(64)
    private var fitPending=false
    fun fitRoute(){route?.let{fitPending=true;camera=MapsMath.fit(it.points,viewportWidth,viewportHeight)}}
    private var viewportWidth=360;private var viewportHeight=400
    fun viewport(w:Int,h:Int){viewportWidth=w.coerceAtLeast(1);viewportHeight=h.coerceAtLeast(1);if(fitPending)route?.let{camera=MapsMath.fit(it.points,viewportWidth,viewportHeight);fitPending=false}}
    fun cancelTiles(){tileTask?.cancel()}
    private var generation=0
    private var task:Job?=null;private var tileTask:Job?=null
    var hasSearched by mutableStateOf(false);private set
    init{viewModelScope.launch{runCatching{withContext(Dispatchers.IO){check(mapsAccess(app));MapsStore.read(app)}}.onSuccess{saved=it}.onFailure{status="Unable to open saved places. Your saved file has been kept.";failed=true};val p=app.getSharedPreferences("maps",0);runCatching{val lat=p.getString("lat",null)?.toDouble()?:return@runCatching;val lon=p.getString("lon",null)?.toDouble()?:return@runCatching;MapsMath.valid(lat,lon);camera=MapsCamera(lat,lon,p.getFloat("zoom",10f).toDouble().coerceIn(2.0,19.0))}}}
    private fun run(label:String,action:suspend CoroutineScope.()->Unit){val token=++generation;task?.cancel();busy=true;status=label;failed=false;task=viewModelScope.launch{try{action()}catch(e:CancellationException){throw e}catch(e:Exception){status=if(e is java.io.IOException)"Connection unavailable. Check your network and try again."else e.message?:"Unable to complete. Try again.";failed=true}finally{if(token==generation)busy=false}}}
    fun message(text:String){status=text;failed=text.isNotBlank()}
    fun cancel(){generation++;task?.cancel();tileTask?.cancel();if(busy)status="Cancelled";busy=false}
    fun settings(metric:Boolean=this.metric,radius:Int=this.radius){this.metric=metric;this.radius=radius.coerceIn(1,50);getApplication<Application>().getSharedPreferences("maps",0).edit().putBoolean("metric",metric).putInt("radius",this.radius).apply()}
    fun move(dx:Double,dy:Double,zoom:Double=1.0){fitPending=false;camera=MapsMath.move(camera,dx,dy,zoom)}
    fun center(place:MapsPlace,zoom:Double=15.0){fitPending=false;selected=place;camera=MapsCamera(place.lat,place.lon,zoom);saveCamera()}
    fun saveCamera(){if(!mapsAccess(getApplication()))return;getApplication<Application>().getSharedPreferences("maps",0).edit().putString("lat",camera.lat.toString()).putString("lon",camera.lon.toString()).putFloat("zoom",camera.zoom.toFloat()).apply()}
    fun pin(lat:Double,lon:Double){center(MapsPlace("pin:$lat,$lon","Dropped pin",lat,lon),camera.zoom)}
    fun search(q:String,onReady:()->Unit={}){val text=q.trim();if(text.length !in 2..160){status="Enter a ZIP code, city, address, or place";failed=true;return};run("Searching…"){
        val c=getApplication<Application>();val local=withContext(Dispatchers.IO){MapsOffline.search(c,text)};val exact=text.matches(Regex("\\d{5}(-\\d{4})?"))||text.split(',').let{it.size==2&&it.all{x->x.trim().toDoubleOrNull()!=null}}
        results=if(exact)local else try{val online=MapsParser.photon(MapsNet.json(c,MapsNet.searchUrl(text)));(online+local).distinctBy{"${it.name.lowercase()}:${"%.3f".format(it.lat)}:${"%.3f".format(it.lon)}"}.take(20)}catch(e:Exception){if(e is CancellationException)throw e;if(local.isEmpty())throw e;status="Showing matching cities from saved map data";local}
        resultTitle="Search results";hasSearched=true;if(!status.startsWith("Showing"))status=if(results.isEmpty())"No matches. Try adding a city, state, or country."else "";onReady()
    }}
    fun nearby(category:String,onReady:()->Unit={}){val center=MapsPlace("area","Map area",camera.lat,camera.lon);run("Finding ${category.lowercase()}…"){
        results=MapsParser.photon(MapsNet.json(getApplication(),MapsNet.nearbyUrl(category,center,radius),3600000)).filter{MapsMath.distance(center,it)<=radius*1000}.sortedBy{MapsMath.distance(center,it)}.distinctBy{"${it.name.lowercase()}:${"%.4f".format(it.lat)}:${"%.4f".format(it.lon)}"};hasSearched=true;resultTitle="$category nearby";status=if(results.isEmpty())"No places found within $radius km. Move the map or increase the radius in Settings."else "";onReady()
    }}
    fun choose(place:MapsPlace,endpoint:String="place"){when(endpoint){"from"->{from=place;route=null};"to"->{to=place;route=null;center(place)};else->{route=null;center(place)}}}
    fun chooseMode(value:String){mode=value;route=null}
    fun swap(){val a=from;from=to;to=a;route=null}
    fun useCurrentAsStart(){from=current;route=null}
    fun destination(){to=selected;from=from?:current;route=null}
    fun directions(){val a=from;val b=to;if(a==null||b==null){status="Choose a start and destination";failed=true;return};run("Finding directions…"){val r=MapsParser.route(MapsNet.json(getApplication(),MapsNet.routeUrl(a,b,mode),0),a,b,mode);route=r;fitPending=true;camera=MapsMath.fit(r.points,viewportWidth,viewportHeight);selected=b;saveCamera();status=""}}
    fun showRoute(r:MapsRoute){route=r;from=r.from;to=r.to;mode=r.mode;selected=r.to;fitPending=true;camera=MapsMath.fit(r.points,viewportWidth,viewportHeight);saveCamera()}
    fun clearRoute(){route=null}
    fun location(onReady:()->Unit={}){run("Finding your location…"){
        val c=getApplication<Application>();check(mapsAccess(c,"location.foreground")){"Location access is off in Settings > Apps > Maps."}
        check(ContextCompat.checkSelfPermission(c,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED){"Allow location access, or search for a ZIP code or city."}
        val manager=c.getSystemService(LocationManager::class.java);val precise=ContextCompat.checkSelfPermission(c,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
        val providers=listOf(LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER).filter{(precise||it!=LocationManager.GPS_PROVIDER)&&manager.isProviderEnabled(it)};check(providers.isNotEmpty()){ "Turn on your phone’s Location setting, or search for a place." }
        val recent=providers.mapNotNull{runCatching{manager.getLastKnownLocation(it)}.getOrNull()}.filter{SystemClock.elapsedRealtimeNanos()-it.elapsedRealtimeNanos in 0..120_000_000_000L}.minByOrNull{it.accuracy}
        val found=recent?:withTimeoutOrNull(20000){suspendCancellableCoroutine<Location>{continuation->val listener=object:LocationListener{override fun onLocationChanged(location:Location){manager.removeUpdates(this);if(continuation.isActive)continuation.resume(location)};override fun onProviderEnabled(p:String){};override fun onProviderDisabled(p:String){};@Deprecated("Legacy")override fun onStatusChanged(p:String,s:Int,b:Bundle?){} };continuation.invokeOnCancellation{manager.removeUpdates(listener)};providers.forEach{manager.requestLocationUpdates(it,0L,0f,listener,Looper.getMainLooper())}}}?:error("Location timed out. Try outdoors, or search for a place.")
        check(mapsAccess(c,"location.foreground"));val p=MapsPlace("current:${found.latitude},${found.longitude}","Current location",found.latitude,found.longitude);current=p;accuracy=found.accuracy;center(p);status=if(found.accuracy>100)"Approximate location · accuracy about ${MapsMath.distanceText(found.accuracy.toDouble(),metric)}"else "";onReady()
    }}
    private fun store(next:MapsSaved){run("Saving…"){withContext(NonCancellable){withContext(Dispatchers.IO){MapsStore.save(getApplication(),next)};saved=next;selected?.let{p->next.places.firstOrNull{MapsMath.distance(it,p)<20}?.let{selected=it}};route?.let{r->next.routes.firstOrNull{it.id==r.id}?.let{route=it}};status="Saved"}}}
    fun savePlace(p:MapsPlace,name:String){val title=name.trim().take(120);if(title.isBlank())return;val existing=saved.places.indexOfFirst{it.id==p.id||MapsMath.distance(it,p)<20};val list=saved.places.toMutableList();if(existing>=0)list[existing]=p.copy(id=list[existing].id,name=title)else list.add(p.copy(name=title));store(saved.copy(places=list))}
    fun saveRoute(r:MapsRoute,name:String){val title=name.trim().take(120);if(title.isBlank())return;val list=saved.routes.toMutableList();val i=list.indexOfFirst{it.id==r.id};if(i>=0)list[i]=r.copy(name=title)else list.add(r.copy(name=title));store(saved.copy(routes=list))}
    fun deletePlace(id:String)=store(saved.copy(places=saved.places.filterNot{it.id==id}))
    fun deleteRoute(id:String)=store(saved.copy(routes=saved.routes.filterNot{it.id==id}))
    fun clearCache(){run("Clearing cached maps…"){withContext(Dispatchers.IO){MapsNet.clear(getApplication())};images.evictAll();tiles=emptyMap();status="Cache cleared"}}
    fun loadTiles(width:Int,height:Int){tileTask?.cancel();val c=camera;tileTask=viewModelScope.launch{delay(180);try{
        val z=floor(c.zoom).toInt();val center=SdkMapTiles.project(c.lat,c.lon,z);val scale=256*2.0.pow(c.zoom-z);val world=1 shl z;val keys=mutableSetOf<MapsTile>()
        for(y in floor(center.second-height/2/scale).toInt()..floor(center.second+height/2/scale).toInt())for(x in floor(center.first-width/2/scale).toInt()..floor(center.first+width/2/scale).toInt()){if(y in 0 until world)keys.add(MapsTile(z,((x%world)+world)%world,y))};require(keys.size<=64)
        tiles=keys.mapNotNull{k->images.get(k)?.let{k to it}}.toMap();var failures=0;val slots=Semaphore(2)
        coroutineScope{keys.filter{images.get(it)==null}.map{k->async{slots.withPermit{try{val bytes=MapsNet.fetch(getApplication(),"https://tile.openstreetmap.org/${k.z}/${k.x}/${k.y}.png",524288,true);val b=withContext(Dispatchers.Default){val opts=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeByteArray(bytes,0,bytes.size,opts);require(opts.outWidth==256&&opts.outHeight==256);requireNotNull(BitmapFactory.decodeByteArray(bytes,0,bytes.size))};ensureActive();images.put(k,b);tiles=tiles+(k to b)}catch(e:CancellationException){throw e}catch(_:Exception){failures++}}}}.awaitAll()};tileMessage=if(failures>0)"Some map tiles are unavailable. Tap Retry."else ""
    }catch(e:CancellationException){throw e}catch(_:Exception){tileMessage="Map unavailable. Check your connection and tap Retry."}}}
    override fun onCleared(){cancel();images.evictAll();super.onCleared()}
}
