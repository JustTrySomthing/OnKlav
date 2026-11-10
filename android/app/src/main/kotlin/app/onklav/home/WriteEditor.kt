@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.onklav.home

import app.onklav.shared.OnKlavFileContracts

import android.app.Application
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.*
import android.text.style.*
import android.view.Gravity
import android.widget.EditText
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.onklav.shared.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.util.UUID

internal object WriteFormatting {
    fun span(kind:String,v:String):Any=when(kind){"bold"->StyleSpan(Typeface.BOLD);"italic"->StyleSpan(Typeface.ITALIC);"underline"->UnderlineSpan();"strike"->StrikethroughSpan();"color"->ForegroundColorSpan(v.toInt());"highlight"->BackgroundColorSpan(v.toInt());"font"->TypefaceSpan(v);"size"->AbsoluteSizeSpan(v.toInt().coerceIn(8,96),true);"link"->URLSpan(safeLink(v));else->error("Unknown format")}
    private fun match(m:WriteMark,kind:String)=if(kind in setOf("bold","italic"))m.kind=="style"&&m.value.toInt()and(if(kind=="bold")Typeface.BOLD else Typeface.ITALIC)!=0 else m.kind==kind
    private fun nativeMatch(span:Any,kind:String)=when(kind){"bold"->span is StyleSpan&&span.style and Typeface.BOLD!=0;"italic"->span is StyleSpan&&span.style and Typeface.ITALIC!=0;"underline"->span is UnderlineSpan;"strike"->span is StrikethroughSpan;"color"->span is ForegroundColorSpan;"highlight"->span is BackgroundColorSpan;"font"->span is TypefaceSpan;"size"->span is AbsoluteSizeSpan;"link"->span is URLSpan;else->false}
    fun active(s:Spanned,start:Int,end:Int,kind:String):Boolean {
        val a=minOf(start,end).coerceIn(0,s.length);val b=maxOf(start,end).coerceIn(a,s.length)
        return s.getSpans(a,b,Any::class.java).any{span->val first=s.getSpanStart(span);val last=s.getSpanEnd(span);val flags=s.getSpanFlags(span)
            nativeMatch(span,kind) && (if(a!=b)first<=a&&last>=b else first<a&&last>a||first==a&&last==a||first==a&&(flags and 0xf0)==0x10||last==a&&(flags and 0x0f)==0x02)
        }
    }
    fun apply(s:Editable,start:Int,end:Int,kind:String,value:String?){val a=minOf(start,end).coerceIn(0,s.length);val b=maxOf(start,end).coerceIn(a,s.length);val replacement=value?.let{span(kind,it)};val captured=WriteDocument.capture("","",s)
        val matches=captured.marks.filter{match(it,kind)&&it.end>=a&&it.start<=b};val kept=captured.marks.toMutableList();kept.removeAll(matches.toSet())
        matches.forEach{m->if(m.start<a)kept.add(m.copy(end=a));if(m.end>b)kept.add(m.copy(start=b));if(kind in setOf("bold","italic")){val rest=m.value.toInt()and(if(kind=="bold")Typeface.BOLD else Typeface.ITALIC).inv();if(rest!=0&&b>=a)kept.add(m.copy(start=maxOf(a,m.start),end=minOf(b,m.end),value=rest.toString()))}}
        val result=captured.copy(marks=kept).spanned();s.getSpans(0,s.length,Any::class.java).filter{it is CharacterStyle||it is ParagraphStyle}.forEach{s.removeSpan(it)}
        result.getSpans(0,result.length,Any::class.java).forEach{span->s.setSpan(span,result.getSpanStart(span),result.getSpanEnd(span),if(nativeMatch(span,kind))Spanned.SPAN_EXCLUSIVE_EXCLUSIVE else result.getSpanFlags(span))};if(replacement!=null)s.setSpan(replacement,a,b,Spanned.SPAN_INCLUSIVE_INCLUSIVE)
    }
    fun paragraphs(s:Editable,start:Int,end:Int,kind:String,value:String){val lo=minOf(start,end).coerceIn(0,s.length);val hi=maxOf(start,end).coerceIn(lo,s.length);val a=s.toString().lastIndexOf('\n',(lo-1).coerceAtLeast(-1))+1;val last=if(hi>lo)hi-1 else hi;val z=s.toString().indexOf('\n',last.coerceIn(0,s.length)).let{if(it<0)s.length else it+1}
        val existing=s.getSpans(a,z,Any::class.java).filter { item ->
            val overlaps=(s.getSpanStart(item)<z&&s.getSpanEnd(item)>a)||(a==z&&s.getSpanStart(item)==a)
            val category=when(kind){
                "align" -> item is AlignmentSpan
                "indent" -> item is LeadingMarginSpan.Standard
                else -> item is WriteListSpan || item is BulletSpan
            }
            overlaps && category
        }
        existing.forEach { old ->
            val left=s.getSpanStart(old);val right=s.getSpanEnd(old)
            fun copySpan():Any { return when(old){
                is AlignmentSpan -> AlignmentSpan.Standard(old.alignment)
                is WriteListSpan -> WriteListSpan(old.kind,old.number,old.indent)
                is BulletSpan -> WriteListSpan("bullet")
                is LeadingMarginSpan.Standard -> LeadingMarginSpan.Standard(old.getLeadingMargin(true))
                else -> error("Unknown paragraph")
            } }
            s.removeSpan(old)
            if(left<a)s.setSpan(copySpan(),left,a,Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
            if(right>z)s.setSpan(copySpan(),z,right,Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
        }
        var offset=a;while(offset<=z&&offset<=s.length){val stop=s.toString().indexOf('\n',offset).let{if(it<0)s.length else it+1};val span:Any?=when(kind){"align"->AlignmentSpan.Standard(Layout.Alignment.valueOf(value));"indent"->LeadingMarginSpan.Standard(value.toInt().coerceIn(0,240));"list"->if(value=="none")null else WriteListSpan(value);else->null};if(span!=null)s.setSpan(span,offset,stop,Spanned.SPAN_INCLUSIVE_INCLUSIVE);if(stop>=z||stop>=s.length)break;offset=stop}
        normalizeLists(s)
    }
    fun normalizeLists(s:Editable){if(s.getSpans(0,s.length,WriteListSpan::class.java).isEmpty())return;val text=s.toString();val rows=mutableListOf<Triple<Int,Int,WriteListSpan>>();var at=0;var number=0;var previous=""
        while(at<=s.length){val end=text.indexOf('\n',at).let{if(it<0)s.length else it+1};val span=s.getSpans(at,(at+1).coerceAtMost(s.length),WriteListSpan::class.java).lastOrNull{ s.getSpanStart(it)<=at && (s.getSpanEnd(it)>at || s.getSpanStart(it)==at)}
            if(span!=null){number=if(span.kind=="number"&&previous=="number")number+1 else 1;rows.add(Triple(at,end,WriteListSpan(span.kind,number,span.indent)));previous=span.kind}else{previous="";number=0};if(end>=s.length){if(at<end&&text.endsWith("\n")){at=end;continue};break};at=end}
        s.getSpans(0,s.length,WriteListSpan::class.java).forEach{s.removeSpan(it)};rows.forEach{(a,b,p)->s.setSpan(p,a,b,if(a==b)Spanned.SPAN_INCLUSIVE_INCLUSIVE else Spanned.SPAN_INCLUSIVE_EXCLUSIVE)}
    }
}
// OnKlav uses ComponentActivity and styles this platform rich editor directly.
// AppCompat is only a transitive media dependency; its widget/theme is not used here.
@android.annotation.SuppressLint("AppCompatCustomView")
internal class WriteNativeEditor(c:Context,private val owner:String="app.onklav.write"):EditText(android.view.ContextThemeWrapper(c,app.onklav.home.R.style.OnKlavWritePaperTheme)){
    var changed:((Spanned,Boolean)->Unit)?=null;var selected:((Int,Int)->Unit)?=null;var guarded=false;private var beforeList:WriteListSpan?=null;private var editStart=0;private var editCount=0;var documentId="";var revision=-1L
    private var selectionMode:android.view.ActionMode?=null
    var formattingToolsOpen=false
    var resumeEditing:(()->Unit)?=null
    private val formattingMode=object:android.view.ActionMode.Callback{
        override fun onCreateActionMode(mode:android.view.ActionMode,menu:android.view.Menu):Boolean{selectionMode=mode;return true}
        override fun onPrepareActionMode(mode:android.view.ActionMode,menu:android.view.Menu):Boolean{if(owner=="host.vault")for(i in menu.size()-1 downTo 0){val item=menu.getItem(i);if(item.intent!=null)menu.removeItem(item.itemId)};return true}
        override fun onActionItemClicked(mode:android.view.ActionMode,item:android.view.MenuItem)=false
        override fun onDestroyActionMode(mode:android.view.ActionMode){if(selectionMode===mode)selectionMode=null}
    }
    fun beginFormatting(){
        val a=minOf(selectionStart,selectionEnd).coerceAtLeast(0);val b=maxOf(selectionStart,selectionEnd).coerceAtLeast(a);val x=scrollX;val y=scrollY
        guarded=true;try{selectionMode?.finish();setSelection(a.coerceAtMost(length()),b.coerceAtMost(length()));scrollTo(x,y)}finally{guarded=false};formattingToolsOpen=true
    }
    override fun requestRectangleOnScreen(rect:android.graphics.Rect,immediate:Boolean):Boolean=if(formattingToolsOpen)false else super.requestRectangleOnScreen(rect,immediate)
    override fun onTouchEvent(event:android.view.MotionEvent):Boolean{if(event.actionMasked==android.view.MotionEvent.ACTION_DOWN&&formattingToolsOpen){formattingToolsOpen=false;resumeEditing?.invoke()};return super.onTouchEvent(event)}
    init{customSelectionActionModeCallback=formattingMode;customInsertionActionModeCallback=formattingMode;gravity=Gravity.TOP or Gravity.START;setTextColor(Color.BLACK);setHintTextColor(Color.GRAY);hint="Start writing…";background=null;setPadding((20*c.resources.displayMetrics.density).toInt(),(18*c.resources.displayMetrics.density).toInt(),(20*c.resources.displayMetrics.density).toInt(),(40*c.resources.displayMetrics.density).toInt());isVerticalScrollBarEnabled=true;setHorizontallyScrolling(false);inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or android.text.InputType.TYPE_TEXT_FLAG_AUTO_CORRECT;filters=arrayOf(InputFilter.LengthFilter(500000));setLineSpacing(4f,1.15f)
        addTextChangedListener(object:TextWatcher{override fun beforeTextChanged(s:CharSequence?,a:Int,c:Int,n:Int){if(!guarded&&formattingToolsOpen){formattingToolsOpen=false;resumeEditing?.invoke()};if(!guarded&&s is Spanned)beforeList=s.getSpans(a,a,WriteListSpan::class.java).lastOrNull{s.getSpanEnd(it)>a||s.getSpanStart(it)==a||a==s.length&&!s.toString().endsWith("\n")}};override fun onTextChanged(s:CharSequence?,a:Int,b:Int,c:Int){editStart=a;editCount=c};override fun afterTextChanged(s:Editable?){if(!guarded&&s!=null){guarded=true
            if(editCount==1&&editStart<s.length&&s[editStart]=='\n'){
                val marker=beforeList
                if(marker!=null){if((editStart==0||s[editStart-1]=='\n')&&(editStart+1>=s.length||s[editStart+1]=='\n')){s.delete(editStart,editStart+1);WriteFormatting.paragraphs(s,editStart,editStart,"list","none")}else if(editStart+1==s.length)s.setSpan(WriteListSpan(marker.kind),s.length,s.length,Spanned.SPAN_INCLUSIVE_INCLUSIVE)}
            }
            WriteFormatting.normalizeLists(s);scaleLists();guarded=false;changed?.invoke(s,true)}}})
    }
    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int){super.onSizeChanged(w,h,oldw,oldh);if(w>0&&w!=oldw){scaleLists();requestLayout();invalidate()}}
    private fun scaleLists(){val available=width-paddingLeft-paddingRight;editableText.getSpans(0,length(),WriteRuleSpan::class.java).forEach{span->if(available>0)span.widthPx=available;span.density=resources.displayMetrics.density*resources.configuration.fontScale};if(available>0)editableText.getSpans(0,length(),WriteImageSpan::class.java).forEach{span->val d=span.drawable;val b=d.bounds;if(b.width()>available)d.setBounds(0,0,available,(b.height().toFloat()*available/b.width()).toInt().coerceAtLeast(1))};editableText.getSpans(0,length(),WriteListSpan::class.java).forEach{it.scale=resources.displayMetrics.density*resources.configuration.fontScale}}
    override fun onProvideStructure(structure:android.view.ViewStructure){if(owner=="host.vault"){structure.setClassName("android.widget.EditText");structure.setText("")}else super.onProvideStructure(structure)}
    override fun onSelectionChanged(a:Int,b:Int){super.onSelectionChanged(a,b);if(!guarded)selected?.invoke(minOf(a,b).coerceAtLeast(0),maxOf(a,b).coerceAtLeast(0))}
    override fun onTextContextMenuItem(id:Int):Boolean {if(owner=="host.vault"&&id==android.R.id.paste)return super.onTextContextMenuItem(android.R.id.pasteAsPlainText);val cap=when(id){android.R.id.copy,android.R.id.cut->"clipboard.write";android.R.id.paste,android.R.id.pasteAsPlainText->"clipboard.read";else->null};if(cap!=null&&owner!="host.vault"&&!runCatching{val a=AppSdk.sync(context,JSONObject().put("action","list"))as org.json.JSONArray;(0 until a.length()).map{a.getJSONObject(it)}.any{r->r.optBoolean("installed")&&r.optJSONObject("manifest")?.optString("id")==owner&&r.optJSONArray("grants")?.let{g->(0 until g.length()).any{g.optString(it)==cap}}==true}}.getOrDefault(false))return false;return super.onTextContextMenuItem(id)}
    fun load(doc:WriteDocument,rev:Long,a:Int,b:Int){guarded=true;setText(doc.spanned());scaleLists();documentId=doc.id;revision=rev;setSelection(a.coerceIn(0,length()),b.coerceIn(0,length()));guarded=false}
    fun format(kind:String,value:String?){beginFormatting();val x=scrollX;val y=scrollY;guarded=true;try{WriteFormatting.apply(editableText,selectionStart,selectionEnd,kind,value);scaleLists()}finally{guarded=false};changed?.invoke(editableText,false);selected?.invoke(selectionStart,selectionEnd);if(formattingToolsOpen)scrollTo(x,y)}
    fun insertRule(){
        if(length()>499997){android.widget.Toast.makeText(context,"Document limit reached",android.widget.Toast.LENGTH_SHORT).show();return}
        beginFormatting();val at=selectionStart.coerceIn(0,length());val value=editableText
        val prefix=if(at>0&&value[at-1]!='\n')"\n"else""
        guarded=true
        try{
            value.insert(at,prefix+"\uFFFC\n");val ruleAt=at+prefix.length
            // A rule is a separate paragraph and must not inherit list/image/link formatting.
            WriteFormatting.paragraphs(value,ruleAt,ruleAt+1,"list","none")
            WriteFormatting.apply(value,ruleAt,ruleAt+1,"link",null)
            value.setSpan(WriteRuleSpan(),ruleAt,ruleAt+1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);scaleLists();setSelection(ruleAt+2)
        }finally{guarded=false}
        changed?.invoke(value,false);selected?.invoke(selectionStart,selectionEnd)
    }
    fun paragraph(kind:String,value:String){beginFormatting();guarded=true;try{WriteFormatting.paragraphs(editableText,selectionStart,selectionEnd,kind,value);scaleLists()}finally{guarded=false};changed?.invoke(editableText,false)}
}
internal class WriteModel(app:Application):AndroidViewModel(app){
    var doc by mutableStateOf(WriteDocument());private set
    var documents by mutableStateOf<List<WriteDocument>>(emptyList());private set
    var revision by mutableLongStateOf(0L);private set
    var selection by mutableStateOf(0 to 0)
    var status by mutableStateOf("Opening…");private set
    var importNotice by mutableStateOf<String?>(null);private set
    fun dismissImportNotice(){importNotice=null}
    var openingFailed by mutableStateOf(false);private set
    var initialized by mutableStateOf(false);private set
    var busy by mutableStateOf(false);private set
    private var savedCheckpoint=doc
    private val undo=mutableStateListOf<WriteDocument>();private val redo=mutableStateListOf<WriteDocument>();private var changedSinceLoad=false;private var lastEdit=0L;private var lastWasTyping=false;private var saveJob:Job?=null;private val disk=Mutex()
    val canUndo get()=undo.isNotEmpty();val canRedo get()=redo.isNotEmpty()
    init{retryOpening()}
    fun retryOpening(){if(busy)return;initialized=false;openingFailed=false;viewModelScope.launch{
        try{val loaded=withContext(Dispatchers.IO){WritePendingSaves.finish();val id=getApplication<Application>().getSharedPreferences("write",0).getString("current",null)?:WriteFiles.list(getApplication()).firstOrNull()?.id;id?.let{WriteFiles.read(getApplication(),it)}}
            if(loaded!=null)doc=loaded;savedCheckpoint=doc;revision++;status="Saved"
        }catch(e:CancellationException){throw e}catch(e:Exception){openingFailed=true;status="Unable to open saved document · ${e.message?:"Try again"}. The original is unchanged."}
        finally{initialized=true;refresh()}
    }}
    fun refresh(){viewModelScope.launch{runCatching{withContext(Dispatchers.IO){WriteFiles.list(getApplication())}}.onSuccess{documents=it}.onFailure{status=it.message?:"Unable to load documents"}}}
    fun change(value:WriteDocument,coalesce:Boolean=true){if(openingFailed)return;val now=System.currentTimeMillis();val tick=android.os.SystemClock.uptimeMillis();if(value.text==doc.text&&value.marks==doc.marks&&value.title==doc.title)return;if(!coalesce||!lastWasTyping||tick-lastEdit !in 0L..900L){undo.add(doc);if(undo.size>30)undo.removeAt(0)};redo.clear();changedSinceLoad=true;lastEdit=tick;lastWasTyping=coalesce;doc=value.copy(modified=now);save()}
    private fun save(){changedSinceLoad=true;saveJob?.cancel();val snapshot=doc;status="Saving…";saveJob=WritePendingSaves.track(viewModelScope.launch{delay(550);persist(snapshot)})}
    private suspend fun persist(snapshot:WriteDocument):Boolean{try{withContext(Dispatchers.IO){disk.withLock{WriteFiles.save(getApplication(),snapshot)}};if(doc==snapshot){changedSinceLoad=false;status="Saved"};return true}catch(e:CancellationException){throw e}catch(e:Exception){status="Not saved · ${e.message?:"Tap Save to retry"}";return false}}
    fun saveNow(markSaved:Boolean=true,onSaved:()->Unit={}){if(openingFailed)return;changedSinceLoad=true;saveJob?.cancel();val snapshot=doc;status="Saving…";saveJob=WritePendingSaves.track(viewModelScope.launch{if(persist(snapshot)){if(markSaved&&doc.id==snapshot.id)savedCheckpoint=snapshot;refresh();onSaved()}})}
    fun checkpointOnOpen(){if(initialized&&!busy)savedCheckpoint=doc}
    fun flush(){if(busy||openingFailed)return;if(changedSinceLoad||saveJob?.isActive==true)saveNow(false)}
    fun discard(onClosed:()->Unit={}){if(busy||openingFailed)return;saveJob?.cancel();val restore=savedCheckpoint;busy=true;viewModelScope.launch{try{withContext(Dispatchers.IO){disk.withLock{WriteFiles.save(getApplication(),restore)}};doc=restore;changedSinceLoad=false;undo.clear();redo.clear();selection=0 to 0;revision++;status="Saved";refresh();onClosed()}catch(e:Exception){status="Unable to discard · ${e.message}"}finally{busy=false}}}
    fun rename(name:String){change(doc.copy(title=name.trim().ifBlank{"Untitled"}.take(120)),false)}
    fun history(back:Boolean){val from=if(back)undo else redo;val to=if(back)redo else undo;if(from.isEmpty())return;to.add(doc);doc=from.removeAt(from.lastIndex).copy(modified=System.currentTimeMillis());revision++;selection=selection.first.coerceIn(0,doc.text.length) to selection.second.coerceIn(0,doc.text.length);lastEdit=0;save()}
    fun open(id:String?=null,copy:Boolean=false){if(busy)return;saveJob?.cancel();val old=doc;busy=true;viewModelScope.launch{try{if(!openingFailed)withContext(Dispatchers.IO){disk.withLock{WriteFiles.save(getApplication(),old)}};val next=withContext(Dispatchers.IO){id?.let{WriteFiles.read(getApplication(),it)}}?:WriteDocument();doc=if(copy)next.copy(id=UUID.randomUUID().toString(),title="${next.title} copy".take(120))else next;openingFailed=false;selection=0 to 0;undo.clear();redo.clear();revision++;lastEdit=0;savedCheckpoint=doc;saveNow(false)}catch(e:Exception){status=e.message?:"Unable to open document"}finally{busy=false}}}
    fun delete(id:String){if(busy||openingFailed)return;if(id==doc.id)saveJob?.cancel();viewModelScope.launch{try{withContext(Dispatchers.IO){disk.withLock{WriteFiles.delete(getApplication(),id)}};if(id==doc.id){doc=WriteDocument();selection=0 to 0;undo.clear();redo.clear();revision++;savedCheckpoint=doc;saveNow(false)};refresh()}catch(e:Exception){status=e.message?:"Unable to delete document"}}}
    fun insertImage(uri:android.net.Uri){if(busy)return;val original=doc;val range=selection;busy=true;viewModelScope.launch{try{val encoded=withContext(Dispatchers.IO){WriteFiles.authorize(getApplication());WriteImageSpan.read(getApplication(),uri)};require(original.marks.count{it.kind=="image"}<12){"Use up to 12 images per document"};val text=original.spanned();val a=range.first.coerceIn(0,text.length);val b=range.second.coerceIn(a,text.length);text.replace(a,b,"\uFFFC");text.setSpan(WriteImageSpan(encoded),a,a+1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);val next=WriteDocument.capture(original.id,original.title,text);require(next.json().toString().toByteArray().size<=8_000_000){"Document images exceed 8 MB"};change(next,false);selection=a+1 to a+1;revision++}catch(e:Exception){status="Image not inserted · ${e.message}"}finally{busy=false}}}
    fun import(uri:android.net.Uri){if(busy)return;saveJob?.cancel();var warnings=emptySet<String>();val old=doc;busy=true;viewModelScope.launch{try{val next=withContext(Dispatchers.IO){if(!openingFailed)disk.withLock{WriteFiles.save(getApplication(),old)};WriteFiles.authorize(getApplication());val resolver=getApplication<Application>().contentResolver;val bytes=resolver.openInputStream(uri).use{input->requireNotNull(input);val data=java.io.ByteArrayOutputStream();val buffer=ByteArray(16384);while(true){val n=input.read(buffer);if(n<0)break;require(data.size()+n<=12_000_000){"Choose a document smaller than 12 MB"};data.write(buffer,0,n)};data.toByteArray()};val name=resolver.query(uri,arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())it.getString(0)else null}?:"Imported document";val text=if(bytes.size>2&&bytes[0]==0xff.toByte()&&bytes[1]==0xfe.toByte())bytes.copyOfRange(2,bytes.size).toString(Charsets.UTF_16LE)else if(bytes.size>2&&bytes[0]==0xfe.toByte()&&bytes[1]==0xff.toByte())bytes.copyOfRange(2,bytes.size).toString(Charsets.UTF_16BE)else bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF");val value=when{bytes.size>4&&bytes[0]==80.toByte()&&bytes[1]==75.toByte()->WriteExport.importDocx(bytes){warnings=it};name.endsWith(".write",true)||text.trimStart().startsWith("{\"schema\":\"onklav.write.v1\"")->WriteDocument.parse(JSONObject(text)).copy(id=UUID.randomUUID().toString());name.endsWith(".html",true)||name.endsWith(".htm",true)->WriteDocument.capture(UUID.randomUUID().toString(),name,Html.fromHtml(text,Html.FROM_HTML_MODE_COMPACT));else->{require(!text.startsWith("%PDF")&&!text.contains('\u0000')&&!text.contains('\uFFFD')){"Use a DOCX, HTML, TXT or Write document"};WriteDocument(text=text)}};require(value.text.length<=500000){"Document exceeds 500,000 characters"};value.copy(title=name.substringBeforeLast('.').take(120))};doc=next;openingFailed=false;selection=0 to 0;revision++;undo.clear();redo.clear();lastEdit=0;savedCheckpoint=doc;saveNow(false);status="Imported · supported text and formatting";importNotice=warnings.takeIf{it.isNotEmpty()}?.let{"Text and basic formatting were imported. These Word features were not preserved: ${it.sorted().joinToString()}. Your original file is unchanged."}}catch(e:Exception){status="Import failed · ${e.message}"}finally{busy=false}}}
    fun export(uri:android.net.Uri,type:String){if(busy||openingFailed)return;saveNow(false);val snapshot=doc;busy=true;viewModelScope.launch{try{withContext(Dispatchers.IO){WriteFiles.authorize(getApplication());writeFiledExport(getApplication(),uri){stream->when(type){"docx"->WriteExport.docx(snapshot,stream);"pdf"->WriteExport.pdf(snapshot,stream);"html"->stream.write(snapshot.html().toByteArray());"txt"->stream.write(snapshot.text.toByteArray());else->stream.write(snapshot.json().toString().toByteArray())}}};status="Copy exported"}catch(e:Exception){status="Export failed · ${e.message}. Choose another destination and retry."}finally{busy=false}}}
    override fun onCleared(){saveJob?.cancel();if(initialized&&!openingFailed&&!busy&&changedSinceLoad)WritePendingSaves.finalSnapshot(getApplication(),doc,disk);super.onCleared()}
}

