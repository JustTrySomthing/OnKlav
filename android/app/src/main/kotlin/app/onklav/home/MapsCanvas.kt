package app.onklav.home

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import kotlin.math.*

@Composable internal fun MapsCanvas(model:MapsModel,modifier:Modifier=Modifier,online:Boolean=true){
    val camera=model.camera;val latestCamera by rememberUpdatedState(camera);val density=LocalDensity.current;var viewport by remember{mutableStateOf(IntSize.Zero)};val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current
    var active by remember{mutableStateOf(true)}
    DisposableEffect(lifecycle){val observer=androidx.lifecycle.LifecycleEventObserver{_,e->if(e==androidx.lifecycle.Lifecycle.Event.ON_STOP){active=false;model.cancelTiles()}else if(e==androidx.lifecycle.Lifecycle.Event.ON_START)active=true};lifecycle.lifecycle.addObserver(observer);onDispose{lifecycle.lifecycle.removeObserver(observer);model.cancelTiles()}}
    LaunchedEffect(camera,viewport,active){if(online&&active&&viewport.width>0&&viewport.height>0)model.loadTiles(with(density){viewport.width.toDp().value.toInt()},with(density){viewport.height.toDp().value.toInt()})}
    val colors=MaterialTheme.colorScheme;val primary=colors.primary;val border=colors.surface;val markers=(model.results.take(40)+listOfNotNull(model.selected,model.current,model.route?.from,model.route?.to)).distinctBy{it.id}
    val latestMarkers by rememberUpdatedState(markers)
    fun point(lat:Double,lon:Double,width:Int,height:Int,camera:MapsCamera=latestCamera):Offset{val z=floor(camera.zoom).toInt();val c=SdkMapTiles.project(camera.lat,camera.lon,z);val p=SdkMapTiles.project(lat,lon,z);val tile=with(density){256.dp.toPx()}*2.0.pow(camera.zoom-z);return Offset((width/2+SdkMapTiles.wrappedDelta(p.first,c.first,2.0.pow(z))*tile).toFloat(),(height/2+(p.second-c.second)*tile).toFloat())}
    Box(modifier.clipToBounds().background(colors.surfaceContainerHighest)){
        Canvas(Modifier.fillMaxSize().onSizeChanged{viewport=it;model.viewport(with(density){it.width.toDp().value.toInt()},with(density){it.height.toDp().value.toInt()})}.semantics{contentDescription="Map centered at ${"%.4f".format(camera.lat)}, ${"%.4f".format(camera.lon)}. Zoom ${camera.zoom.roundToInt()}. Long press to drop a pin.";customActions=listOf(CustomAccessibilityAction("Pan north"){model.move(0.0,128.0);true},CustomAccessibilityAction("Pan south"){model.move(0.0,-128.0);true},CustomAccessibilityAction("Pan west"){model.move(128.0,0.0);true},CustomAccessibilityAction("Pan east"){model.move(-128.0,0.0);true})}.pointerInput(model,density){
            awaitEachGesture{val down=awaitFirstDown(requireUnconsumed=false);var moved=false;var last=down.position;var end=down.uptimeMillis
                do{val event=awaitPointerEvent();val pan=event.calculatePan();val zoom=event.calculateZoom();last=event.changes.first().position;end=event.changes.first().uptimeMillis
                    if(event.changes.count{it.pressed}>1||(last-down.position).getDistance()>viewConfiguration.touchSlop)moved=true
                    if(moved){event.changes.forEach{it.consume()};model.move((pan.x/density.density).toDouble(),(pan.y/density.density).toDouble(),zoom.toDouble())}
                }while(event.changes.any{it.pressed})
                if(!moved){val camera=latestCamera;if(end-down.uptimeMillis>=500){val z=floor(camera.zoom).toInt();val c=SdkMapTiles.project(camera.lat,camera.lon,z);val tile=with(density){256.dp.toPx()}*2.0.pow(camera.zoom-z);val ll=MapsMath.unproject(c.first+(last.x-size.width/2)/tile,c.second+(last.y-size.height/2)/tile,z);model.pin(ll.first,ll.second)}else latestMarkers.minByOrNull{(point(it.lat,it.lon,size.width,size.height)-last).getDistance()}?.takeIf{(point(it.lat,it.lon,size.width,size.height)-last).getDistance()<with(density){24.dp.toPx()}}?.let{model.center(it,camera.zoom)}}
                model.saveCamera()
            }
        }){
            val z=floor(camera.zoom).toInt();val world=2.0.pow(z);val center=SdkMapTiles.project(camera.lat,camera.lon,z);val tile=256.dp.toPx()*2.0.pow(camera.zoom-z)
            model.tiles.forEach{(key,image)->if(key.z==z){val dx=SdkMapTiles.wrappedDelta(key.x.toDouble(),center.first,world);val dy=key.y-center.second;drawImage(image.asImageBitmap(),dstOffset=IntOffset((size.width/2+dx*tile).roundToInt(),(size.height/2+dy*tile).roundToInt()),dstSize=IntSize(tile.roundToInt()+1,tile.roundToInt()+1))}}
            model.route?.let{r->val path=Path();var previous:Double?=null;r.points.forEach{ll->val p=SdkMapTiles.project(ll.first,ll.second,z);val dx=SdkMapTiles.wrappedDelta(p.first,center.first,world);val x=(size.width/2+dx*tile).toFloat();val y=(size.height/2+(p.second-center.second)*tile).toFloat();if(previous==null||abs(dx-previous!!)>world/2)path.moveTo(x,y)else path.lineTo(x,y);previous=dx};drawPath(path,border,style=Stroke(8.dp.toPx()));drawPath(path,primary,style=Stroke(5.dp.toPx()))}
            markers.forEach{p->val at=point(p.lat,p.lon,size.width.toInt(),size.height.toInt());val color=if(p.id==model.current?.id)androidx.compose.ui.graphics.Color(0xff1976d2)else primary;drawCircle(border,9.dp.toPx(),at);drawCircle(color,6.dp.toPx(),at)}
        }
        if(model.tiles.isEmpty())Text(if(online)"Loading map…" else "Map preview",Modifier.align(Alignment.Center).background(colors.surface.copy(alpha=.9f)).padding(12.dp),style=MaterialTheme.typography.bodySmall)
        Surface(Modifier.align(Alignment.CenterEnd).padding(10.dp),shape=androidx.compose.foundation.shape.RoundedCornerShape(14.dp),tonalElevation=3.dp){Column{IconButton(onClick={model.move(0.0,0.0,2.0)},enabled=camera.zoom<19){MapsIcon("plus","Zoom in")};IconButton(onClick={model.move(0.0,0.0,.5)},enabled=camera.zoom>2){MapsIcon("minus","Zoom out")}}}
        if(model.tileMessage.isNotEmpty())Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth(),color=colors.surface.copy(alpha=.95f)){Row(verticalAlignment=Alignment.CenterVertically){Text(model.tileMessage,Modifier.weight(1f).padding(start=12.dp),style=MaterialTheme.typography.bodySmall);TextButton(onClick={model.loadTiles(with(density){viewport.width.toDp().value.toInt()},with(density){viewport.height.toDp().value.toInt()})}){Text("Retry")}}}
    }
}
