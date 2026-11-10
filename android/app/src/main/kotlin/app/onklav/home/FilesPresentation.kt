package app.onklav.home

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject

internal fun initialFilesFolder(types:List<String>,save:Boolean=false):String {
 val groups=types.map{it.substringBefore('/')}.toSet()
 return when(groups.singleOrNull()){ "image"->"images";"audio"->"audio";"video"->"video";else->if(save)"documents"else"" }
}
internal fun filesPermission(c:Context,external:Boolean=false){
 val rows=AppSdk.sync(c,JSONObject().put("action","list")) as org.json.JSONArray
 check((0 until rows.length()).any{val a=rows.getJSONObject(it);a.optBoolean("installed")&&a.child("manifest").optString("id")=="app.onklav.files"&&a.optJSONArray("grants")?.let{v->(0 until v.length()).any{v.optString(it)==if(external)"files.external"else"files.manage"}}==true}){"Files access is off. Restore it in Settings > Apps > Files."}
}
internal fun filesPreview(c:Context,row:JSONObject,external:Boolean):JSONObject {
 filesPermission(c,external)
 return if(external)ExternalFiles(c,"app.onklav.files").run(JSONObject().put("operation","open").put("id",row.getString("id"))){filesPermission(c,true)}else AppSdk.sync(c,JSONObject().put("action","files_preview").put("id",row.getString("id"))) as JSONObject
}
@Composable internal fun FilesViewButton(grid:Boolean,change:()->Unit){IconButton(onClick=change){Icon(painterResource(if(grid)R.drawable.files_list else R.drawable.files_grid),if(grid)"List view"else"Thumbnail view",Modifier.size(28.dp))}}
@Composable private fun FilePicture(row:JSONObject,external:Boolean,modifier:Modifier){
 val c=LocalContext.current;val epoch by AppSdk.epoch.collectAsState();val mime=row.optString("mime")
 val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null,row.optString("id"),external,epoch){
  value=null;if(!row.optBoolean("folder")&&(mime.startsWith("image/")||mime.startsWith("video/")))value=withContext(Dispatchers.IO){MediaPreferences.decoders.withPermit{
   try{ensureActive();val descriptor=filesPreview(c,row,external);val path=descriptor.optString("path").takeIf{it.isNotBlank()};val uri=descriptor.optString("uri").takeIf{it.isNotBlank()}?.let(Uri::parse)
    val b=if(mime.startsWith("video/"))MediaMetadataRetriever().use{m->if(path!=null)m.setDataSource(path)else m.setDataSource(c,uri);m.getScaledFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST_SYNC,320,240)}else{
     fun stream()=if(path!=null)java.io.File(path).inputStream()else c.contentResolver.openInputStream(requireNotNull(uri))?:error("No image")
     val options=BitmapFactory.Options().apply{inJustDecodeBounds=true};stream().use{BitmapFactory.decodeStream(it,null,options)};require(options.outWidth>0&&options.outHeight>0&&options.outWidth.toLong()*options.outHeight<=100_000_000);options.inJustDecodeBounds=false;options.inSampleSize=1;while(maxOf(options.outWidth,options.outHeight)/options.inSampleSize>320)options.inSampleSize*=2;val decoded=stream().use{BitmapFactory.decodeStream(it,null,options)}
     val orientation=runCatching{stream().use{android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1)}}.getOrDefault(1)
     if(decoded!=null&&orientation!=1){val matrix=android.graphics.Matrix();when(orientation){2->matrix.setScale(-1f,1f);3->matrix.setRotate(180f);4->matrix.setScale(1f,-1f);5->{matrix.setRotate(90f);matrix.postScale(-1f,1f)};6->matrix.setRotate(90f);7->{matrix.setRotate(-90f);matrix.postScale(-1f,1f)};8->matrix.setRotate(-90f)};android.graphics.Bitmap.createBitmap(decoded,0,0,decoded.width,decoded.height,matrix,true).also{if(it!==decoded)decoded.recycle()}}else decoded
    };ensureActive();filesPermission(c,external);b?.asImageBitmap()
   }catch(e:CancellationException){throw e}catch(_:Exception){null}catch(_:OutOfMemoryError){null}
  }}
 }
 Box(modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),contentAlignment=Alignment.Center){
  if(bitmap!=null)Image(bitmap!!,row.optString("name"),Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
  else Icon(painterResource(if(row.optBoolean("folder"))R.drawable.files_folder else if(mime.startsWith("audio/"))R.drawable.files_audio else if(mime.startsWith("video/"))R.drawable.files_video else if(mime.startsWith("image/"))R.drawable.files_image else R.drawable.files_document),null,Modifier.size(32.dp),tint=MaterialTheme.colorScheme.primary)
  if(mime.startsWith("video/"))Icon(Icons.Outlined.PlayArrow,null,Modifier.align(Alignment.BottomEnd).padding(6.dp),tint=MaterialTheme.colorScheme.primary)
 }
}
private val fileSizeUnits = listOf("B", "KB", "MB", "GB", "TB", "PB", "EB")

