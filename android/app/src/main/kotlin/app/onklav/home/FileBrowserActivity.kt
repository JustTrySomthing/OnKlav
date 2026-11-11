package app.onklav.home

import android.app.Activity
import android.content.*
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.onklav.shared.*
import kotlinx.coroutines.*
import org.json.JSONObject

internal fun filePickerAccepts(row:JSONObject,types:List<String>):Boolean{
 if(row.optBoolean("folder"))return true
 val name=row.optString("name");val reported=app.onklav.shared.Mp3Codec.mime(row.optString("mime"),name)
 if(types.any{it in MessageMedia.vCardTypes}&&MessageMedia.isVCard(reported,name))return true
 val mime=if(reported.isBlank()||reported=="application/octet-stream")android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.').lowercase())?:reported else reported
 if(name.endsWith(".layers",true)&&types.contains(PhotoLayers.MIME))return true
 return types.isEmpty()||types.any{it=="*/*"||it==mime||it.endsWith("/*")&&mime.startsWith(it.substringBefore('/')+"/")}
}

/** One shared full-page selection surface for every host screen and SDK service. */
class FileBrowserActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);FolderVaultLifecycle.install(this)
  setContent{OnKlavTheme{FileBrowserPane(intent){uris->if(uris.isEmpty())setResult(Activity.RESULT_CANCELED)else{
   val out=Intent().setData(uris.first()).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
   out.clipData=ClipData.newRawUri("Selected files",uris.first()).also{clip->uris.drop(1).forEach{clip.addItem(ClipData.Item(it))}};setResult(Activity.RESULT_OK,out)
  };finish()}}}
 }
}

