package app.onklav.home

import android.graphics.Bitmap
import app.onklav.shared.CompactAction
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.semantics.Role
import org.json.JSONObject
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.time.*
import java.time.format.DateTimeFormatter

internal fun videoSection(time:Long,now:LocalDate=LocalDate.now(),zone:ZoneId=ZoneId.systemDefault()):String {
    val day=Instant.ofEpochMilli(time).atZone(zone).toLocalDate()
    return when { day==now->"Today";day==now.minusDays(1)->"Yesterday";day>=now.minusDays(6)->"Last 7 Days";day.year==now.year && day.month==now.month->"Earlier This Month";else->day.format(DateTimeFormatter.ofPattern("MMMM yyyy")) }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun SdkMediaRecord(node:JSONObject,model:SdkAppModel,modifier:Modifier) {
    val context=LocalContext.current;val id=node.getString("id");val video=node.optString("video");val title=node.optString("text").ifBlank{"Video"};val copy=node.optString("copy").takeIf{it.isNotBlank()};val delete=node.optString("delete").takeIf{it.isNotBlank()};val enabled=!model.busy && model.pending==null && node.optBoolean("enabled",true)
    val epoch by AppSdk.epoch.collectAsState()
    var image by remember(id,video,epoch,node.optLong("cache_epoch")) { mutableStateOf<Bitmap?>(null) }
    val owner=LocalLifecycleOwner.current
    var foreground by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(owner,id) {
        val app=runCatching { model.imageComponent(id).getString("app") }.getOrNull()
        val observer=LifecycleEventObserver { _,_ -> foreground=owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED);if(!foreground && app!=null) SdkThumbnails.cancelApp(app) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer);if(app!=null) SdkThumbnails.cancelApp(app) }
    }
    LaunchedEffect(id,video,epoch,node.optLong("cache_epoch"),foreground) {
        if(foreground && video.isNotEmpty()) runCatching {
            val app=model.imageComponent(id).getString("app")
            image=SdkThumbnails.load(context,app,video) {
                val auth=model.imageComponent(id);require(auth.child("node").optString("video")==video);auth.optBoolean("download")
            }
        }
    }
    Column(modifier.fillMaxWidth()) {
        if(node.optBoolean("sections")) {
            val section=videoSection(node.getLong("time"))
            if(node.isNull("previous_time") || section!=videoSection(node.optLong("previous_time"))) Text(section,Modifier.padding(top=14.dp,bottom=6.dp),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth().testTag("sdk-$id").combinedClickable(enabled=enabled,role=Role.Button,onClickLabel="Open video",onClick={model.action(id)},onLongClickLabel=if(copy!=null)"Copy video link"else null,onLongClick=copy?.let{action->{model.action(action)}}).padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(96.dp,54.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),contentAlignment=Alignment.Center) {
                image?.let { Image(it.asImageBitmap(),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop) } ?: Icon(Icons.Outlined.PlayArrow,null)
            }
            Text(title,Modifier.weight(1f).padding(horizontal=10.dp),fontSize=14.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
            if(delete!=null)CompactAction(Icons.Outlined.Close,"Remove $title",enabled) { model.action(delete) }
        }
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
    }
}
