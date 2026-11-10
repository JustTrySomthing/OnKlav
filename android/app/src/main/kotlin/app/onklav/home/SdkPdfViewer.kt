package app.onklav.home

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.onklav.shared.ReferenceDialog
import app.onklav.shared.DialogAction
import kotlinx.coroutines.*
import org.json.JSONObject

@Suppress("DEPRECATION") private fun Bundle.rectangles():List<FloatArray> = getParcelableArrayList<Bundle>("bounds").orEmpty().mapNotNull{it.getFloatArray("rect")}
@Composable internal fun SdkPdfViewer(node:JSONObject,model:SdkAppModel,modifier:Modifier=Modifier){
 key(node.getString("id"),node.getString("file")){
  val c=LocalContext.current;val lifecycle=LocalLifecycleOwner.current;val scope=rememberCoroutineScope();val density=LocalDensity.current
  var active by remember{mutableStateOf(lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))}
  var busy by remember{mutableStateOf(false)};var status by remember{mutableStateOf("Opening PDF…")};var page by remember{mutableIntStateOf(0)};var pages by remember{mutableIntStateOf(0)}
  var zoom by remember{mutableFloatStateOf(1f)};var bitmap by remember{mutableStateOf<android.graphics.Bitmap?>(null)};var pageWidth by remember{mutableIntStateOf(1)};var pageHeight by remember{mutableIntStateOf(1)}
  var source by remember{mutableStateOf<JSONObject?>(null)};var textSupported by remember{mutableStateOf(false)};var bounds by remember{mutableStateOf<List<FloatArray>>(emptyList())}
  var dialog by remember{mutableStateOf("")};var query by remember{mutableStateOf("")};var text by remember{mutableStateOf("")};var matches by remember{mutableStateOf<List<Bundle>>(emptyList())};var nextPage by remember{mutableIntStateOf(-1)};var nextMatch by remember{mutableIntStateOf(0)}
  var operation by remember{mutableStateOf<Job?>(null)}
  val id=node.getString("id")
  fun authorize(d:JSONObject){check(active&&!model.sessionUnavailable){"Reopen the app to read this PDF"};val current=model.pdfDocument(id);check(current.getJSONObject("owner").getString("hash")==d.getJSONObject("owner").getString("hash")&&current.getJSONObject("file").getString("id")==d.getJSONObject("file").getString("id")){"PDF access changed"}}
  suspend fun run(d:JSONObject,op:String,extra:Bundle=Bundle()):PdfReply{extra.putString("op",op);extra.putInt("page",extra.getInt("page",page));return SdkPdfClient.execute(c,d,extra){authorize(d)}}
  suspend fun render(d:JSONObject,index:Int,highlight:List<FloatArray> = emptyList()){
   val result=run(d,"render",Bundle().apply{putInt("page",index);putInt("render_width",(1000*zoom).toInt().coerceIn(256,1600))});authorize(d)
   bitmap=result.bitmap;pageWidth=result.data.getInt("width");pageHeight=result.data.getInt("height");page=index;pages=result.data.getInt("pages");textSupported=result.data.getBoolean("text_supported");bounds=highlight
   status="Page ${page+1} of $pages";try{SdkPdfPositions.save(c,d,page,pages){authorize(d)}}catch(e:PdfPositionWriteException){status="Page ${page+1} of $pages · position not saved"}
  }
  fun perform(block:suspend()->Unit){if(busy)return;operation=scope.launch{busy=true;try{block()}catch(e:TimeoutCancellationException){status="PDF request timed out. Try again."}catch(e:CancellationException){throw e}catch(e:Exception){status=if(e is TimeoutCancellationException)"PDF request timed out. Try again."else e.message?:"PDF request failed"}finally{busy=false}}}
  DisposableEffect(lifecycle){val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_STOP){active=false;source?.getJSONObject("owner")?.getString("id")?.let{SdkPdfClient.cancelApp(it)};operation?.cancel();bitmap=null;text="";bounds=emptyList();dialog=""}else if(event==Lifecycle.Event.ON_START)active=true};lifecycle.lifecycle.addObserver(observer);onDispose{lifecycle.lifecycle.removeObserver(observer);operation?.cancel();source?.getJSONObject("owner")?.getString("id")?.let{SdkPdfClient.cancelApp(it)}}}
  LaunchedEffect(active,model.sessionUnavailable){
   if(!active||model.sessionUnavailable){operation?.cancel();bitmap=null;text="";return@LaunchedEffect}
   busy=true
   try{val d=withContext(Dispatchers.IO){model.pdfDocument(id)};source=d;val info=run(d,"info");pages=info.data.getInt("pages");textSupported=info.data.getBoolean("text_supported");val saved=SdkPdfPositions.read(c,d,pages);render(d,saved)}catch(e:TimeoutCancellationException){status="PDF request timed out. Try again."}catch(e:CancellationException){throw e}catch(e:Exception){status=e.message?:"This PDF could not be opened"}finally{busy=false}
  }
  val enabled=active&&!model.sessionUnavailable&&!busy&&!model.busy&&model.pending==null
  Column(modifier.fillMaxSize()){
   Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())){
    TextButton(onClick={source?.let{d->perform{render(d,page-1)}}},enabled=enabled&&page>0,modifier=Modifier.heightIn(min=48.dp)){Text("Previous")}
    Text("${if(pages>0)page+1 else 0} / $pages",Modifier.padding(12.dp))
    TextButton(onClick={source?.let{d->perform{render(d,page+1)}}},enabled=enabled&&page+1<pages,modifier=Modifier.heightIn(min=48.dp)){Text("Next")}
    TextButton(onClick={zoom=(zoom-.5f).coerceAtLeast(1f);source?.let{d->perform{render(d,page)}}},enabled=enabled&&zoom>1,modifier=Modifier.heightIn(min=48.dp)){Text("Zoom −")}
    TextButton(onClick={zoom=(zoom+.5f).coerceAtMost(2f);source?.let{d->perform{render(d,page)}}},enabled=enabled&&pages>0&&zoom<2,modifier=Modifier.heightIn(min=48.dp)){Text("Zoom +")}
    TextButton(onClick={dialog="search"},enabled=enabled&&textSupported,modifier=Modifier.heightIn(min=48.dp)){Text("Search")}
    TextButton(onClick={source?.let{d->perform{val result=run(d,"text");text=result.data.getString("text").orEmpty().ifBlank{"No extractable text on this page. OCR is not available."};dialog="text"}}},enabled=enabled&&textSupported,modifier=Modifier.heightIn(min=48.dp)){Text("Page text")}
   }
   Text(if(busy)"Processing PDF…"else status,Modifier.semantics{liveRegion=LiveRegionMode.Polite})
   if(pages>0&&!textSupported)Text("Search and text selection need Android15 or later.")
   if(bitmap==null&&!busy)TextButton(onClick={source?.let{d->perform{render(d,page)}}},enabled=enabled&&source!=null){Text("Retry page")}
   BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()){
    val image=bitmap
    if(image!=null&&active&&!model.sessionUnavailable){
     val ratio=pageWidth.toFloat()/pageHeight;val baseW=if(maxWidth/maxHeight>ratio)maxHeight*ratio else maxWidth;val baseH=baseW/ratio
     val displayW=(baseW*zoom).coerceAtLeast(1.dp);val displayH=(baseH*zoom).coerceAtLeast(1.dp)
     Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState()).verticalScroll(rememberScrollState())){
      Box(Modifier.size(displayW,displayH).pointerInput(enabled,page,displayW,displayH){
       if(enabled&&textSupported){var start=Offset.Zero;var end=Offset.Zero;detectDragGesturesAfterLongPress(onDragStart={start=it;end=it},onDrag={change,drag->change.consume();end+=drag},onDragEnd={val d=source;if(d!=null){val w=with(density){displayW.toPx()};val h=with(density){displayH.toPx()};perform{val r=run(d,"select",Bundle().apply{putFloatArray("points",floatArrayOf(start.x/w*pageWidth,start.y/h*pageHeight,end.x/w*pageWidth,end.y/h*pageHeight))});text=r.data.getString("text").orEmpty().ifBlank{"No text selected. Try Page text instead."};bounds=r.data.rectangles();dialog="selection"}}})}
      }){
       Image(image.asImageBitmap(),"PDF page ${page+1} of $pages",Modifier.fillMaxSize())
       Canvas(Modifier.fillMaxSize()){for(r in bounds){val left=r[0]/pageWidth*size.width;val top=r[1]/pageHeight*size.height;drawRect(Color(0xffffd54f).copy(alpha=.38f),Offset(left,top),androidx.compose.ui.geometry.Size(((r[2]-r[0])/pageWidth*size.width).coerceAtLeast(0f),((r[3]-r[1])/pageHeight*size.height).coerceAtLeast(0f)))}}
      }
     }
    }
   }
  }
  if(dialog=="search")ReferenceDialog("Search PDF",{if(!busy)dialog=""},footer={DialogAction("Close",enabled=!busy){dialog=""};DialogAction("Find",enabled=!busy&&query.isNotBlank()){source?.let{d->perform{val r=run(d,"search",Bundle().apply{putInt("page",0);putInt("after",0);putString("query",query)});matches=r.data.getParcelableArrayList<Bundle>("matches").orEmpty();nextPage=r.data.getInt("next_page",-1);nextMatch=r.data.getInt("next_match",0)}}}}){
   OutlinedTextField(query,{query=it.take(128);matches=emptyList();nextPage=-1},label={Text("Find text")},enabled=!busy,singleLine=true)
   Text(if(busy)"Searching…"else status)
   matches.forEachIndexed{i,match->TextButton(onClick={source?.let{d->perform{render(d,match.getInt("page"),match.rectangles());dialog=""}}},enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("Page ${match.getInt("page")+1} · match ${i+1}")}}
   if(matches.isEmpty()&&!busy)Text("Searchable text depends on the PDF. Scanned pages may need OCR, which is not included.")
   if(nextPage>=0)TextButton(onClick={source?.let{d->perform{val r=run(d,"search",Bundle().apply{putInt("page",nextPage);putInt("after",nextMatch);putString("query",query)});matches=r.data.getParcelableArrayList<Bundle>("matches").orEmpty();nextPage=r.data.getInt("next_page",-1);nextMatch=r.data.getInt("next_match",0)}}},enabled=!busy){Text("More matches")}
  }
  if(dialog in setOf("text","selection"))ReferenceDialog(if(dialog=="text")"Page text"else"Selected text",{dialog=""},footer={DialogAction("Close"){dialog=""};DialogAction("Copy",enabled=model.clipboardAllowed("clipboard.write")){AppSdk.carrierEgress{source?.let{authorize(it)};check(model.clipboardAllowed("clipboard.write"));c.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("PDF text",text))}}}){Text(text,Modifier.padding(16.dp))}
 }
}