internal class WriteToolbarState{
 var enabled by mutableStateOf(false)
 var find:()->Unit={};var documents:()->Unit={};var save:()->Unit={};var settings:()->Unit={}
}
@Composable internal fun WriteHeaderButtons(state:WriteToolbarState){
 IconButton(enabled=state.enabled,onClick={state.find()}){Icon(painterResource(R.drawable.ref_action_search),"Find")}
 IconButton(enabled=state.enabled,onClick={state.documents()}){Icon(Icons.Outlined.FolderOpen,"Documents")}
 IconButton(enabled=state.enabled,onClick={state.save()}){Icon(painterResource(R.drawable.photo_save),"Save options")}
 IconButton(enabled=state.enabled,onClick={state.settings()}){Icon(Icons.Outlined.Settings,"Write settings")}
}
@Composable internal fun WriteWorkspace(toolbar:WriteToolbarState?=null,initialFile:String?=null,onClose:()->Unit={}){
    val context=LocalContext.current;val epoch by AppSdk.epoch.collectAsState();val access=remember(epoch){WriteFiles.allowed(context)}
    if(!access){Surface{Text("Write storage access is off. Restore it in Settings > Apps > Write.",Modifier.padding(24.dp))};return}
    val currentSource=initialFile?:context.getSharedPreferences("write",0).getString("current",null)
    val protectedSource=remember(epoch,currentSource){currentSource?.let{runCatching{AppSdk.folderVault(context,"status",it.replace("-",""))}.getOrNull()}}
    var unlockSource by remember{mutableStateOf(false)}
    if(protectedSource?.optBoolean("locked")==true){
        SideEffect{toolbar?.enabled=false}
        FolderLockedPane(protectedSource){unlockSource=true}
        if(unlockSource)FolderVaultDialog(protectedSource,{unlockSource=false}){unlockSource=false}
        return
    }
    val model:WriteModel=viewModel(key="write-$epoch");var editor by remember{mutableStateOf<WriteNativeEditor?>(null)}
    LaunchedEffect(model){snapshotFlow{model.initialized&&!model.busy}.first{it};model.checkpointOnOpen()}
    model.importNotice?.let{notice->AlertDialog(onDismissRequest={model.dismissImportNotice()},title={Text("Import details")},text={Text(notice)},confirmButton={TextButton(onClick={model.dismissImportNotice()}){Text("Done")}})}
    var linkedError by remember{mutableStateOf<String?>(null)}
    LaunchedEffect(initialFile){snapshotFlow{model.initialized}.first{it};if(initialFile!=null)runCatching{withContext(Dispatchers.IO){WriteFiles.authorize(context);AppSdk.writeMigration(context,"linked",initialFile).getString("id")}}.onSuccess{model.open(it)}.onFailure{linkedError=it.message}}
    linkedError?.let{message->AlertDialog(onDismissRequest={linkedError=null},title={Text("Unable to open document")},text={Text(message)},confirmButton={TextButton(onClick={linkedError=null}){Text("Close")}})}
    var panel by remember{mutableStateOf("")};var folder by remember{mutableStateOf(false)};var rename by remember{mutableStateOf(false)};var name by remember{mutableStateOf("")};var remove by remember{mutableStateOf<WriteDocument?>(null)}
    var format by remember{mutableStateOf("docx")};var find by remember{mutableStateOf("")};var replacement by remember{mutableStateOf("")};var findStatus by remember{mutableStateOf("")};var link by remember{mutableStateOf("")};var customColor by remember{mutableStateOf("")};var colorError by remember{mutableStateOf("")}
    var documentQuery by remember{mutableStateOf("")}
    var reviewGeneration by remember{mutableIntStateOf(0)}
    var review by remember{mutableStateOf<List<WriteIssue>>(emptyList())};var reviewText by remember{mutableStateOf("")};var reviewStatus by remember{mutableStateOf("")}
    val proofPrefs=remember{context.getSharedPreferences("write_proofreading",0)}
    var proofEnabled by remember{mutableStateOf(proofPrefs.getBoolean("underline",true))}
    var ignoredWords by remember(model.doc.id){mutableStateOf<Set<String>>(emptySet())}
    val density=LocalDensity.current;val ink=MaterialTheme.colorScheme.primary.toArgb();val doc=model.doc;var selectionVersion by remember{mutableIntStateOf(0)}
    val imagePicker=rememberLauncherForActivityResult(OnKlavFileContracts.OpenDocument()){it?.let(model::insertImage)}
    val importer=rememberLauncherForActivityResult(OnKlavFileContracts.OpenDocument()){it?.let(model::import)}
    val docx=rememberLauncherForActivityResult(OnKlavFileContracts.CreateDocument("application/vnd.openxmlformats-officedocument.wordprocessingml.document")){it?.let{u->model.export(u,"docx")}}
    val pdf=rememberLauncherForActivityResult(OnKlavFileContracts.CreateDocument("application/pdf")){it?.let{u->model.export(u,"pdf")}}
    val html=rememberLauncherForActivityResult(OnKlavFileContracts.CreateDocument("text/html")){it?.let{u->model.export(u,"html")}}
    val txt=rememberLauncherForActivityResult(OnKlavFileContracts.CreateDocument("text/plain")){it?.let{u->model.export(u,"txt")}}
    val own=rememberLauncherForActivityResult(OnKlavFileContracts.CreateDocument("application/json")){it?.let{u->model.export(u,"write")}}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    DisposableEffect(model,lifecycle){val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_STOP)model.flush()};lifecycle.addObserver(observer);onDispose{lifecycle.removeObserver(observer);model.flush()}}
    fun character(kind:String,value:String?){editor?.let{e->model.change(WriteDocument.capture(doc.id,doc.title,e.editableText),false);e.format(kind,value);selectionVersion++}}
    fun toggle(kind:String){val e=editor?:return;val (a,b)=model.selection;character(kind,if(WriteFormatting.active(e.editableText,a,b,kind))null else "1")}
    fun active(kind:String):Boolean{selectionVersion;val e=editor?:return false;return WriteFormatting.active(e.editableText,model.selection.first,model.selection.second,kind)}
    fun search(back:Boolean=false){val e=editor?:return;if(find.isBlank()){findStatus="Enter text to find";return};val text=e.text.toString();val a=if(back)text.lastIndexOf(find,(e.selectionStart-1).coerceAtLeast(0),true).let{if(it<0)text.lastIndexOf(find,ignoreCase=true)else it}else text.indexOf(find,e.selectionEnd,ignoreCase=true).let{if(it<0)text.indexOf(find,ignoreCase=true)else it};if(a<0)findStatus="No matches"else{e.requestFocus();e.setSelection(a,a+find.length);e.bringPointIntoView(a);findStatus="Match ${a+1}"}}
    var formatRange by remember{mutableStateOf<Pair<Int,Int>?>(null)}
    var chosenInk by remember{mutableIntStateOf(0xff111111.toInt())}
    var chosenHighlight by remember{mutableIntStateOf(0xffffd54f.toInt())}
    fun hideKeyboard(){editor?.let{e->(context.getSystemService(Context.INPUT_METHOD_SERVICE)as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(e.windowToken,0)}}
    fun openTool(value:String){
        if(panel==value){panel="";return}
        if(panel !in setOf("Text","Lists","Align","Indent","Color","Highlight","Custom Color","Custom Highlight"))formatRange=model.selection
        if(value in setOf("Text","Lists","Align","Indent","Color","Highlight","Custom Color","Custom Highlight"))editor?.beginFormatting()else hideKeyboard()
        if(value=="Review")reviewText=editor?.text?.toString()?:doc.text
        if(value=="Link"){link="";colorError=""}
        if(value.startsWith("Custom")){customColor="";colorError=""}
        panel=value
    }
    SideEffect{editor?.let{e->e.formattingToolsOpen=panel in setOf("Text","Lists","Align","Indent","Color","Highlight","Custom Color","Custom Highlight");e.resumeEditing={panel="";formatRange=null}}}
    androidx.activity.compose.BackHandler(enabled=panel in setOf("Text","Lists","Align","Indent","Color","Highlight")){panel=""}
    fun restoreRange(){formatRange?.let{(a,b)->editor?.let{e->e.setSelection(a.coerceIn(0,e.length()),b.coerceIn(a.coerceIn(0,e.length()),e.length()))}}}
    fun applyChoice(kind:String,value:String?){restoreRange();character(kind,value);if(kind=="color")chosenInk=value?.toInt()?:0xff111111.toInt();if(kind=="highlight")chosenHighlight=value?.toInt()?:0xffffd54f.toInt()}
    fun paragraphChoice(kind:String,value:String){restoreRange();editor?.paragraph(kind,value)}
    val selectedStart=model.selection.first
    selectionVersion
    val currentInk=editor?.editableText?.getSpans(selectedStart,selectedStart,ForegroundColorSpan::class.java)?.lastOrNull{editor!!.editableText.getSpanEnd(it)>selectedStart}?.foregroundColor?:chosenInk
    val currentHighlight=editor?.editableText?.getSpans(selectedStart,selectedStart,BackgroundColorSpan::class.java)?.lastOrNull{editor!!.editableText.getSpanEnd(it)>selectedStart}?.backgroundColor?:chosenHighlight
    val currentSize=editor?.editableText?.getSpans(selectedStart,selectedStart,AbsoluteSizeSpan::class.java)?.lastOrNull{editor!!.editableText.getSpanEnd(it)>selectedStart}?.size?:16
    val actions=toolbar?:remember{WriteToolbarState()}
    val canAct=model.initialized&&!model.busy&&!model.openingFailed
    SideEffect{actions.enabled=canAct;actions.find={openTool("Find")};actions.documents={model.refresh();folder=true};actions.save={panel="Save"};actions.settings={panel="Settings"}}
    DisposableEffect(toolbar){onDispose{toolbar?.let{it.enabled=false;it.find={};it.documents={};it.save={};it.settings={}}}}
    val words=remember(doc.text){Regex("\\S+").findAll(doc.text).count()}
    Surface(Modifier.fillMaxSize()) {Column(Modifier.fillMaxSize()){
        if(toolbar==null)Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){WriteHeaderButtons(actions)}
        WriteTools(canAct,model.canUndo,model.canRedo,::active,panel,currentInk,currentHighlight,currentSize,
            ::openTool,{panel=""},::toggle,::applyChoice,::paragraphChoice,
            {delta->restoreRange();editor?.let{e->val at=e.selectionStart;val current=e.editableText.getSpans(at,at,LeadingMarginSpan.Standard::class.java).lastOrNull()?.getLeadingMargin(true)?:0;e.paragraph("indent",(current+delta).coerceIn(0,240).toString())}},
            {restoreRange();val e=editor;if(e!=null){val(a,b)=model.selection;e.guarded=true;listOf("bold","italic","underline","strike","color","highlight","size","font","relative","link").forEach{WriteFormatting.apply(e.editableText,a,b,it,null)};e.guarded=false;model.change(WriteDocument.capture(doc.id,doc.title,e.editableText),false);selectionVersion++}},
            {hideKeyboard();imagePicker.launch(arrayOf("image/*"))},{panel="";model.history(true)},{panel="";model.history(false)},rule={panel="";restoreRange();editor?.insertRule()})
        if(panel=="Find")AlertDialog(onDismissRequest={panel=""},title={Text("Find & replace")},text={Column(Modifier.heightIn(max=300.dp).verticalScroll(rememberScrollState())){
            OutlinedTextField(value=find,onValueChange={find=it;findStatus=""},label={Text("Find")},singleLine=true,modifier=Modifier.fillMaxWidth());OutlinedTextField(value=replacement,onValueChange={replacement=it},label={Text("Replace with")},singleLine=true,modifier=Modifier.fillMaxWidth())
            FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)){TextButton(onClick={search(true)}){Text("Previous")};TextButton(onClick={search()}){Text("Next")};TextButton(onClick={val e=editor?:return@TextButton;val a=e.selectionStart;val b=e.selectionEnd;if(find.isNotEmpty()&&e.text.substring(a,b).equals(find,true)){e.editableText.replace(a,b,replacement);findStatus="Replaced"}else search()}){Text("Replace")};TextButton(onClick={val e=editor?:return@TextButton;if(find.isBlank()){findStatus="Enter text to find";return@TextButton};val matches=Regex(Regex.escape(find),RegexOption.IGNORE_CASE).findAll(e.text.toString()).toList();val length=e.length().toLong()+matches.size.toLong()*(replacement.length.toLong()-find.length);if(length>500000L){findStatus="Replacement exceeds the document limit";return@TextButton};e.guarded=true;matches.asReversed().forEach{e.editableText.replace(it.range.first,it.range.last+1,replacement)};WriteFormatting.normalizeLists(e.editableText);e.guarded=false;model.selection=e.selectionStart.coerceAtLeast(0) to e.selectionEnd.coerceAtLeast(0);model.change(WriteDocument.capture(doc.id,doc.title,e.editableText),false);findStatus="${matches.size} replaced"}){Text("Replace all")}};if(findStatus.isNotBlank())Text(findStatus,style=MaterialTheme.typography.bodySmall)
        }},confirmButton={TextButton(onClick={panel="";editor?.let{it.requestFocus();it.bringPointIntoView(it.selectionEnd)}}){Text("Done")}})
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal=10.dp,vertical=6.dp).background(androidx.compose.ui.graphics.Color.White,RoundedCornerShape(12.dp)).border(1.dp,MaterialTheme.colorScheme.outlineVariant,RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp))){
            if(model.openingFailed)Column(Modifier.align(Alignment.Center).padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                Text("Unable to open this document",style=MaterialTheme.typography.titleLarge)
                Text("Your original is unchanged. Retry, or choose another document.")
                Button(onClick={model.retryOpening()}){Text("Retry opening")}
                TextButton(onClick={model.refresh();folder=true}){Text("Documents")}
            }else AndroidView(factory={c->WriteNativeEditor(c).also{e->editor=e;e.contentDescription="Document text";e.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,with(density){16.dp.toPx()*density.fontScale});e.highlightColor=(ink and 0xffffff) or 0x44000000;e.textCursorDrawable=android.graphics.drawable.GradientDrawable().apply{setColor(ink);setSize(with(density){2.dp.roundToPx()},with(density){24.dp.roundToPx()})};e.load(model.doc,model.revision,model.selection.first,model.selection.second);e.changed={s,coalesce->model.change(WriteDocument.capture(model.doc.id,model.doc.title,s),coalesce);selectionVersion++};e.selected={a,b->model.selection=a to b;selectionVersion++}}},update={e->editor=e;e.changed={s,coalesce->model.change(WriteDocument.capture(model.doc.id,model.doc.title,s),coalesce);selectionVersion++};e.selected={a,b->model.selection=a to b;selectionVersion++};if(e.documentId!=doc.id||e.revision!=model.revision)e.load(doc,model.revision,model.selection.first,model.selection.second);e.isEnabled=model.initialized&&!model.busy},modifier=Modifier.fillMaxSize())
            if(model.busy)CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=10.dp),verticalAlignment=Alignment.CenterVertically){
            TextButton(modifier=Modifier.weight(1f).semantics{contentDescription="Rename document"},enabled=canAct,onClick={name=doc.title;rename=true}){Text(doc.title,Modifier.fillMaxWidth(),textAlign=androidx.compose.ui.text.style.TextAlign.Start,style=MaterialTheme.typography.labelMedium,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)}
            Column(Modifier.clickable(enabled=!model.openingFailed){panel="Save"},horizontalAlignment=Alignment.End){
                Text("$words words",Modifier.semantics{contentDescription="$words words, ${doc.text.length} characters"},style=MaterialTheme.typography.labelSmall)
                Text(if(model.status in setOf("Saved","Saving…","Opening…"))model.status else if(model.status.startsWith("Not saved")||model.status.startsWith("Unable"))"Not saved · details"else"Save details",Modifier.semantics{contentDescription="Save status: ${model.status}"},style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

    }}
    LaunchedEffect(doc.id,doc.text,doc.marks,model.revision,proofEnabled,ignoredWords){
        delay(650)
        val text=doc.text
        val issues=if(proofEnabled)withContext(Dispatchers.Default){WriteEnglish.check(context,text,ignoredWords)}else emptyList()
        editor?.let{e->if(e.text.toString()==text){
            e.editableText.getSpans(0,e.length(),android.text.style.SuggestionSpan::class.java).forEach{e.editableText.removeSpan(it)}
            issues.forEach{issue->val flags=android.text.style.SuggestionSpan.FLAG_EASY_CORRECT or (if(issue.kind=="grammar"&&android.os.Build.VERSION.SDK_INT>=31)android.text.style.SuggestionSpan.FLAG_GRAMMAR_ERROR else android.text.style.SuggestionSpan.FLAG_MISSPELLED)
                e.editableText.setSpan(android.text.style.SuggestionSpan(context,java.util.Locale.US,issue.suggestions.toTypedArray(),flags,null),issue.start,issue.end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)}
        }}
    }
    LaunchedEffect(panel,reviewText,ignoredWords){if(panel=="Review"){
        reviewStatus="Checking English…";val text=reviewText;val generation=++reviewGeneration
        val issues=withContext(Dispatchers.Default){WriteEnglish.check(context,text,ignoredWords)}
        if(generation==reviewGeneration&&panel=="Review"){review=issues;reviewStatus=if(issues.isEmpty())"No spelling or supported grammar issues found."else "Suggestions to review: ${issues.size}"}
    }}
    if(panel=="Review")AlertDialog(onDismissRequest={reviewGeneration++;panel=""},title={Text("Spelling & grammar")},text={Column(Modifier.fillMaxWidth().heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
        Text(reviewStatus,style=MaterialTheme.typography.bodySmall)
        if(editor?.text?.toString()!=reviewText)TextButton(onClick={reviewText=editor?.text?.toString()?:doc.text}){Text("Check current text")}
        review.filter{it.start>=0&&it.end<=reviewText.length&&it.end>it.start}.forEach{issue->
            Surface(shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.surfaceContainerHigh){Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                Text(issue.message,style=MaterialTheme.typography.labelMedium);Text(reviewText.substring(issue.start,issue.end),style=MaterialTheme.typography.bodyLarge)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){issue.suggestions.forEach{value->OutlinedButton(onClick={val e=editor;if(e!=null&&e.text.toString()==reviewText){e.editableText.replace(issue.start,issue.end,value);reviewText=e.text.toString()}else{reviewStatus="The document changed. Check current text before applying.";review=emptyList()}}){Text(value)}}
                    if(issue.kind=="spelling")TextButton(onClick={ignoredWords=ignoredWords+reviewText.substring(issue.start,issue.end).lowercase(java.util.Locale.US).replace('’','\'')}){Text("Ignore word")}
                }
            }}
        }
        Text("English (US) · included offline dictionary. Grammar checks cover common rules, not every meaning or writing style. Up to 100 suggestions appear at a time.",style=MaterialTheme.typography.bodySmall)
    }},confirmButton={TextButton(onClick={reviewGeneration++;panel=""}){Text("Done")}})
    if(panel=="Settings")AlertDialog(onDismissRequest={panel=""},title={Text("Write settings")},text={Column(Modifier.fillMaxWidth().heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text("Your draft saves automatically. The Save icon opens Save, Discard and export choices. Tap the document name at the bottom to rename it.",style=MaterialTheme.typography.bodyMedium)
        HorizontalDivider();Text("English proofreading",style=MaterialTheme.typography.titleSmall)
        Text("$words words · ${doc.text.length} characters",style=MaterialTheme.typography.bodySmall)
        Text("163,006 dictionary forms are included and work offline. No Android dictionary setup is required.")
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("Underline spelling and grammar",Modifier.weight(1f));Switch(checked=proofEnabled,onCheckedChange={proofEnabled=it;proofPrefs.edit().putBoolean("underline",it).apply()})}
        Text("Checks include spelling, repeated words, capitalization, spacing, a/an and common subject–verb agreement. Suggestions never rewrite your document automatically.",style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick={reviewText=editor?.text?.toString()?:doc.text;panel="Review"},modifier=Modifier.fillMaxWidth()){Text("Spelling & grammar")}
        TextButton(onClick={panel="Dictionary license"}){Text("Dictionary source & license")}
        HorizontalDivider();Text("Select text, then choose a tool. Color and highlight choices replace the toolbar without moving your page or hiding the keyboard. Other formatting menus open beside the toolbar. Tap the page to continue typing. Link and image tools are on the second row.",style=MaterialTheme.typography.bodySmall)
    }},confirmButton={TextButton(onClick={panel=""}){Text("Done")}})
    if(panel=="Dictionary license")AlertDialog(onDismissRequest={panel="Settings"},title={Text("English dictionary")},text={val license=remember{context.assets.open("write/english-COPYRIGHT.txt").bufferedReader().use{it.readText()}};Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){Text("SCOWL / Hunspell en_US · 2020.12.07. Expanded inflections are bundled with Write. Original notices follow.");Text(license,style=MaterialTheme.typography.bodySmall)}},confirmButton={TextButton(onClick={panel="Settings"}){Text("Done")}})
    WriteDocumentsDrawer(visible=folder,
        documents=if(model.openingFailed)model.documents else (listOf(doc)+model.documents.filter{it.id!=doc.id}),current=doc.id,busy=model.busy,query=documentQuery,onQuery={documentQuery=it},
        onClose={folder=false},onNew={folder=false;model.open()},onImport={folder=false;importer.launch(arrayOf("text/plain","text/html","application/json","application/vnd.openxmlformats-officedocument.wordprocessingml.document","application/octet-stream"))},
        onOpen={folder=false;model.open(it)},onDelete={remove=it},onDuplicate={folder=false;model.open(doc.id,true)})
    if(rename)AlertDialog(onDismissRequest={rename=false},title={Text("Document name")},text={OutlinedTextField(value=name,onValueChange={name=it.take(120)},singleLine=true,label={Text("Name")})},confirmButton={TextButton(onClick={model.rename(name);rename=false}){Text("Save")}},dismissButton={TextButton(onClick={rename=false}){Text("Cancel")}})
    remove?.let{entry->AlertDialog(onDismissRequest={remove=null},title={Text("Delete ${entry.title}?")},text={Text("Deletes this saved document. Documents linked in Files can be restored from Files > Trash.")},confirmButton={TextButton(onClick={model.delete(entry.id);remove=null}){Text("Delete")}},dismissButton={TextButton(onClick={remove=null}){Text("Cancel")}})}
    if(panel=="Link")AlertDialog(onDismissRequest={panel=""},title={Text("Link")},text={Column{TextButton(onClick={applyChoice("link",null);panel=""}){Text("Remove link")};OutlinedTextField(value=link,onValueChange={link=it.take(2048)},singleLine=true,label={Text("https://…")});if(colorError.isNotBlank())Text(colorError)}},confirmButton={TextButton(onClick={runCatching{safeLink(link)}.onSuccess{applyChoice("link",it);hideKeyboard();panel="";colorError=""}.onFailure{colorError=it.message.orEmpty()}}){Text("Apply")}},dismissButton={TextButton(onClick={panel="";colorError=""}){Text("Cancel")}})
    if(panel=="Custom Color"||panel=="Custom Highlight")AlertDialog(onDismissRequest={panel=panel.removePrefix("Custom ")},title={Text("Custom color")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        OutlinedTextField(value=customColor,onValueChange={customColor=it.take(7);colorError=""},singleLine=true,label={Text("#RRGGBB")});if(colorError.isNotBlank())Text(colorError,color=MaterialTheme.colorScheme.error)
    }},confirmButton={TextButton(onClick={if(customColor.matches(Regex("#[0-9a-fA-F]{6}"))){applyChoice(if(panel=="Custom Color")"color"else"highlight",Color.parseColor(customColor).toString());panel=panel.removePrefix("Custom ")}else colorError="Enter a color such as #2456A0"}){Text("Apply")}},dismissButton={TextButton(onClick={panel=panel.removePrefix("Custom ")}){Text("Cancel")}})
    if(panel=="Save")WriteSaveDialog(format,model.busy||model.status=="Saving…",{format=it},{model.saveNow(onSaved={panel=""})},{model.discard{panel="";onClose()}},{
        panel="";val title=doc.title.replace(Regex("[^\\p{L}\\p{N} ._-]"),"_").take(80).ifBlank{"Document"}
        when(format){"docx"->docx.launch("$title.docx");"pdf"->pdf.launch("$title.pdf");"html"->html.launch("$title.html");"txt"->txt.launch("$title.txt");else->own.launch("$title.write")}
    },{panel=""},model.status)
}
