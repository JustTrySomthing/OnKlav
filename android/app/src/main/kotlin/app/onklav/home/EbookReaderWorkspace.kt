package app.onklav.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.json.JSONObject

/** Presentation only: the signed EPUB controller still owns page/chapter state,
 * position persistence, conflicts, auto-read continuation and all navigation. */
@Composable internal fun EbookReaderWorkspace(model:SdkAppModel,render:@Composable (JSONObject,Modifier)->Unit){
 val nodes=model.frame.nodes("body");fun node(id:String)=nodes.firstOrNull{it.optString("id")==id}
 val colors=MaterialTheme.colorScheme;val scroll=rememberScrollState()
 LaunchedEffect(model.frame.optString("scroll_key")){scroll.scrollTo(0)}
 var targetChapter by remember{mutableIntStateOf(-1)}
 var targetLast by remember{mutableStateOf(false)}
 val reader=model.frame.child("state").child("reader")
 val chapter=reader.child("chapter").optInt("chapter",-1)
 val ready=!model.busy&&model.pending==null
 LaunchedEffect(chapter,ready,reader.child("reading").optBoolean("ready")){
  if(ready&&chapter==targetChapter&&reader.child("reading").optBoolean("ready")){
   targetChapter=-1
   val offset=ebookBoundaryOffset(reader.child("chapter").optString("text"),targetLast)
   model.action("reading.goto",offset.toString())
  }
 }
 fun enabled(id:String)=node(id)?.optBoolean("enabled",true)==true
 val nextPage=enabled("reading.next");val nextChapter=enabled("epub.next")
 val previousPage=enabled("reading.previous");val previousChapter=enabled("epub.previous")
 Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal=20.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
  Column(verticalArrangement=Arrangement.spacedBy(4.dp)){
   Text(node("epub.title")?.optString("text").orEmpty(),style=MaterialTheme.typography.titleLarge,maxLines=2,overflow=TextOverflow.Ellipsis)
   Text(node("epub.chapter_title")?.optString("text").orEmpty(),style=MaterialTheme.typography.titleSmall,color=colors.primary)
  }
  node("ebook.read.aloud")?.let{Surface(shape=RoundedCornerShape(20.dp),color=colors.surfaceContainerLow){Box(Modifier.padding(horizontal=12.dp,vertical=2.dp)){render(it,Modifier.fillMaxWidth())}}}
  node("reading.page")?.let{render(it,Modifier.fillMaxWidth())}
  val replaced=setOf("epub.title","epub.chapter_title","ebook.read.aloud","reading.page","reading.previous","reading.next","epub.previous","epub.next","book.progress","book.bookmark","book.marks","book.status")
  nodes.filter{it.optString("id") !in replaced}.forEach{render(it,Modifier.fillMaxWidth())}
  Surface(shape=RoundedCornerShape(24.dp),color=colors.surfaceContainerLow,modifier=Modifier.fillMaxWidth().testTag("ebook-page-navigation")){
   Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
    Text(node("book.progress")?.optString("text").orEmpty(),style=MaterialTheme.typography.labelMedium,color=colors.onSurfaceVariant)
    Text(if(nextPage)"Continue this chapter"else if(nextChapter)"End of chapter"else"End of book",style=MaterialTheme.typography.titleMedium,modifier=Modifier.semantics{liveRegion=LiveRegionMode.Polite})
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
     OutlinedButton(enabled=ready&&(previousPage||previousChapter),onClick={if(previousPage)model.action("reading.previous")else{targetChapter=chapter-1;targetLast=true;model.action("epub.previous")}},modifier=Modifier.weight(1f).heightIn(min=48.dp).testTag("ebook-previous")){
      Text("Previous")
     }
     Button(enabled=ready&&(nextPage||nextChapter),onClick={if(nextPage)model.action("reading.next")else{targetChapter=chapter+1;targetLast=false;model.action("epub.next")}},modifier=Modifier.weight(1f).heightIn(min=48.dp).testTag("ebook-next")){
      Text("Next")
     }
    }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically){
     TextButton(enabled=ready,onClick={model.action("epub.toc")}){Icon(painterResource(app.onklav.shared.R.drawable.ebook_contents),null,Modifier.size(18.dp));Spacer(Modifier.width(5.dp));Text("Contents")}
     TextButton(enabled=ready,onClick={model.action("book.bookmark")},modifier=Modifier.testTag("sdk-book.bookmark")){Text("Bookmark page")}
    }
    TextButton(enabled=ready,onClick={model.action("book.marks")},modifier=Modifier.align(Alignment.CenterHorizontally).testTag("sdk-book.marks")){Text("Your bookmarks")}
   }
  }
  node("book.status")?.optString("text")?.takeIf{it.isNotBlank()}?.let{Text(it,style=MaterialTheme.typography.bodySmall,color=colors.primary)}
 }
}

internal fun ebookBoundaryOffset(text:String,last:Boolean):Int {
 val count=text.codePointCount(0,text.length);val page=if(last&&count>0)((count-1)/4000)*4000 else 0
 return text.substring(0,text.offsetByCodePoints(0,page)).toByteArray(Charsets.UTF_8).size
}