@Composable internal fun FileBrowserPane(request:Intent,done:(List<Uri>)->Unit){
 val c=androidx.compose.ui.platform.LocalContext.current
 val scope=rememberCoroutineScope();val files=remember(c){FileSelectionSession(c)}
 val mode=request.getStringExtra("mode")?:"open";val multiple=request.getBooleanExtra("multiple",false)
 val types=request.getStringArrayExtra("mimes")?.toList()?:listOf("*/*")
 val initial=initialFilesFolder(types,mode=="save")
 var path by rememberSaveable{mutableStateOf(if(initial.isEmpty())arrayListOf<String>()else arrayListOf(initial))}
 val appearance=remember{c.getSharedPreferences("files-appearance",0)};var grid by remember{mutableStateOf(appearance.getBoolean("thumbnails",true))}
 var listing by remember{mutableStateOf(JSONObject())};var busy by remember{mutableStateOf(true)};var error by remember{mutableStateOf<String?>(null)}
 var query by rememberSaveable{mutableStateOf("")};var name by rememberSaveable{mutableStateOf(request.getStringExtra("name")?:"File")}
 var vaultRequest by remember{mutableStateOf<JSONObject?>(null)}
 val vaultEpoch by AppSdk.epoch.collectAsState()
 var newFolder by remember{mutableStateOf(false)};var folderName by remember{mutableStateOf("")}
 var recovered by remember{mutableStateOf(false)};var recoveryRows by remember{mutableStateOf(emptyList<JSONObject>())}
 var selected by rememberSaveable{mutableStateOf(arrayListOf<String>())}
 var selectedUris by rememberSaveable{mutableStateOf(hashMapOf<String,String>())}
 val deliver:(List<Uri>)->Unit={uris->scope.launch(Dispatchers.Main.immediate){done(uris)}}
 fun work(block:()->Unit){if(busy)return;scope.launch{busy=true;error=null;try{withContext(Dispatchers.IO){block()};listing=JSONObject(files.state.toString())}catch(e:CancellationException){throw e}catch(e:Exception){error=e.message?:"Files could not be opened";withContext(Dispatchers.IO){files.cancelPending(error.orEmpty())}}finally{busy=false}}}
 LaunchedEffect(Unit){try{AppSdk.bootstrap(c);withContext(Dispatchers.IO){files.start();if(path.isNotEmpty())files.navigate(path)else if(mode=="save"){files.event("entry:documents");path=arrayListOf("documents")};recoveryRows=FileSelectionStore.recovered(c)};listing=JSONObject(files.state.toString())}catch(e:Exception){error=e.message?:"Files could not open"}finally{busy=false}}
 DisposableEffect(files){onDispose{files.close()}}
 fun up(){if(recovered){recovered=false;return};if(path.isEmpty())done(emptyList())else{path=ArrayList(path.dropLast(1));query="";work{files.navigate(path)}}}
 BackHandler{if(busy)done(emptyList())else up()}
 val external=listing.optBoolean("external_mode")
 val folder=listing.optString("folder")
 val vaultStatus=remember(folder,vaultEpoch){if(folder.isNotEmpty())runCatching{AppSdk.folderVault(c,"status",folder)}.getOrNull()else null}
 val vaultLocked=vaultStatus?.optBoolean("locked")==true
 val selectableFolder=!vaultLocked&&!listing.optBoolean("in_trash")&&folder !in setOf("","trash","android-trash","system","system-apps","external","notes")&&!folder.startsWith("system-")
 val writable=!vaultLocked&&!listing.optBoolean("in_trash")&&folder!="android-trash"&&folder.isNotEmpty()&&folder !in setOf("trash","system","system-apps","external","notes")&&!folder.startsWith("system")&&(!folder.startsWith("android-")||folder in setOf("android-downloads","android-other"))
 Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background){Column(Modifier.safeDrawingPadding().imePadding().padding(horizontal=16.dp)){
  Row(Modifier.fillMaxWidth().heightIn(min=64.dp),verticalAlignment=Alignment.CenterVertically){
   ClosePageButton(enabled=!busy,description="Close file selection",onClick={done(emptyList())})
   Column(Modifier.weight(1f)){Text(if(recovered)"Recovered saves"else when(mode){"save"->"Save file";"folder"->"Choose folder";else->"Choose ${if(multiple)"files"else"a file"}"},style=MaterialTheme.typography.titleLarge);Text(if(external)"Android files"else"OnKlav Files",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)}
   IconButton(enabled=!busy,onClick={query="";recovered=false;work{files.navigate(if(external)listOf("external")else emptyList());path=ArrayList(if(external)listOf("external")else emptyList())}}){Icon(painterResource(app.onklav.shared.R.drawable.ref_browser_home),"Files Home")}
   FilesViewButton(grid){grid=!grid;appearance.edit().putBoolean("thumbnails",grid).apply()}
   if(writable)IconButton(enabled=!busy,onClick={newFolder=true}){Icon(painterResource(app.onklav.shared.R.drawable.ref_folder_add),"New folder")}
  }
  if(!recovered){
   Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(if(vaultLocked)"Encrypted folder"else listing.optString("title","Files").replace("OnKlav System Files","OnKlave Files"),Modifier.weight(1f).padding(horizontal=8.dp,vertical=8.dp),style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.onSurfaceVariant);if(path.isNotEmpty())TextButton(onClick={up()},enabled=!busy){Text("Parent folder")}}
   OutlinedTextField(query,{query=it},label={Text("Search files")},singleLine=true,enabled=!busy,modifier=Modifier.fillMaxWidth().testTag("file-picker-search"),shape=RoundedCornerShape(18.dp),trailingIcon={IconButton(onClick={work{files.event("query",query)}}){Icon(painterResource(app.onklav.shared.R.drawable.ref_action_search),"Search files")}})
   FilesStorageTabs(external,busy){android->query="";work{val target=if(android)listOf("external")else emptyList();files.navigate(target);path=ArrayList(target)}}
   if(path.isEmpty()&&recoveryRows.isNotEmpty())TextButton(onClick={recovered=true}){Text("Recovered saves · ${recoveryRows.size}")}
  }
  if(external)AndroidFilesPermissionPanel{work{files.event("refresh")}}
  if(busy)LinearProgressIndicator(Modifier.fillMaxWidth().padding(top=8.dp))
  error?.let{Text(it,Modifier.padding(8.dp),color=MaterialTheme.colorScheme.error);TextButton(onClick={work{if(files.state.length()==0)files.start()else files.event("refresh")}}){Text("Try again")}}
  if(vaultLocked)FolderLockedPane(vaultStatus!!){vaultRequest=vaultStatus}
  LaunchedEffect(vaultLocked){if(vaultLocked){query="";selected=arrayListOf();selectedUris=hashMapOf()}}
  val rows=if(vaultLocked)emptyList()else if(recovered)recoveryRows else listing.nodes("items").filter{it.optString("id") !in setOf("trash","android-trash","android-photos","android-apk")&&!it.optBoolean("trash")&&!(folder.isEmpty()&&it.optString("id") in setOf("system","external"))&&!(folder=="external"&&it.optString("id")=="android-other")&&FolderVaultFrames.visible(c,it.optString("id"))&&(mode=="open"||!it.optString("id").startsWith("system"))}
  fun chooseRow(row:JSONObject){
   val isFolder=row.optBoolean("folder");val key=if(recovered)row.getString("token")else row.getString("id");val chosen=selected.contains(key)

     if(isFolder){val status=AppSdk.folderVault(c,"status",key);if(status.optBoolean("locked"))vaultRequest=status.put("navigate",key)else{query="";work{files.event("entry:$key");path=ArrayList(files.breadcrumbs())}}}
     else if(chosen){selected=ArrayList(selected-key);selectedUris=HashMap(selectedUris).apply{remove(key)}}
     else work{
      val uri=if(recovered)FileSelectionStore.uri(c,row)else FileSelectionStore.selected(c,files.choose(row),path)
      if(multiple){check(selected.size<20){"Select up to 20 files at a time"};selectedUris=HashMap(selectedUris).apply{put(key,uri.toString())};selected=ArrayList(selected+key)}else deliver(listOf(uri))
     }
  }
  FilesRows(rows.map{if(recovered)JSONObject(it.toString()).put("id",it.getString("token"))else it},grid,external,busy,selected.toSet(),accept={it.optBoolean("folder")||mode!="folder"&&filePickerAccepts(it,types)},open=::chooseRow,modifier=Modifier.weight(1f).testTag("file-picker-list"))
  if(!recovered&&external&&folder=="external")TextButton(enabled=!busy,onClick={query="";work{files.event("entry:android-other");path=ArrayList(files.breadcrumbs())}},modifier=Modifier.fillMaxWidth()){Text("Internal files")}
  if(rows.isEmpty()&&!busy)Text("No files in this location",Modifier.padding(20.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)
  if(!recovered&&listing.optInt("total")>40)Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){TextButton(enabled=!busy&&listing.optInt("offset")>0,onClick={work{files.event("previous")}}){Text("Previous")};Text("${listing.optInt("offset")+1}–${minOf(listing.optInt("offset")+40,listing.optInt("total"))} of ${listing.optInt("total")}",Modifier.weight(1f),style=MaterialTheme.typography.labelSmall);TextButton(enabled=!busy&&listing.optInt("offset")+40<listing.optInt("total"),onClick={work{files.event("next")}}){Text("Next")}}
  if(mode=="save")OutlinedTextField(name,{name=it},label={Text("File name")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("file-picker-name"),shape=RoundedCornerShape(16.dp),enabled=!busy)
  Row(Modifier.fillMaxWidth().padding(vertical=12.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)){
   OutlinedButton(onClick={done(emptyList())},enabled=true,modifier=Modifier.weight(1f)){Text("Cancel")}
   if(mode!="open"||multiple)Button(enabled=!busy&&if(mode=="open")selected.isNotEmpty()else if(mode=="folder")selectableFolder else writable,modifier=Modifier.weight(1f).testTag("file-picker-confirm"),onClick={work{
    when(mode){
     "save"->{val clean=name.trim();check(clean==FilesBroker.cleanName(clean)&&clean.isNotBlank()){ "Choose a valid file name" };val uri=if(external){files.createExternal(clean,types.firstOrNull()?:"application/octet-stream")}else FileSelectionStore.save(c,path,clean,types.firstOrNull()?:"application/octet-stream");deliver(listOf(uri))}
     "folder"->deliver(listOf(FileSelectionStore.folder(c,path,listing.optString("title","Folder"))))
     else->deliver(selected.mapNotNull{selectedUris[it]?.let(Uri::parse)})
    }
   }}){Text(if(mode=="save")"Save here"else if(mode=="folder")"Use folder"else"Open (${selected.size})")}
  }
 }}
 vaultRequest?.let{request->FolderVaultDialog(request,{vaultRequest=null}){vaultRequest=null;val target=request.optString("navigate");work{if(target.isNotEmpty()){files.event("entry:$target");path=ArrayList(files.breadcrumbs())}else files.event("refresh")}}}
 if(newFolder)AlertDialog(onDismissRequest={newFolder=false},title={Text("New folder")},text={OutlinedTextField(folderName,{folderName=it},singleLine=true,label={Text("Folder name")})},confirmButton={TextButton(onClick={newFolder=false;work{files.event("new.folder");files.event("name",folderName);files.event("name.save");folderName=""}}){Text("Create")}},dismissButton={TextButton(onClick={newFolder=false}){Text("Cancel")}})
}
