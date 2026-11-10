package app.onklav.home

import app.onklav.shared.OnKlavFileContracts

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import app.onklav.shared.*
import app.onklav.shared.R
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import org.json.JSONObject
import java.util.UUID

/** A capability-scoped audiobook library/player, rendered by the same signed SDK widget model as media/maps. */
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun SdkAudioBooksComponent(node:JSONObject,model:SdkAppModel,modifier:Modifier){
 val context=LocalContext.current;val scope=rememberCoroutineScope();val widget=node.getString("id")
 val saveErrors by SdkAudioBooks.saveErrors.collectAsState()
 val mediaEpoch by SdkMedia.changes.collectAsState();val libraryEpoch by SdkAudioBooks.changes.collectAsState();val downloads by SdkAudioBookDownloadService.status.collectAsState()
 var owner by remember{mutableStateOf<JSONObject?>(null)};var books by remember{mutableStateOf(listOf<JSONObject>())};var player by remember{mutableStateOf(JSONObject())}
 var view by rememberSaveable{mutableStateOf("discover")};var selected by rememberSaveable{mutableStateOf("")};var query by rememberSaveable{mutableStateOf("")};var language by rememberSaveable{mutableStateOf("")};var byAuthor by rememberSaveable{mutableStateOf(false)}
 var libraryQuery by rememberSaveable{mutableStateOf("")};var settings by remember{mutableStateOf(false)};var genre by rememberSaveable{mutableStateOf("")}
 var filter by rememberSaveable{mutableStateOf("All")};var sort by rememberSaveable{mutableStateOf("Recent")};var page by rememberSaveable{mutableIntStateOf(0)};var resultPage by rememberSaveable{mutableIntStateOf(0)};var chapterPage by rememberSaveable{mutableIntStateOf(0)}
 var openedBook by remember{mutableStateOf<JSONObject?>(null)}
 var results by remember{mutableStateOf(listOf<JSONObject>())};var busy by remember{mutableStateOf(false)};var message by remember{mutableStateOf("")};var operation by remember{mutableStateOf("")}
 var rename by remember{mutableStateOf(false)};var remove by remember{mutableStateOf(false)};var bookmarks by rememberSaveable{mutableStateOf(false)};var editTitle by remember{mutableStateOf("")};var editAuthor by remember{mutableStateOf("")};var bookmarkDialog by remember{mutableStateOf(false)};var bookmarkLabel by remember{mutableStateOf("")}
 fun fresh():JSONObject=model.mediaOwner(widget).also{SdkAudioBooks.authorize(context,it);owner=it}
 fun work(task:suspend (JSONObject,String)->Unit){if(busy)return;busy=true;message="";val token="${owner?.optString("id")?:"app.onklav.audiobooks"}/ui/"+UUID.randomUUID();operation=token;scope.launch{try{val o=fresh();task(o,token)}catch(e:CancellationException){throw e}catch(e:Exception){message=e.message?:"Unable to complete that action. Your saved books are kept."}finally{if(operation==token){busy=false;operation=""}}}}
 suspend fun reload(o:JSONObject){books=withContext(Dispatchers.IO){SdkAudioBooks.library(context,o)}}
 LaunchedEffect(libraryEpoch,AppSdk.epoch.collectAsState().value,model.busy){if(model.busy)return@LaunchedEffect;runCatching{fresh()}.onSuccess{o->runCatching{reload(o)}.onFailure{message=it.message.orEmpty()}}.onFailure{message=it.message?:"Couldn’t open AudioBooks. Try again."}}
 LaunchedEffect(node.optLong("settings_epoch")){if(node.optLong("settings_epoch")>0)settings=true}
 LaunchedEffect(mediaEpoch,owner){owner?.let{player=SdkMedia.audiobookStatus(it)}}
 DisposableEffect(widget){onDispose{if(operation.isNotEmpty())SdkAudioBooks.cancel(operation)}}
 val componentBack by rememberUpdatedState<()->Boolean>({if(selected.isNotEmpty()){selected="";chapterPage=0;true}else false})
 DisposableEffect(model,widget){val handler:()->Boolean={componentBack()};model.componentBack=handler;onDispose{if(model.componentBack===handler)model.componentBack=null}}
 BackHandler(selected.isNotEmpty()){componentBack()}
 val files=rememberLauncherForActivityResult(OnKlavFileContracts.OpenMultipleDocuments()){uris->if(uris.isNotEmpty())work{o,t->val b=withContext(Dispatchers.IO){SdkAudioBooks.import(context,o,uris,t)};reload(o);selected=b.getString("id");view="library";message=""}}
 val folder=rememberLauncherForActivityResult(OnKlavFileContracts.OpenDocumentTree()){uri->if(uri!=null)work{o,t->val b=withContext(Dispatchers.IO){val pair=SdkAudioBooks.folder(context,uri);SdkAudioBooks.import(context,o,pair.second,t,pair.first)};reload(o);selected=b.getString("id");view="library";message=""}}
 val book=books.firstOrNull{it.getString("id")==selected}?:openedBook?.takeIf{it.optString("id")==selected};val current=owner
 fun command(op:String,args:JSONObject=JSONObject()){runCatching{val o=fresh();SdkMedia.controlFor(o,op,args);player=SdkMedia.audiobookStatus(o)}.onFailure{message=it.message?:"Playback unavailable. Try Resume or download this book."}}
 fun openCatalog(b:JSONObject){
  if(busy)return
  selected=b.getString("id");chapterPage=0;bookmarks=false
  openedBook=JSONObject(b.toString()).put("tracks",org.json.JSONArray()).put("bookmarks",org.json.JSONArray()).put("in_library",false)
  work{o,t->val added=withContext(Dispatchers.IO){SdkAudioBooks.addCatalog(context,o,b.getString("id"),t,inLibrary=false,record=b.optJSONObject("catalog_record"))};openedBook=added;books=(books.filterNot{it.optString("id")==added.optString("id")}+added)}
 }
 Column(modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(6.dp)){
  ReaderTabs(listOf("discover" to "Browse","recent" to "Recent","library" to "My Library"),view){view=it;selected="";page=0;message=""}
  val contentScroll=key(view,selected,chapterPage,bookmarks){rememberScrollState()}
  Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(contentScroll),verticalArrangement=Arrangement.spacedBy(8.dp)){
  if(busy){LinearProgressIndicator(Modifier.fillMaxWidth());TextButton(onClick={SdkAudioBooks.cancel(operation);message="Cancelling…"}){Text("Cancel")}}
  saveErrors[current?.optString("id")]?.let{Text(it,color=MaterialTheme.colorScheme.error)}
  if(message.isNotBlank()){Text(message,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.primary);if(current==null)TextButton(onClick={work{o,_->reload(o)}}){Text("Try again")}}
  if(book!=null&&current!=null){
   val key=book.getString("id");val tracks=book.getJSONArray("tracks");val progress=SdkAudioBooks.progress(context,current,key);val offline=SdkAudioBooks.offline(context,current,book)
   TextButton(onClick={selected="";chapterPage=0}){Text("‹ Back")}
   Text(book.getString("title"),style=MaterialTheme.typography.headlineSmall)
   Text(book.optString("author").ifEmpty{""},style=MaterialTheme.typography.titleMedium)
   Text("${tracks.length()} chapters"+if(tracks.length()>0&&offline==tracks.length())" · Downloaded"else"",style=MaterialTheme.typography.bodySmall)
   Text(if(progress.optBoolean("finished"))"Finished"else "${progressPercent(book,progress)}% listened",style=MaterialTheme.typography.labelLarge)
   FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp)){
    Button(enabled=!busy&&tracks.length()>0,onClick={runCatching{SdkAudioBooks.start(context,fresh(),key);player=SdkMedia.audiobookStatus(fresh())}.onFailure{message=it.message.orEmpty()}}){Text(if(progress.optBoolean("finished"))"Listen again"else if(progress.optLong("updated")==0L)"Play"else"Resume")}
    if(!book.optBoolean("in_library",true))OutlinedButton(enabled=!busy&&tracks.length()>0,onClick={work{o,_->withContext(Dispatchers.IO){SdkAudioBooks.keep(context,o,key)};reload(o)}}){Text("Add to My Library")}
    if(book.optString("source")=="LibriVox"&&offline<tracks.length())OutlinedButton(enabled=!busy,onClick={runCatching{val o=fresh();SdkAudioBooks.keep(context,o,key);SdkAudioBookDownloadService.start(context,o,key)}.onFailure{message=it.message.orEmpty()}}){Text(if(offline>0)"Resume download"else"Download book")}
    var options by remember(key){mutableStateOf(false)}
    Box{TextButton(enabled=tracks.length()>0,onClick={options=true}){Text("Book options")};DropdownMenu(options,{options=false}){
     DropdownMenuItem(text={Text("Edit details")},enabled=!busy,onClick={options=false;editTitle=book.getString("title");editAuthor=book.optString("author");rename=true})
     DropdownMenuItem(text={Text(if(progress.optBoolean("finished"))"Mark unfinished"else"Mark finished")},enabled=!busy,onClick={options=false;work{o,_->if(SdkMedia.audiobookStatus(o).optInt("count")>0)SdkMedia.controlFor(o,"stop");withContext(Dispatchers.IO){SdkAudioBooks.finished(context,o,key,!progress.optBoolean("finished"))};reload(o)}})
     DropdownMenuItem(text={Text("Remove book")},enabled=!busy,onClick={options=false;remove=true})
    }}
   }
   val downloadKey=SdkAudioBooks.downloadToken(current,key);downloads[downloadKey]?.let{Text(it,style=MaterialTheme.typography.bodySmall);if(!it.startsWith("Available")&&!it.startsWith("Stopped"))TextButton(onClick={SdkAudioBooks.cancelDownload(current.getString("id"),key)}){Text("Cancel download")}}
   FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
    FilterChip(selected=!bookmarks,onClick={bookmarks=false;chapterPage=0},label={Text("Chapters")})
    FilterChip(selected=bookmarks,onClick={bookmarks=true;chapterPage=0},label={Text("Bookmarks")})
    TextButton(onClick={bookmarkLabel="";bookmarkDialog=true}){Text("Add bookmark")}
   }
   val chapterRows=if(bookmarks)(0 until book.getJSONArray("bookmarks").length()).map{book.getJSONArray("bookmarks").getJSONObject(it)}else SdkAudioBookChapters.rows(book)
   if(chapterRows.isEmpty())Text(if(bookmarks)"No bookmarks yet"else if(busy)"Loading chapters…"else"Chapters unavailable")
   if(tracks.length()==0&&!busy)TextButton(onClick={openCatalog(book)}){Text("Retry chapters")}
   for(row in chapterRows.drop(chapterPage*20).take(20)){
    val ix=row.getInt("index");val pos=row.optLong("position_ms")
    Surface(tonalElevation=1.dp,shape=MaterialTheme.shapes.medium){Column(Modifier.fillMaxWidth().padding(10.dp)){
     TextButton(enabled=!busy,onClick={runCatching{SdkAudioBooks.start(context,fresh(),key,ix,pos);player=SdkMedia.audiobookStatus(fresh())}.onFailure{message=it.message.orEmpty()}}){Text(if(bookmarks)row.optString("label")else row.optString("title"))}
     Text("File ${ix+1} · ${audioTime(pos)}",style=MaterialTheme.typography.labelSmall)
     if(bookmarks)TextButton(enabled=!busy,onClick={work{o,_->withContext(Dispatchers.IO){SdkAudioBooks.deleteBookmark(context,o,key,row.getString("id"))};reload(o)}}){Text("Delete bookmark")}
     else if(book.optString("source")=="Imported"&&pos==0L&&tracks.length()>1)FlowRow{if(ix>0)TextButton(enabled=!busy,onClick={work{o,_->withContext(Dispatchers.IO){SdkAudioBooks.reorder(context,o,key,ix,ix-1)};reload(o)}}){Text("Move up")};if(ix+1<tracks.length())TextButton(enabled=!busy,onClick={work{o,_->withContext(Dispatchers.IO){SdkAudioBooks.reorder(context,o,key,ix,ix+1)};reload(o)}}){Text("Move down")}}
    }}
   }
   FlowRow{if(chapterPage>0)TextButton(onClick={chapterPage--}){Text("Previous")};if((chapterPage+1)*20<chapterRows.size)TextButton(onClick={chapterPage++}){Text("More")}}

  }else if(view=="library"||view=="recent"){
   if(view=="library"){
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
     OutlinedButton(enabled=!busy&&current!=null,onClick={files.launch(arrayOf("audio/*","video/mp4","application/octet-stream"))},modifier=Modifier.weight(1f)){Text("Add files")}
     OutlinedButton(enabled=!busy&&current!=null,onClick={folder.launch(null)},modifier=Modifier.weight(1f)){Text("Add folder")}
    }
    BookSearchField(libraryQuery,{libraryQuery=it.take(100);page=0},"Search your library")
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)){for(f in listOf("All","In progress","Finished","Offline"))FilterChip(selected=filter==f,onClick={filter=f;page=0},label={Text(f)})}
   }
   val shown=if(current==null)emptyList()else books.filter{b->val p=SdkAudioBooks.progress(context,current,b.getString("id"));(if(view=="recent")p.optLong("updated")>0 else b.optBoolean("in_library",true)&&(b.getString("title")+" "+b.optString("author")).contains(libraryQuery,true))&&(view=="recent"||when(filter){"Finished"->p.optBoolean("finished");"In progress"->!p.optBoolean("finished")&&p.optLong("updated")>0;"Offline"->SdkAudioBooks.offline(context,current,b)==b.getJSONArray("tracks").length();else->true})}.let{rows->when(if(view=="recent")"Recent"else sort){"Title"->rows.sortedBy{it.getString("title").lowercase()};"Author"->rows.sortedBy{it.optString("author").lowercase()};else->rows.sortedByDescending{SdkAudioBooks.progress(context,current,it.getString("id")).optLong("updated",it.optLong("added"))}}}
   for(b in shown.drop(page*20).take(20)){val p=SdkAudioBooks.progress(context,current!!,b.getString("id"));Card(onClick={selected=b.getString("id");chapterPage=0;bookmarks=false},modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){Text(b.getString("title"),style=MaterialTheme.typography.titleMedium);Text(b.optString("author").ifEmpty{b.optString("source")},style=MaterialTheme.typography.bodySmall);LinearProgressIndicator(progress={progressPercent(b,p)/100f},modifier=Modifier.fillMaxWidth());Text(if(p.optBoolean("finished"))"Finished"else "${progressPercent(b,p)}% listened",style=MaterialTheme.typography.labelMedium)}}}
   if(shown.isEmpty()){Text(if(view=="recent")"No recent audiobooks"else"No books in this view");TextButton(onClick={view="discover";selected="";message=""}){Text("Browse audiobooks")}}
   FlowRow{if(page>0)TextButton(onClick={page--}){Text("Previous books")};if((page+1)*20<shown.size)TextButton(onClick={page++}){Text("More books")}}
  }else{
   Text("LibriVox",style=MaterialTheme.typography.titleLarge)
   BookSearchField(query,{query=it.take(100);resultPage=0},if(byAuthor)"Search authors"else"Search audiobooks")
   Row{FilterChip(selected=!byAuthor,onClick={byAuthor=false},label={Text("Title")});Spacer(Modifier.width(8.dp));FilterChip(selected=byAuthor,onClick={byAuthor=true},label={Text("Author")})}
   fun search(offset:Int,category:String=genre){work{o,t->results=withContext(Dispatchers.IO){SdkAudioBooks.discover(context,o,query,byAuthor,offset,t,category)};resultPage=offset/20;message=if(results.isEmpty())"No recordings found. Try another title or author."else""}}
   Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)){for((key,text)in listOf("" to "All","Fiction" to "Fiction","Mystery" to "Mystery","History" to "History","Children" to "Children"))FilterChip(selected=genre==key,onClick={genre=key;search(0,key)},label={Text(text)})}
   Button(enabled=!busy&&current!=null,onClick={search(0)},modifier=Modifier.fillMaxWidth().heightIn(min=56.dp),shape=RoundedCornerShape(16.dp)){Text(if(query.isBlank())"Browse LibriVox"else"Search LibriVox")}
   for(b in results.filter{language.isBlank()||it.optString("language").contains(language,true)})Card(onClick={openCatalog(b)},modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){Text(b.getString("title"),style=MaterialTheme.typography.titleMedium);Text(b.optString("author"));Text("${b.optString("language")} · ${b.optString("duration")}",style=MaterialTheme.typography.bodySmall);Text("View chapters",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)}}
   FlowRow{if(resultPage>0)TextButton(enabled=!busy,onClick={search((resultPage-1)*20)}){Text("Previous results")};if(results.size==20)TextButton(enabled=!busy,onClick={search((resultPage+1)*20)}){Text("More results")}}
  }
  }
  if(player.optInt("count")>0)AudioBooksPlayer(player,::command,{bookmarkLabel="";bookmarkDialog=true})
 }
 if(settings)ReferenceDialog("AudioBooks settings",{settings=false},footer={DialogAction("Done"){settings=false}}){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
  Text("Source: LibriVox",style=MaterialTheme.typography.titleMedium)
  TextButton(onClick={settings=false;files.launch(arrayOf("audio/*","video/mp4","application/octet-stream"))}){Text("Add audio files")}
  TextButton(onClick={settings=false;folder.launch(null)}){Text("Add a folder")}
  OutlinedTextField(language,{language=it.take(80)},label={Text("Browse language")},singleLine=true)
  TextButton(onClick={sort=when(sort){"Recent"->"Title";"Title"->"Author";else->"Recent"}}){Text("Library order: $sort")}
 }}
 if(rename&&book!=null)AlertDialog(onDismissRequest={rename=false},title={Text("Book details")},text={Column{OutlinedTextField(editTitle,{editTitle=it.take(200)},label={Text("Title")});OutlinedTextField(editAuthor,{editAuthor=it.take(200)},label={Text("Author")})}},confirmButton={TextButton(enabled=editTitle.isNotBlank(),onClick={rename=false;work{o,_->withContext(Dispatchers.IO){SdkAudioBooks.rename(context,o,book.getString("id"),editTitle,editAuthor)};reload(o)}}){Text("Save")}},dismissButton={TextButton(onClick={rename=false}){Text("Cancel")}})
 if(remove&&book!=null)AlertDialog(onDismissRequest={remove=false},title={Text("Remove this book?")},text={Text("Remove the library entry, listening progress, bookmarks and the copies saved in AudioBooks. Your original files stay untouched.")},confirmButton={TextButton(onClick={remove=false;work{o,_->withContext(Dispatchers.IO){SdkAudioBooks.remove(context,o,book.getString("id"))};selected="";reload(o)}}){Text("Remove")}},dismissButton={TextButton(onClick={remove=false}){Text("Keep book")}})
 if(bookmarkDialog){val key=book?.getString("id")?:player.optString("book");AlertDialog(onDismissRequest={bookmarkDialog=false},title={Text("Save a bookmark")},text={OutlinedTextField(bookmarkLabel,{bookmarkLabel=it.take(120)},label={Text("Label (optional)")})},confirmButton={TextButton(enabled=key.isNotEmpty(),onClick={bookmarkDialog=false;work{o,_->val snapshot=SdkMedia.audiobookStatus(o);val p=if(snapshot.optString("book")==key)snapshot else SdkAudioBooks.progress(context,o,key);withContext(Dispatchers.IO){SdkAudioBooks.bookmark(context,o,key,p.optInt("index"),p.optLong("position_ms"),bookmarkLabel)};reload(o);message="Bookmark saved"}}){Text("Save")}},dismissButton={TextButton(onClick={bookmarkDialog=false}){Text("Cancel")}})}
}
internal fun audioTime(ms:Long):String {val sec=(ms/1000).coerceAtLeast(0);return if(sec>=3600)"${sec/3600}:${(sec/60%60).toString().padStart(2,'0')}:${(sec%60).toString().padStart(2,'0')}"else "${sec/60}:${(sec%60).toString().padStart(2,'0')}"}
internal fun progressPercent(b:JSONObject,p:JSONObject):Int {if(p.optBoolean("finished"))return 100;val tracks=b.getJSONArray("tracks");val ix=p.optInt("index").coerceIn(0,(tracks.length()-1).coerceAtLeast(0));val durations=(0 until tracks.length()).map{tracks.getJSONObject(it).optLong("duration_ms")};val total=durations.sum();return if(total>0)((durations.take(ix).sum()+p.optLong("position_ms")).coerceAtMost(total)*100/total).toInt().coerceIn(0,100)else if(tracks.length()>0)ix*100/tracks.length()else 0}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun AudioBooksPlayer(row:JSONObject,command:(String,JSONObject)->Unit,bookmark:()->Unit){
 var slider by remember{mutableFloatStateOf(0f)};var dragging by remember{mutableStateOf(false)};var timer by remember{mutableStateOf(false)};var speeds by remember{mutableStateOf(false)}
 val pos=row.optLong("position_ms");val duration=row.optLong("duration_ms");val state=row.optString("state");val ready=state in setOf("playing","paused","ended","error")
 Surface(tonalElevation=4.dp,shape=MaterialTheme.shapes.large){Column(Modifier.fillMaxWidth().padding(horizontal=10.dp,vertical=6.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
  Text(row.optString("title"),style=MaterialTheme.typography.titleMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
  Text(when(state){"preparing"->"Loading chapter…";"error"->"Couldn’t play this chapter · Retry Play";else->"File ${row.optInt("index")+1}/${row.optInt("count")} · ${audioTime(pos)} / ${audioTime(duration)}"},style=MaterialTheme.typography.bodySmall)
  Slider(value=if(dragging)slider else pos.toFloat().coerceIn(0f,duration.coerceAtLeast(1).toFloat()),onValueChange={slider=it;dragging=true},onValueChangeFinished={command("seek",JSONObject().put("position_ms",slider.toLong()));dragging=false},valueRange=0f..duration.coerceAtLeast(1).toFloat(),enabled=ready&&duration>0,modifier=Modifier.fillMaxWidth().semantics{contentDescription="Audiobook position"})
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically){
   IconButton(enabled=ready&&duration>0,onClick={command("seek",JSONObject().put("position_ms",(pos-15000).coerceAtLeast(0)))},modifier=Modifier.size(48.dp).semantics{contentDescription="Back 15 seconds"}){Text("−15")}
   IconButton(enabled=state!="preparing",onClick={command(if(state=="playing")"pause"else"play",JSONObject())},modifier=Modifier.size(48.dp)){Icon(painterResource(if(state=="playing")R.drawable.ref_radio_pause else R.drawable.ref_radio_play),if(state=="playing")"Pause audiobook"else"Play audiobook",Modifier.size(26.dp),tint=MaterialTheme.colorScheme.primary)}
   IconButton(enabled=ready&&duration>0,onClick={command("seek",JSONObject().put("position_ms",(pos+30000).coerceAtMost(duration)))},modifier=Modifier.size(48.dp).semantics{contentDescription="Forward 30 seconds"}){Text("+30")}
   Box{TextButton(enabled=ready,onClick={speeds=true},modifier=Modifier.width(48.dp),contentPadding=PaddingValues(0.dp)){Text("${row.optDouble("speed",1.0)}×")};DropdownMenu(speeds,{speeds=false}){for(speed in listOf(0.5,0.75,1.0,1.25,1.5,1.75,2.0,2.5,3.0))DropdownMenuItem(text={Text("$speed×")},onClick={speeds=false;command("speed",JSONObject().put("speed",speed))})}}
   Box{IconButton(enabled=ready,onClick={timer=true},modifier=Modifier.size(48.dp)){Icon(painterResource(R.drawable.ref_radio_sleep),"Sleep timer",Modifier.size(24.dp))};DropdownMenu(timer,{timer=false}){for(n in listOf(0,15,30,45,60,90,120))DropdownMenuItem(text={Text(if(n==0)"Sleep off"else"Stop in $n minutes")},onClick={timer=false;command("sleep",JSONObject().put("minutes",n))});DropdownMenuItem(text={Text("End of chapter")},onClick={timer=false;command("sleep.chapter",JSONObject())})}}
   var more by remember{mutableStateOf(false)}
   Box{IconButton(onClick={more=true},modifier=Modifier.size(48.dp)){Icon(painterResource(R.drawable.ref_browser_more),"Playback options",Modifier.size(24.dp))};DropdownMenu(more,{more=false}){
    DropdownMenuItem(text={Text("Add bookmark")},enabled=ready,onClick={more=false;bookmark()})
    DropdownMenuItem(text={Text("Previous chapter")},enabled=ready,onClick={more=false;command("previous",JSONObject())})
    DropdownMenuItem(text={Text("Next chapter")},enabled=ready,onClick={more=false;command("next",JSONObject())})
    DropdownMenuItem(text={Text("Stop")},onClick={more=false;command("stop",JSONObject())})
   }}
  }
 }}
}

@Composable private fun BookSearchField(value:String,change:(String)->Unit,hint:String){
 TextField(value,change,placeholder={Text(hint)},singleLine=true,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp),colors=TextFieldDefaults.colors(focusedIndicatorColor=Color.Transparent,unfocusedIndicatorColor=Color.Transparent,focusedContainerColor=MaterialTheme.colorScheme.surface,unfocusedContainerColor=MaterialTheme.colorScheme.surface))
}
