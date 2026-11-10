package app.onklav.home

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.onklav.shared.ReferenceDialog
import app.onklav.shared.DialogAction
import kotlinx.coroutines.*
import org.json.JSONObject
import kotlin.math.roundToInt

/** Passive private raster renderer: no URL loads, location reads or disk cache. */
@Composable internal fun SdkMapViewer(node:JSONObject,model:SdkAppModel,modifier:Modifier=Modifier){
 key(node.toString()){
  val c=LocalContext.current;val lifecycle=LocalLifecycleOwner.current
  var active by remember{mutableStateOf(lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))}
  var loaded by remember{mutableStateOf<List<Pair<JSONObject,android.graphics.Bitmap>>>(emptyList())}
  var status by remember{mutableStateOf("Loading map tiles…")};var scale by remember{mutableFloatStateOf(1f)};var pan by remember{mutableStateOf(Offset.Zero)};var dialog by remember{mutableStateOf(false)}
  val density=LocalDensity.current;var viewportWidth by remember{mutableIntStateOf(0)};var viewportHeight by remember{mutableIntStateOf(0)};var reportedViewport by remember{mutableStateOf(0 to 0)}
  LaunchedEffect(viewportWidth,viewportHeight,model.busy,model.pending,active){if(active&&!model.busy&&model.pending==null&&node.has("on_viewport")&&viewportWidth>0&&viewportHeight>0&&reportedViewport!=(viewportWidth to viewportHeight)&&(node.optInt("viewport_width")!=viewportWidth||node.optInt("viewport_height")!=viewportHeight)){reportedViewport=viewportWidth to viewportHeight;model.mapViewport(node.getString("id"),viewportWidth,viewportHeight)}}
  fun navigate(operation:String,dx:Double=0.0,dy:Double=0.0){if(viewportWidth>0&&viewportHeight>0)model.mapViewport(node.getString("id"),viewportWidth,viewportHeight,operation,dx,dy)}
  val id=node.getString("id");val zoom=node.getInt("zoom");val world=(1 shl zoom).toDouble();val center=SdkMapTiles.project(node.getDouble("latitude"),node.getDouble("longitude"),zoom)
  DisposableEffect(lifecycle){val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_STOP){active=false;loaded=emptyList();dialog=false}else if(event==Lifecycle.Event.ON_START)active=true};lifecycle.lifecycle.addObserver(observer);onDispose{lifecycle.lifecycle.removeObserver(observer)}}
  LaunchedEffect(active,model.sessionUnavailable){
   loaded=emptyList();if(!active||model.sessionUnavailable)return@LaunchedEffect
   try{
    val result=withContext(Dispatchers.IO){
     val d=model.mapDocument(id);check(d.getJSONObject("node").toString()==node.toString()){"Map changed. Reopen it."}
     fun authorize(){check(active&&!model.sessionUnavailable);check(model.mapDocument(id).toString()==d.toString()){"Map access changed"}}
     val rows=d.getJSONArray("tiles");require(rows.length()<=25);val owner=d.getJSONObject("owner");val images=ArrayList<Pair<JSONObject,android.graphics.Bitmap>>()
     try{for(i in 0 until rows.length()){ensureActive();val row=rows.getJSONObject(i);images.add(row to SdkMapTiles.decode(c,owner,row.getJSONObject("file"),::authorize))};authorize();images.toList()}
     catch(e:Exception){images.forEach{it.second.recycle()};throw e}
    };loaded=result;status=if(result.isEmpty())"No tiles supplied. Map data is unavailable."else"${result.size} supplied tiles · z$zoom · no additional detail when magnified"
   }catch(e:CancellationException){throw e}catch(e:Exception){status="Map unavailable. ${e.message?:"Reopen the app or choose valid tiles."}"}
  }
  val enabled=active&&!model.sessionUnavailable&&!model.busy&&model.pending==null
  val surface=MaterialTheme.colorScheme.surfaceVariant;val line=MaterialTheme.colorScheme.outline;val markerColor=MaterialTheme.colorScheme.primary
  Column(modifier.fillMaxSize()){
   Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())){
    TextButton(onClick={if(node.has("on_viewport"))navigate("zoom_out")else scale=(scale/2).coerceAtLeast(.5f)},enabled=enabled&&(if(node.has("on_viewport"))zoom>0 else scale>.5f),modifier=Modifier.heightIn(min=48.dp)){Text("Zoom −")}
    TextButton(onClick={if(node.has("on_viewport"))navigate("zoom_in")else scale=(scale*2).coerceAtMost(4f)},enabled=enabled&&(if(node.has("on_viewport"))zoom<19 else scale<4f),modifier=Modifier.heightIn(min=48.dp)){Text("Zoom +")}
    TextButton(onClick={scale=1f;pan=Offset.Zero},enabled=enabled,modifier=Modifier.heightIn(min=48.dp)){Text("Reset")}
    TextButton(onClick={if(node.has("on_viewport"))navigate("north")else pan=(pan+Offset(0f,128f)).limit()},enabled=enabled,modifier=Modifier.heightIn(min=48.dp)){Text("North")}
    TextButton(onClick={if(node.has("on_viewport"))navigate("south")else pan=(pan+Offset(0f,-128f)).limit()},enabled=enabled,modifier=Modifier.heightIn(min=48.dp)){Text("South")}
    TextButton(onClick={if(node.has("on_viewport"))navigate("west")else pan=(pan+Offset(128f,0f)).limit()},enabled=enabled,modifier=Modifier.heightIn(min=48.dp)){Text("West")}
    TextButton(onClick={if(node.has("on_viewport"))navigate("east")else pan=(pan+Offset(-128f,0f)).limit()},enabled=enabled,modifier=Modifier.heightIn(min=48.dp)){Text("East")}
   }
   Text(status,Modifier.semantics{liveRegion=LiveRegionMode.Polite})
   Canvas(Modifier.weight(1f).fillMaxWidth().onSizeChanged{viewportWidth=with(density){it.width.toDp().value.toInt().coerceAtLeast(1)};viewportHeight=with(density){it.height.toDp().value.toInt().coerceAtLeast(1)}}.clipToBounds().background(surface).semantics{contentDescription="Map centered at ${node.getDouble("latitude")}, ${node.getDouble("longitude")}. Supplied raster zoom $zoom. Use direction buttons to pan; map details list markers."}.pointerInput(enabled){if(enabled)detectDragGestures(onDragEnd={if(node.has("on_viewport")){val tile=with(density){256.dp.toPx()}*scale;navigate("pan",(-pan.x/tile).toDouble().coerceIn(-4.0,4.0),(-pan.y/tile).toDouble().coerceIn(-4.0,4.0))}}){change,drag->change.consume();pan=(pan+drag).limit()}}){
    if(active&&!model.sessionUnavailable){val tile=256.dp.toPx()*scale;val origin=Offset(size.width/2+pan.x,size.height/2+pan.y)
     // Draw nearest wrapped copies, including repeated world tiles at zoom zero.
     for((row,image)in loaded){val dx=SdkMapTiles.wrappedDelta(row.getDouble("x"),center.first,world);val dy=row.getDouble("y")-center.second
      for(wrap in -1..1){val left=origin.x+(dx+wrap*world).toFloat()*tile;val top=origin.y+dy.toFloat()*tile;if(left+tile>=0&&left<=size.width&&top+tile>=0&&top<=size.height)drawImage(image.asImageBitmap(),dstOffset=IntOffset(left.roundToInt(),top.roundToInt()),dstSize=IntSize(tile.roundToInt()+1,tile.roundToInt()+1))}
     }
     node.optJSONObject("route")?.getJSONArray("points")?.let{points->val path=androidx.compose.ui.graphics.Path();var previous:Double?=null;for(i in 0 until points.length()){val p=points.getJSONObject(i);val projected=SdkMapTiles.project(p.getDouble("latitude"),p.getDouble("longitude"),zoom);val dx=SdkMapTiles.wrappedDelta(projected.first,center.first,world);val px=origin.x+dx.toFloat()*tile;val py=origin.y+(projected.second-center.second).toFloat()*tile;if(previous==null||kotlin.math.abs(dx-previous!!)>world/2)path.moveTo(px,py)else path.lineTo(px,py);previous=dx};drawPath(path,markerColor,style=androidx.compose.ui.graphics.drawscope.Stroke(4.dp.toPx()))}
     val markers=node.getJSONArray("markers");for(i in 0 until markers.length()){val m=markers.getJSONObject(i);val point=SdkMapTiles.project(m.getDouble("latitude"),m.getDouble("longitude"),zoom);val at=Offset(origin.x+SdkMapTiles.wrappedDelta(point.first,center.first,world).toFloat()*tile,origin.y+(point.second-center.second).toFloat()*tile);drawCircle(markerColor,7.dp.toPx(),at);drawCircle(line,8.dp.toPx(),at,style=androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))}
    }
   }
   node.optJSONObject("route")?.let{Text(it.getString("attribution"))}
   // Attribution is outside the pannable/scaled viewport and cannot be hidden by tiles.
   TextButton(onClick={dialog=true},enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(SdkMapTiles.attribution(node))}
  }
  if(dialog&&active&&!model.sessionUnavailable)ReferenceDialog("Map details",{dialog=false},footer={DialogAction("Close"){dialog=false}}){
   val p=node.getJSONObject("provider");Text(p.getString("name"));Text(SdkMapTiles.attribution(node));Text(p.getString("copyright_url"));if(p.getString("data")=="osm")Text("https://www.openstreetmap.org/copyright")
   Text("Tile set z$zoom · ${node.getDouble("latitude")}, ${node.getDouble("longitude")}. Raster magnification does not fetch new map detail.")
   val markers=node.getJSONArray("markers");for(i in 0 until markers.length()){val m=markers.getJSONObject(i);Text("${m.getString("label")} · ${m.getDouble("latitude")}, ${m.getDouble("longitude")}")}
  }
 }
}
private fun Offset.limit()=Offset(x.coerceIn(-2048f,2048f),y.coerceIn(-2048f,2048f))