private fun compactFileSize(row: JSONObject): String? {
    if (row.optBoolean("folder")) return null
    val bytes = row.optLong("bytes", -1)
    if (bytes < 0) return null
    if (bytes < 1000) return "${bytes}B"
    var size = bytes.toDouble()
    var unit = 0
    while (size >= 999.95 && unit < fileSizeUnits.lastIndex) {
        size /= 1000
        unit++
    }
    return java.text.DecimalFormat("0.#").format(size) + fileSizeUnits[unit]
}

@Composable internal fun FilesRows(rows:List<JSONObject>,grid:Boolean,external:Boolean,busy:Boolean=false,selected:Set<String> = emptySet(),accept:(JSONObject)->Boolean={true},open:(JSONObject)->Unit,edit:((JSONObject)->Unit)?=null,modifier:Modifier=Modifier){
 @Composable fun card(row:JSONObject){val enabled=!busy&&accept(row);val chosen=row.optString("id") in selected
  Surface(onClick={open(row)},enabled=enabled,shape=RoundedCornerShape(16.dp),color=if(chosen)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,modifier=Modifier.fillMaxWidth().testTag("sdk-entry:${row.optString("id")}")){
   @Composable fun caption() {
    val size = compactFileSize(row)
    val detail = when {
     grid -> size.orEmpty()
     size == null -> row.optString("detail").ifBlank { row.optString("mime") }
     external -> size
     else -> listOf(size, if (row.optBoolean("note")) "Note" else row.optString("mime"))
      .filter { it.isNotBlank() }.joinToString(" · ")
    }
    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
     Column(Modifier.weight(1f)) {
      Text(if (row.optString("id") == "system") "OnKlave Files" else row.optString("name"),
       maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
      if (detail.isNotBlank()) Text(detail, maxLines = 1, overflow = TextOverflow.Ellipsis,
       style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
     }
     if (chosen) Icon(Icons.Outlined.CheckCircle, "Selected")
     if (!grid && edit != null && !row.optBoolean("protected") && (!external || row.optBoolean("editable"))) {
      IconButton(onClick = { edit(row) }, enabled = !busy) {
       Icon(Icons.Outlined.Edit, "Options for ${row.optString("name")}", Modifier.size(20.dp))
      }
     }
    }
   }
   if(grid)Column{Box{FilePicture(row,external,Modifier.fillMaxWidth().height(128.dp))
    if(edit!=null&&!row.optBoolean("protected")&&(!external||row.optBoolean("editable")))IconButton(onClick={edit(row)},enabled=!busy,modifier=Modifier.align(Alignment.TopEnd).padding(5.dp).background(MaterialTheme.colorScheme.surface,RoundedCornerShape(8.dp))){Icon(Icons.Outlined.Edit,"Options for ${row.optString("name")}",Modifier.size(24.dp),tint=MaterialTheme.colorScheme.primary)}
   };caption()}else Row(verticalAlignment=Alignment.CenterVertically){FilePicture(row,external,Modifier.padding(start=10.dp).size(52.dp));Box(Modifier.weight(1f)){caption()}}
  }
 }
 if(grid)LazyVerticalGrid(columns=GridCells.Adaptive(148.dp),modifier=modifier.fillMaxWidth(),contentPadding=PaddingValues(vertical=12.dp),verticalArrangement=Arrangement.spacedBy(10.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)){items(rows,key={it.getString("id")}){card(it)}}
 else LazyColumn(modifier.fillMaxWidth(),contentPadding=PaddingValues(vertical=12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){items(rows,key={it.getString("id")}){card(it)}}
}
@Composable internal fun FilesWorkspaceBody(model:SdkAppModel){
 var emptyAndroidTrash by remember{mutableStateOf(false)}
 val c=LocalContext.current;val prefs=remember{c.getSharedPreferences("files-appearance",0)};var grid by remember{mutableStateOf(prefs.getBoolean("thumbnails",true))};val state=model.frame.child("state");var query by remember(state.optString("folder")){mutableStateOf("")};val folder=state.optString("folder");val external=state.optBoolean("external_mode");val busy=model.busy||model.pending!=null
 DisposableEffect(prefs){val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener{_,key->if(key=="thumbnails")grid=prefs.getBoolean("thumbnails",true)};prefs.registerOnSharedPreferenceChangeListener(listener);onDispose{prefs.unregisterOnSharedPreferenceChangeListener(listener)}}
 Column(Modifier.fillMaxSize().padding(horizontal=16.dp)){
  OutlinedTextField(query,{query=it},placeholder={Text("Search files")},leadingIcon={Icon(Icons.Outlined.Search,null)},trailingIcon={IconButton(onClick={model.action("query",query)},enabled=!busy){Icon(Icons.Outlined.Search,"Search files")}},singleLine=true,shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth().testTag("sdk-query"),enabled=!busy)
  FilesStorageTabs(external,busy||state.optJSONObject("destination")!=null){model.filesLocation(it)}
  if(folder.isNotEmpty()&&folder!="external")Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){if(external)Text(state.optString("title"),Modifier.weight(1f).padding(vertical=6.dp),style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)else Spacer(Modifier.weight(1f));TextButton(onClick={model.action("up")},enabled=!busy){Text("Parent folder")}}
  val writable=folder.isNotBlank()&&folder !in setOf("system","system-apps","trash","external")&&!folder.startsWith("system-")&&(!folder.startsWith("android-")||folder in setOf("android-downloads","android-other"))
  Row{
   if(external&&folder=="android-trash"){
    TextButton(onClick={model.androidTrash(false)},enabled=!busy){Text("Restore all")}
    TextButton(onClick={emptyAndroidTrash=true},enabled=!busy){Text("Empty trash")}
   }
   if(folder=="trash"){TextButton(onClick={model.action("restore.all")},enabled=!busy,modifier=Modifier.testTag("sdk-restore.all")){Text("Restore all")};TextButton(onClick={model.action("empty.trash")},enabled=!busy,modifier=Modifier.testTag("sdk-empty.trash")){Text("Empty trash")}}
  }
  if(folder.isEmpty())FileRecoveryPanel()
  if(external)AndroidFilesPermissionPanel{model.action("refresh")}
  if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
  if(state.optString("status").isNotBlank())Text(state.getString("status"),Modifier.padding(8.dp),color=MaterialTheme.colorScheme.error)
  if(state.child("clipboard").length()>0&&folder.isNotBlank()&&folder !in setOf("trash","android-trash","external")&&!state.optBoolean("in_trash")&&state.optJSONObject("destination")==null)TextButton(onClick={model.action("paste.here")},enabled=!busy){Text("Paste here")}
  if(state.child("destination").length()>0)Row{TextButton(onClick={model.action("destination.cancel")},modifier=Modifier.testTag("sdk-destination.cancel")){Text("Cancel")};Button(onClick={model.action("destination.accept")},enabled=!busy&&writable,modifier=Modifier.testTag("sdk-destination.accept")){Text("Use this folder")}}
  val rows=state.nodes("items").filter{!(folder.isEmpty()&&it.optString("id") in setOf("system","external"))&&!(folder=="external"&&it.optString("id") in setOf("android-other","android-photos","android-apk"))&&(state.optJSONObject("destination")==null||(it.optBoolean("folder")&&!it.optBoolean("trash")&&it.optString("id") !in setOf("trash","android-trash")))}.let{items->if(folder.isEmpty())items.sortedBy{listOf("audio","video","images","documents","downloads","trash").indexOf(it.optString("id")).let{index->if(index<0)6 else index}}else items}
  FilesRows(rows,grid,external,busy,open={model.action("${if(it.optBoolean("trash")&&external)"options"else"entry"}:${it.getString("id")}")},edit=if(state.optJSONObject("destination")==null)({model.action("options:${it.getString("id")}")})else null,modifier=Modifier.weight(1f))
  if(external&&folder=="external")TextButton(onClick={model.action("entry:android-other")},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Internal files")}
  if(rows.isEmpty()&&!busy)Text("No files here yet",Modifier.padding(16.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){if(state.optInt("offset")>0)TextButton(onClick={model.action("previous")},enabled=!busy){Text("Previous")};if(state.optInt("offset")+40<state.optInt("total"))TextButton(onClick={model.action("next")},enabled=!busy){Text("More files")}}
 }
 if(emptyAndroidTrash)AlertDialog(onDismissRequest={emptyAndroidTrash=false},title={Text("Empty Android Trash?")},text={Text("This permanently deletes every item in Android Trash. This cannot be undone.")},confirmButton={TextButton(onClick={emptyAndroidTrash=false;model.androidTrash(true)},enabled=!busy){Text("Delete permanently")}},dismissButton={TextButton(onClick={emptyAndroidTrash=false}){Text("Cancel")}})
}

@Composable internal fun FilesStorageTabs(external:Boolean,busy:Boolean,choose:(Boolean)->Unit){
 Row(Modifier.fillMaxWidth().padding(top=10.dp,bottom=4.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)){
   listOf(false to "OnKlav",true to "Android").forEach{(android,label)->
    val active=external==android
    Surface(onClick={choose(android)},enabled=!busy,selected=active,shape=RoundedCornerShape(12.dp),color=if(active)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,contentColor=if(active)MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.weight(1f).testTag("files-tab-$label")){
     Box(Modifier.padding(vertical=12.dp),contentAlignment=Alignment.Center){Text(label,style=MaterialTheme.typography.titleSmall)}
    }
   }
  }
}

@Composable internal fun FilesHeaderControls(model:SdkAppModel){
 val c=LocalContext.current;val prefs=remember{c.getSharedPreferences("files-appearance",0)}
 var grid by remember{mutableStateOf(prefs.getBoolean("thumbnails",true))}
 val folder=model.frame.child("state").optString("folder");val external=model.frame.child("state").optBoolean("external_mode");val busy=model.busy||model.pending!=null
 IconButton(onClick={model.filesLocation(external)},enabled=!busy&&model.frame.child("state").optJSONObject("destination")==null){Icon(Icons.Outlined.Home,"Files home",Modifier.size(30.dp))}
 IconButton(onClick={model.action("new.folder")},enabled=!busy&&if(external)folder=="android-other"||folder=="android-downloads"||(!folder.startsWith("android-")&&folder!="external") else folder.isEmpty()||folder !in setOf("system","system-apps","trash")&&!folder.startsWith("system-")){Icon(painterResource(app.onklav.shared.R.drawable.ref_folder_add),"Create folder",Modifier.size(28.dp))}
 FilesViewButton(grid){grid=!grid;prefs.edit().putBoolean("thumbnails",grid).apply()}
}
