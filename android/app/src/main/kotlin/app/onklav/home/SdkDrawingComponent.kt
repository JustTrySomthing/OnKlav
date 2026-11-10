package app.onklav.home

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.onklav.shared.*
import kotlinx.coroutines.*
import org.json.JSONObject

@Composable internal fun SdkDrawingComponent(node:JSONObject,model:SdkAppModel,modifier:Modifier) {
 val owner=LocalLifecycleOwner.current
 var active by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
 DisposableEffect(owner) { val watch=LifecycleEventObserver { _,_->active=owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) };owner.lifecycle.addObserver(watch);onDispose {owner.lifecycle.removeObserver(watch)} }
 val epoch by AppSdk.epoch.collectAsState()
 var viewport by rememberSaveable(node.optJSONObject("document")?.optString("id")){mutableStateOf("")}
 Box(modifier.fillMaxWidth().testTag("sdk-canvas-surface")) {
  if(active) key(epoch,node.optJSONObject("document")?.optString("id")) { SdkActiveDrawing(node,model,viewport){viewport=it} }
 }
}
@Composable private fun SdkActiveDrawing(node:JSONObject,model:SdkAppModel,viewport:String,saveViewport:(String)->Unit) {
 val id=node.getString("id");val metadata=node.optJSONObject("document")
 val undo=node.optString("undo_action").takeIf{it.isNotBlank()};val redo=node.optString("redo_action").takeIf{it.isNotBlank()};val tools=node.optString("tools_action").takeIf{it.isNotBlank()}
 var doc by remember { mutableStateOf<JSONObject?>(null) };var image by remember { mutableStateOf<Bitmap?>(null) }
 var failure by remember { mutableStateOf<String?>(null) };var view by remember { mutableStateOf<SdkDrawingView?>(null) }
 val latest by rememberUpdatedState(node)
 LaunchedEffect(metadata?.optLong("revision")) {
  if(metadata!=null) try {
   val result=withContext(Dispatchers.IO) { model.canvasComponent(id,image==null) }
   val value=result.child("document");require(value.getString("id")==metadata.getString("id") && value.getLong("revision")==metadata.getLong("revision"))
   if(image==null) image=withContext(Dispatchers.IO) { SdkDrawingImages.image(value) }
   doc=value;failure=null
  } catch(e:Exception) { if(e is CancellationException) throw e;failure=e.message;view?.clear();image=null }
 }
 DisposableEffect(Unit) { onDispose { view?.viewport()?.takeIf{it.isNotEmpty()}?.let(saveViewport);view?.clear();image?.takeUnless { it.isRecycled }?.recycle() } }
 Box(Modifier.fillMaxSize()) {
  if(metadata==null) Text("Open a Picture to Start",Modifier.align(Alignment.Center),fontSize=16.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
  else AndroidView(modifier=Modifier.fillMaxSize().padding(bottom=0.dp),factory={ctx->SdkDrawingView(ctx).also {view=it}},update={v->
   v.tool=node.getString("tool");v.ink=node.getLong("color").toInt();v.thickness=node.getDouble("width").toFloat();v.isEnabled=!model.busy && model.pending==null && failure==null && doc?.optLong("revision",-1)==metadata?.optLong("revision")
   v.send={event,done->val current=latest.child("document");model.canvasEvent(id,current.getString("id"),current.getLong("revision"),event,done)}
   v.viewportChanged=saveViewport
   doc?.let { v.update(it,image);v.restoreViewport(viewport) }
  })
  failure?.let { Text(it,Modifier.align(Alignment.Center).padding(12.dp),color=MaterialTheme.colorScheme.error) }
  Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start=4.dp,end=if(tools!=null)72.dp else 4.dp,bottom=8.dp).horizontalScroll(rememberScrollState())) {
   if(undo!=null)TextButton(modifier=Modifier.heightIn(min=48.dp),onClick={model.action(undo)},enabled=metadata?.optBoolean("undo")==true && !model.busy && model.pending==null) {Text("Undo",fontSize=12.sp)}
   if(redo!=null)TextButton(modifier=Modifier.heightIn(min=48.dp),onClick={model.action(redo)},enabled=metadata?.optBoolean("redo")==true && !model.busy && model.pending==null) {Text("Redo",fontSize=12.sp)}
   TextButton(modifier=Modifier.heightIn(min=48.dp),onClick={view?.fit()},enabled=metadata!=null && !model.busy && model.pending==null) {Text("Fit",fontSize=12.sp)}
  }
  if(tools!=null)Surface(onClick={model.action(tools)},modifier=Modifier.align(Alignment.BottomEnd).padding(end=8.dp,bottom=8.dp).size(52.dp).testTag("sdk-canvas-tools"),shape=CircleShape,color=MaterialTheme.colorScheme.surface,border=androidx.compose.foundation.BorderStroke(2.dp,MaterialTheme.colorScheme.outline),enabled=!model.busy && model.pending==null) { Box(contentAlignment=Alignment.Center) {Icon(Icons.Outlined.Brush,"Drawing tools",Modifier.size(24.dp))} }
 }
}
@Composable internal fun SdkImageRecord(node:JSONObject,model:SdkAppModel,modifier:Modifier) {
 val title=node.optString("text").ifBlank{"Image"};val delete=node.optString("delete").takeIf{it.isNotBlank()};val enabled=!model.busy && model.pending==null && node.optBoolean("enabled",true)
 val epoch by AppSdk.epoch.collectAsState()
 var image by remember(node.getString("document"),node.optLong("revision"),epoch) {mutableStateOf<Bitmap?>(null)}
 LaunchedEffect(node.getString("document"),node.optLong("revision"),epoch) {
  try { val auth=withContext(Dispatchers.IO) {model.drawingImage(node.getString("id"))};image=withContext(Dispatchers.IO) {SdkDrawingImages.thumbnail(auth)} }
  catch(e:Exception) { if(e is CancellationException) throw e;image=null }
 }
 DisposableEffect(image) {val captured=image;onDispose {captured?.takeUnless {it.isRecycled}?.recycle()} }
 Row(modifier.fillMaxWidth().testTag("sdk-"+node.getString("id")).clickable(enabled=enabled,role=Role.Button,onClickLabel="Open image") {model.action(node.getString("id"))}.padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
  Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {image?.let {Image(it.asImageBitmap(),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)} }
  Column(Modifier.weight(1f).padding(horizontal=8.dp)) {Text(title,fontSize=16.sp,maxLines=2);if(node.optLong("time")>0)Text(java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(node.getLong("time"))),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
  if(delete!=null)CompactAction(Icons.Outlined.Close,"Delete $title",enabled) {model.action(delete)}
 }
}
