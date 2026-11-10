package app.onklav.home

import android.app.Application
import android.content.*
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.Spanned
import androidx.compose.ui.semantics.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.*
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import app.onklav.shared.*
import app.onklav.shared.R
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID

/** Host and SDK files.open share this activity; it accepts no filesystem paths or remote URLs. */
class DocuViewActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);FolderVaultLifecycle.install(this)
  setContent{OnKlavTheme{DocuViewWorkspace({finish()},initial=intent.data)}}
 }
}
internal class DocuViewModel(app:Application):AndroidViewModel(app){
 var name by mutableStateOf("");private set
 var document by mutableStateOf<ReadingDocument?>(null);internal set
 var busy by mutableStateOf(false);private set
 var message by mutableStateOf("");private set
 var page by mutableIntStateOf(0);private set
 var pages by mutableIntStateOf(0);private set
 var bitmap by mutableStateOf<Bitmap?>(null);private set
 var pdfText by mutableStateOf<String?>(null);private set
 var pdfBounds by mutableStateOf<List<android.graphics.RectF>>(emptyList());private set
 var pdfSize by mutableStateOf(1 to 1);private set
 var textSupported by mutableStateOf(false);private set
 var sheet by mutableIntStateOf(0)
 var font by mutableFloatStateOf(18f)
 var query by mutableStateOf("")
 private var source:Uri?=null
 private var file:File?=null
 private var job:Job?=null
 private val root=File(app.cacheDir,"docu-view-${UUID.randomUUID()}").apply{mkdirs()}
 private var findQuery="";private var findPage=0;private var findAfter=0
 val isPdf get()=pages>0
 init{app.cacheDir.listFiles()?.filter{it.isDirectory&&it.name.startsWith("docu-view-")&&it!=root&&System.currentTimeMillis()-it.lastModified()>86_400_000}?.forEach{it.deleteRecursively()}}
 private fun authorize(){source?.let{getApplication<Application>().contentResolver.openFileDescriptor(it,"r")?.use{}?:error("File access was removed. Open the file again.")}}
 private fun task(block:suspend ()->Unit){if(busy)return;busy=true;message="";job=viewModelScope.launch{try{block()}catch(e:CancellationException){message="Cancelled";throw e}catch(e:LinkageError){message="This document contains an unsupported embedded feature. Try a PDF copy."}catch(e:Exception){message=when(e){is SecurityException->"File is locked, password-protected, or access was removed. Choose it again after unlocking.";else->e.message?.take(240)?:"This document could not be opened."}}finally{busy=false;job=null}}}
 fun share(){val uri=source?:return;task{withContext(Dispatchers.IO){authorize()};val c=getApplication<Application>();val mime=c.contentResolver.getType(uri)?:"application/octet-stream";c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply{clipData=ClipData.newRawUri(name,uri)},"Share document").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}}
 fun cancel(){job?.cancel()}
 fun clear(){job?.cancel();job=null;busy=false;bitmap=null;document=null;file?.delete();file=null;source=null;pages=0;page=0;name="";message="";query="";sheet=0;pdfText=null;pdfBounds=emptyList()}
 fun checkAccess(){val candidate=source?:return;val selected=file
  viewModelScope.launch{try{withContext(Dispatchers.IO){getApplication<Application>().contentResolver.openFileDescriptor(candidate,"r")?.use{}?:error("File access was removed.")}}
   catch(e:Exception){if(e is CancellationException)throw e;if(source==candidate&&file==selected){clear();message="File is locked or access was removed. Choose it again after unlocking."}}}
 }
 fun dismissPdfText(){pdfText=null}
 fun open(uri:Uri){if(busy)return;clear();task{
  val c=getApplication<Application>();val temp=File(root,"selected");var title="Document";var accepted=false
  try{withContext(Dispatchers.IO){require(uri.scheme=="content"){"Choose a document in Files."}
   c.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE),null,null,null)?.use{q->if(q.moveToFirst()){val n=q.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(n>=0)title=q.getString(n).orEmpty().take(180)}}
   val mime=c.contentResolver.getType(uri).orEmpty();if(title.substringAfterLast('.',"").lowercase() !in DocumentReading.extensions){val ext=android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);if(ext!=null&&ext in DocumentReading.extensions)title+=".$ext"}
   require(title.substringAfterLast('.',"").lowercase() in DocumentReading.extensions){"Choose PDF, OpenDocument, Word, text, RTF or a spreadsheet."}
   val activeJob=currentCoroutineContext()[Job];val limit=if(title.endsWith(".pdf",true))SdkPdfProtocol.MAX_FILE else DocumentReading.MAX_BYTES
   c.contentResolver.openInputStream(uri)?.use{input->temp.outputStream().use{out->FilesBroker.copyBoundedLimit(input,out,limit){activeJob?.ensureActive()}}}?:error("File unavailable.")
  };source=uri;file=temp;name=title;sheet=0;findQuery=""
   if(title.endsWith(".pdf",true)){val r=pdf("info");pages=r.data.getInt("pages");textSupported=r.data.getBoolean("text_supported");loadPage(0)}else document=withContext(Dispatchers.IO){DocumentReading.read(temp,title)}
   withContext(Dispatchers.IO){authorize()};accepted=true
  }finally{if(!accepted){temp.delete();file=null;source=null;name="";pages=0;bitmap=null;document=null}}
 }}
 private suspend fun pdf(op:String,extra:Bundle=Bundle()):PdfReply {val f=file?:error("Open a document first.");return SdkPdfClient.selected(getApplication(),f,Bundle(extra).apply{putString("op",op)}){authorize()}}
 private suspend fun loadPage(index:Int){pdfText=null;val result=pdf("render",Bundle().apply{putInt("page",index);putInt("render_width",1600)});page=index;pdfSize=result.data.getInt("width") to result.data.getInt("height");bitmap=result.bitmap}
 fun go(index:Int){if(index !in 0 until pages)return;task{pdfBounds=emptyList();loadPage(index)}}
 fun showText(){if(!textSupported)return;task{pdfText=pdf("text",Bundle().apply{putInt("page",page)}).data.getString("text").orEmpty().ifBlank{"No selectable text on this page. Scanned pages need OCR."}}}
 fun findPdf(){val text=query.trim().take(128);if(text.isBlank())return;task{
  if(text!=findQuery){findQuery=text;findPage=0;findAfter=0}
  while(findPage<pages){currentCoroutineContext().ensureActive();message="Searching page ${findPage+1} of $pages"
   val r=pdf("search",Bundle().apply{putInt("page",findPage);putInt("after",findAfter);putString("query",text)})
   @Suppress("DEPRECATION") val found=r.data.getParcelableArrayList<Bundle>("matches").orEmpty()
   if(found.isNotEmpty()){val first=found.first();val target=first.getInt("page");if(target!=findPage)findAfter=0;findPage=target;findAfter++
    @Suppress("DEPRECATION") val bounds=first.getParcelableArrayList<Bundle>("bounds").orEmpty().mapNotNull{it.getFloatArray("rect")?.let{r->android.graphics.RectF(r[0],r[1],r[2],r[3])}}
    loadPage(target);pdfBounds=bounds;message="Match on page ${target+1}";return@task
   };findPage=r.data.getInt("next_page",-1);findAfter=r.data.getInt("next_match");if(findPage<0)break
  };findPage=0;findAfter=0;message="End of document · tap Find next to search from the beginning."
 }}
 override fun onCleared(){job?.cancel();root.deleteRecursively();super.onCleared()}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun DocuViewWorkspace(exit:()->Unit,model:DocuViewModel=viewModel(),initial:Uri?=null){
 val c=androidx.compose.ui.platform.LocalContext.current
 val epoch by AppSdk.epoch.collectAsState()
 LaunchedEffect(epoch){model.checkAccess()}
 val open=rememberLauncherForActivityResult(OnKlavFileContracts.OpenDocument()){it?.let(model::open)}
 var find by remember{mutableStateOf(false)};var info by remember{mutableStateOf(false)};var jump by remember{mutableStateOf(false)};var pageInput by remember{mutableStateOf("")};var zoom by remember{mutableFloatStateOf(1f)}
 val back:()->Unit={if(model.busy)model.cancel()else if(model.name.isNotEmpty()){model.clear();find=false}else exit()}
 BackHandler(onBack=back)
 LaunchedEffect(initial){if(initial!=null&&model.name.isBlank())model.open(initial)}
 LifecycleEventEffect(Lifecycle.Event.ON_RESUME){model.checkAccess()}
 Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background){Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)){
  HomeHeader("Docu View",back=back)
  if(model.name.isBlank()&&!model.busy){Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)){
   Icon(androidx.compose.ui.res.painterResource(R.drawable.ref_app_docu_view),null,Modifier.size(56.dp),tint=MaterialTheme.colorScheme.primary)
   Text("Your documents.\nEasy to read.",style=MaterialTheme.typography.headlineLarge)
   Text("Open a document",style=MaterialTheme.typography.titleLarge)
   Text("PDF · Word · OpenDocument · Text · Spreadsheets",color=MaterialTheme.colorScheme.onSurfaceVariant)
   Button(onClick={open.launch(arrayOf("*/*"))},Modifier.fillMaxWidth().heightIn(min=52.dp)){Text("Choose file")}
   Text("PDF, ODF / ODT, TXT, DOC, DOCX, RTF\nODS, XLSX, XLS, CSV / TSV",style=MaterialTheme.typography.bodySmall)
   Text("Private · offline · original stays unchanged",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
   if(model.message.isNotEmpty())Text(model.message,color=MaterialTheme.colorScheme.error)
  }}else{
   Column(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
    Text(model.name.ifBlank{"Opening document…"},style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
     TextButton(onClick={open.launch(arrayOf("*/*"))},enabled=!model.busy){Text("Open")}
     TextButton(onClick={find=!find},enabled=!model.busy&&(!model.isPdf||model.textSupported)){Text("Find")}
     TextButton(onClick=model::share,enabled=!model.busy){Text("Share")}
     TextButton(onClick={info=true},enabled=!model.busy){Text("Details")}
     if(!model.isPdf){TextButton(onClick={model.font=(model.font-2).coerceAtLeast(12f)}){Text("A−")};TextButton(onClick={model.font=(model.font+2).coerceAtMost(32f)}){Text("A+")}}
     else {TextButton(onClick={zoom=(zoom-.5f).coerceAtLeast(1f)}){Text("−",modifier=Modifier.semantics{contentDescription="Zoom out"})};TextButton(onClick={zoom=(zoom+.5f).coerceAtMost(4f)}){Text("+",modifier=Modifier.semantics{contentDescription="Zoom in"})};if(model.textSupported)TextButton(onClick=model::showText,enabled=!model.busy){Text("Page text")}}
    }
    if(find){OutlinedTextField(model.query,{model.query=it.take(128)},Modifier.fillMaxWidth(),singleLine=true,label={Text("Find in document")},trailingIcon={if(model.isPdf)TextButton(onClick=model::findPdf,enabled=!model.busy&&model.query.isNotBlank()){Text("Next")}})}
   }
   if(model.busy){LinearProgressIndicator(Modifier.fillMaxWidth());TextButton(onClick=model::cancel){Text("Cancel")}}
   if(model.message.isNotEmpty()){Text(model.message,Modifier.padding(horizontal=16.dp,vertical=4.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)}
   if(model.isPdf){Box(Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer)){model.bitmap?.let{bitmap->AndroidView(factory={PdfReadingView(it)},update={it.show(bitmap,model.pdfBounds,model.pdfSize,zoom)},modifier=Modifier.fillMaxSize())}}
    Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){TextButton(onClick={model.go(model.page-1);zoom=1f},enabled=!model.busy&&model.page>0){Text("Previous")};TextButton(onClick={pageInput=(model.page+1).toString();jump=true}){Text("${model.page+1} / ${model.pages}")};TextButton(onClick={model.go(model.page+1);zoom=1f},enabled=!model.busy&&model.page+1<model.pages){Text("Next")}}
   }else model.document?.let{doc->ReadingContent(doc,model.font,model.query,model.sheet,{model.sheet=it},Modifier.weight(1f))}
  }
 }}
 if(info)AlertDialog(onDismissRequest={info=false},title={Text("Document details")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){Text(model.name);Text(if(model.isPdf)"Original PDF pages · pinch to zoom, drag to pan, double-tap to fit. PDFs up to 50 MB and 2,000 pages." else model.document?.notice.orEmpty().ifBlank{"Read-only text view"});if(model.isPdf&&!model.textSupported)Text("PDF search and selectable page text need Android 15 or later. Scanned pages need OCR, which is not included.")}},confirmButton={TextButton(onClick={info=false}){Text("Done")}})
 if(jump)AlertDialog(onDismissRequest={jump=false},title={Text("Go to page")},text={OutlinedTextField(pageInput,{pageInput=it.filter(Char::isDigit).take(4)},label={Text("1–${model.pages}")},singleLine=true,keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Number))},confirmButton={TextButton(onClick={pageInput.toIntOrNull()?.let{model.go(it-1)};jump=false},enabled=pageInput.toIntOrNull() in 1..model.pages){Text("Go")}},dismissButton={TextButton(onClick={jump=false}){Text("Cancel")}})
 model.pdfText?.let{text->AlertDialog(onDismissRequest={model.dismissPdfText()},title={Text("Page ${model.page+1} text")},text={androidx.compose.foundation.text.selection.SelectionContainer{Text(text,Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState()))}},confirmButton={TextButton(onClick={model.dismissPdfText()}){Text("Done")}})}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun ReadingContent(doc:ReadingDocument,font:Float,query:String,sheet:Int,onSheet:(Int)->Unit,modifier:Modifier=Modifier){
 val density=androidx.compose.ui.platform.LocalDensity.current.density;val state=rememberLazyListState();val horizontal=rememberScrollState();var match by remember(doc,sheet,query){mutableIntStateOf(0)};var selectedCell by remember(doc,sheet){mutableStateOf<Pair<String,String>?>(null)}
 val table=doc.sheets.getOrNull(sheet);val blocks=remember(doc){doc.text.chunked(5000).ifEmpty{listOf("")}}
 val hits=remember(doc,sheet,query){if(query.isBlank())emptyList()else if(table!=null)table.rows.flatMapIndexed{ri,row->row.mapIndexedNotNull{ci,s->if(s.contains(query,true))ri to ci else null}}.take(1000)else buildList{var at=doc.text.indexOf(query,ignoreCase=true);while(at>=0&&size<1000){add(at/5000 to 0);at=doc.text.indexOf(query,at+query.length,true)}}}
 LaunchedEffect(hits,match){hits.getOrNull(match)?.let{(row,col)->state.animateScrollToItem(row+if(table!=null)1 else 0);if(table!=null)horizontal.animateScrollTo((col*150*density).toInt())}}
 Column(modifier.fillMaxWidth()){
  if(doc.notice.isNotBlank())Text(doc.notice,Modifier.padding(horizontal=16.dp,vertical=4.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis)
  if(doc.sheets.isNotEmpty()){Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){doc.sheets.forEachIndexed{i,s->FilterChip(selected=i==sheet,onClick={onSheet(i)},label={Text(s.name.ifBlank{"Sheet ${i+1}"})})}}}
  if(query.isNotBlank())Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically){Text(if(hits.isEmpty())"No matches" else "${match+1} / ${hits.size}${if(hits.size==1000)"+" else ""}",Modifier.weight(1f),style=MaterialTheme.typography.labelMedium);TextButton(onClick={match=(match-1+hits.size)%hits.size},enabled=hits.isNotEmpty()){Text("Previous")};TextButton(onClick={match=(match+1)%hits.size},enabled=hits.isNotEmpty()){Text("Next")}}
  if(table!=null){val columns=remember(table){table.rows.maxOfOrNull{it.size}?:0}
   Box(Modifier.weight(1f).fillMaxWidth().horizontalScroll(horizontal)){
    LazyColumn(state=state,modifier=Modifier.width((48+columns*150).dp)){
     stickyHeader{Row(Modifier.background(MaterialTheme.colorScheme.secondaryContainer)){Text("#",Modifier.width(48.dp).padding(8.dp),fontSize=14.sp);repeat(columns){ci->Text(columnLabel(ci),Modifier.width(150.dp).padding(8.dp),fontSize=14.sp)}}}
     itemsIndexed(table.rows){ri,row->Row(Modifier.background(if(ri%2==0)MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerLow)){
      Text("${ri+1}",Modifier.width(48.dp).padding(8.dp),fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
      repeat(columns){ci->val value=row.getOrNull(ci).orEmpty();androidx.compose.foundation.text.selection.SelectionContainer{Text(value,Modifier.width(150.dp).clickable{selectedCell=(columnLabel(ci)+(ri+1)) to value}.background(if(query.isNotBlank()&&value.contains(query,true))MaterialTheme.colorScheme.tertiaryContainer else androidx.compose.ui.graphics.Color.Transparent).padding(8.dp),fontSize=font.sp,maxLines=8,overflow=TextOverflow.Ellipsis)}}
     };HorizontalDivider()}
    }
   }
  }else if(doc.text.isBlank()){Box(Modifier.fillMaxSize().padding(24.dp)){Text("No readable text found. Embedded images and scanned content may require another viewer.")}}
  else LazyColumn(state=state,modifier=Modifier.fillMaxWidth().weight(1f),contentPadding=PaddingValues(16.dp)){
   itemsIndexed(blocks){i,text->val color=MaterialTheme.colorScheme.onSurface.toArgb();val highlight=MaterialTheme.colorScheme.tertiaryContainer.toArgb()
    AndroidView(factory={android.widget.TextView(it).apply{setTextIsSelectable(true);setPadding(0,0,0,12);setLineSpacing(0f,1.2f);importantForAutofill=android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS}},update={view->val styled=android.text.SpannableStringBuilder(doc.styled?.subSequence(i*5000,(i*5000+text.length).coerceAtMost(doc.text.length))?:text);if(query.isNotBlank()){var at=text.indexOf(query,ignoreCase=true);var n=0;while(at>=0&&n++<1000){styled.setSpan(android.text.style.BackgroundColorSpan(highlight),at,at+query.length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);at=text.indexOf(query,at+query.length,true)}};styled.getSpans(0,styled.length,android.text.style.AbsoluteSizeSpan::class.java).forEach{span->val a=styled.getSpanStart(span);val b=styled.getSpanEnd(span);styled.removeSpan(span);styled.setSpan(android.text.style.AbsoluteSizeSpan((span.size*font/18f).toInt().coerceAtLeast(8),true),a,b,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)};view.text=styled;view.textSize=font;view.setTextColor(color)},modifier=Modifier.fillMaxWidth())
   }
  }
 }
 selectedCell?.let{(address,value)->AlertDialog(onDismissRequest={selectedCell=null},title={Text(address)},text={androidx.compose.foundation.text.selection.SelectionContainer{Text(value.ifBlank{"Empty cell"},Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState()))}},confirmButton={TextButton(onClick={selectedCell=null}){Text("Done")}})}
}
internal fun columnLabel(index:Int):String {var n=index+1;var s="";while(n>0){n--;s=('A'+n%26)+s;n/=26};return s}
