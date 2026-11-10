package app.onklav.home

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import app.onklav.shared.OnKlavFileContracts
import app.onklav.shared.R

@Composable internal fun CodeEditWorkspace(exit:()->Unit,m:CodeEditModel=viewModel()){
 val c=LocalContext.current;val colors=MaterialTheme.colorScheme;val d=m.current
 val keyboard=WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current)>0
 var native by remember{mutableStateOf<CodeEditorView?>(null)}
 var menu by remember{mutableStateOf(false)};var panel by remember{mutableStateOf<String?>(null)};var query by remember{mutableStateOf("")};var replacement by remember{mutableStateOf("")};var matchCase by remember{mutableStateOf(false)};var line by remember{mutableStateOf("")};var name by remember{mutableStateOf("Untitled.rs")};var closing by remember{mutableStateOf<String?>(null)};var confirmReplace by remember{mutableStateOf(false)}
 fun back(){m.flush();exit()}
 BackHandler{if(panel!=null)panel=null else back()}
 LifecycleEventEffect(Lifecycle.Event.ON_STOP){m.flush()}
 DisposableEffect(Unit){onDispose{native?.release();m.flush()}}
 val open=rememberLauncherForActivityResult(OnKlavFileContracts.OpenDocument()){it?.let(m::open)}
 val save=rememberLauncherForActivityResult(OnKlavFileContracts.CreateDocument("text/plain")){it?.let{m.save(it)}}
 fun saveFile(){if(m.canSaveBack())m.save()else save.launch(d?.name?:"Untitled.txt")}
 fun apply(e:CodeText.Edit){m.apply(e);native?.focusEditor()}
 fun find(back:Boolean=false){val current=m.current?:return;val at=CodeText.find(current.text,query,if(back)minOf(current.start,current.end)else maxOf(current.start,current.end),back,matchCase);if(at<0)m.message("No matches")else{m.selection(at,at+query.length);m.message("Match · line ${current.text.take(at).count{it=='\n'}+1}")}}
 Surface(Modifier.fillMaxSize(),color=colors.surface){Column(Modifier.fillMaxSize().imePadding()){
  if(!keyboard)HomeHeader("Code Edit",back=::back)
  Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically){
   VideoIcon(R.drawable.maps_folder,"Open code file",!m.busy){open.launch(arrayOf("*/*"))}
   VideoIcon(R.drawable.ref_action_add,"New code file",!m.busy){panel="New"}
   Spacer(Modifier.weight(1f))
   VideoIcon(R.drawable.audio_edit_undo,"Undo",!m.busy&&m.canUndo){m.history(true)};VideoIcon(R.drawable.audio_edit_redo,"Redo",!m.busy&&m.canRedo){m.history(false)}
   VideoIcon(R.drawable.photo_save,"Save code file",!m.busy&&d!=null){saveFile()}
   Box{VideoIcon(R.drawable.ref_browser_more,"Code options",!m.busy){menu=true};DropdownMenu(expanded=menu,onDismissRequest={menu=false}){
    listOf("Find & replace","Open tabs","Go to line","Language","Editor settings","Save as","Select all","Toggle comment","Validate JSON").forEach{label->DropdownMenuItem(text={Text(label)},onClick={menu=false;when(label){"Save as"->save.launch(d?.name?:"Untitled.txt");"Select all"->d?.let{m.selection(0,it.text.length)};"Toggle comment"->d?.let{if(it.language in listOf("HTML","XML","CSS","JSON","Plain text","Markdown"))m.message("Line comments are unavailable in ${it.language}")else apply(CodeText.comment(it.text,it.start,it.end,it.language))};"Validate JSON"->m.validateJson();else->panel=label}})}
   }}
  }
  if(!keyboard)LazyRow(Modifier.fillMaxWidth().background(colors.surfaceContainerLow),horizontalArrangement=Arrangement.spacedBy(4.dp),contentPadding=PaddingValues(horizontal=8.dp)){
   items(m.documents,key={it.id}){doc->val active=doc.id==m.selected;Surface(color=if(active)colors.secondaryContainer else colors.surfaceContainerLow,shape=RoundedCornerShape(topStart=10.dp,topEnd=10.dp),modifier=Modifier.padding(top=4.dp)){
    Row(verticalAlignment=Alignment.CenterVertically){TextButton(onClick={m.pick(doc.id)},enabled=!m.busy,modifier=Modifier.widthIn(max=190.dp).semantics{selected=active;contentDescription="File ${doc.name}"}){Text((if(runCatching{doc.dirty}.getOrDefault(true))"• "else"")+doc.name,maxLines=1,overflow=TextOverflow.Ellipsis)};TextButton(onClick={m.pick(doc.id);if(runCatching{doc.dirty}.getOrDefault(true))closing=doc.id else m.close(doc.id)},enabled=!m.busy,modifier=Modifier.width(48.dp).semantics{contentDescription="Close ${doc.name}"}){Text("×")}}
   }}
  }
  if(m.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
  if(d!=null)AndroidView(factory={CodeEditorView(it).also{v->native=v}},modifier=Modifier.fillMaxWidth().weight(1f),update={v->v.bind(d,m.wrap,m.font,!m.busy,m.highlight,colors.onSurface.toArgb(),colors.onSurfaceVariant.toArgb(),if(androidx.core.graphics.ColorUtils.calculateLuminance(colors.surface.toArgb())>.5)intArrayOf(0xff637568.toInt(),0xff236b3b.toInt(),0xff9a5b13.toInt(),0xff7542a0.toInt(),0xff166e95.toInt())else intArrayOf(0xff95a59b.toInt(),0xff90d5a3.toInt(),0xfff2c080.toInt(),0xffc7a2ed.toInt(),0xff86c8e8.toInt()),{text,a,b->m.change(text,a,b)},m::selection);v.indentUnit=m.indent;v.input.setOnKeyListener{_,key,event->if(event.action!=android.view.KeyEvent.ACTION_DOWN)false else if(event.isCtrlPressed){when(key){android.view.KeyEvent.KEYCODE_S->{saveFile();true};android.view.KeyEvent.KEYCODE_O->{open.launch(arrayOf("*/*"));true};android.view.KeyEvent.KEYCODE_F->{panel="Find & replace";true};android.view.KeyEvent.KEYCODE_P->{panel="Open tabs";true};android.view.KeyEvent.KEYCODE_Z->{m.history(!event.isShiftPressed);true};android.view.KeyEvent.KEYCODE_Y->{m.history(false);true};else->false}}else if(key==android.view.KeyEvent.KEYCODE_TAB){m.current?.let{current->apply(if(event.isShiftPressed||current.start!=current.end)CodeText.indent(current.text,current.start,current.end,m.indent,event.isShiftPressed)else CodeText.insert(current.text,current.start,current.end,m.indent))};true}else false}})else Spacer(Modifier.weight(1f))
  if(panel=="Find & replace")Column(Modifier.fillMaxWidth().padding(horizontal=8.dp),verticalArrangement=Arrangement.spacedBy(2.dp)){
   Row(verticalAlignment=Alignment.CenterVertically){OutlinedTextField(query,{query=it.take(500)},label={Text("Find")},singleLine=true,modifier=Modifier.weight(1f));TextButton(onClick={find(true)},enabled=query.isNotEmpty(),modifier=Modifier.width(48.dp).semantics{contentDescription="Previous match"}){Text("↑")};TextButton(onClick={find()},enabled=query.isNotEmpty(),modifier=Modifier.width(48.dp).semantics{contentDescription="Next match"}){Text("↓")};TextButton(onClick={panel=null},modifier=Modifier.width(48.dp).semantics{contentDescription="Close search"}){Text("×")}}
   Row(verticalAlignment=Alignment.CenterVertically){OutlinedTextField(replacement,{replacement=it.take(10000)},label={Text("Replace with")},singleLine=true,modifier=Modifier.weight(1f));TextButton(onClick={val v=m.current?:return@TextButton;val a=minOf(v.start,v.end);val b=maxOf(v.start,v.end);if(query.isNotEmpty()&&v.text.substring(a,b).equals(query,!matchCase))m.apply(CodeText.insert(v.text,a,b,replacement))else find()},enabled=query.isNotEmpty()){Text("Replace")};var searchMenu by remember{mutableStateOf(false)};Box{TextButton(onClick={searchMenu=true},modifier=Modifier.width(48.dp).semantics{contentDescription="Search options"}){Text("⋮")};DropdownMenu(expanded=searchMenu,onDismissRequest={searchMenu=false}){DropdownMenuItem(text={Text((if(matchCase)"✓ "else"")+"Match case")},onClick={matchCase=!matchCase;searchMenu=false});DropdownMenuItem(text={Text("Replace all")},enabled=query.isNotEmpty(),onClick={searchMenu=false;confirmReplace=true})}}}
  }
  if(d!=null){
   if(panel!="Find & replace")Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).background(colors.surfaceContainerLow),horizontalArrangement=Arrangement.spacedBy(2.dp)){
    listOf("Tab","⇤","{ }","( )","[ ]","\"","'",":",";","=","/","<",">","_").forEach{key->TextButton(enabled=!m.busy,onClick={val current=m.current?:return@TextButton;runCatching{when(key){"Tab"->if(current.start==current.end)apply(CodeText.insert(current.text,current.start,current.end,m.indent))else apply(CodeText.indent(current.text,current.start,current.end,m.indent));"⇤"->apply(CodeText.indent(current.text,current.start,current.end,m.indent,true));"{ }","( )","[ ]"->{val left=key.first().toString();val right=key.last().toString();val a=minOf(current.start,current.end);val b=maxOf(current.start,current.end);val e=CodeText.insert(current.text,a,b,left+current.text.substring(a,b)+right);apply(e.copy(start=a+1,end=b+1))};else->apply(CodeText.insert(current.text,current.start,current.end,key))}}.onFailure{m.message(it.message?:"Edit exceeds file limit")}},modifier=Modifier.widthIn(min=48.dp).heightIn(min=48.dp)){Text(key)}}
   }
   Row(Modifier.fillMaxWidth().padding(horizontal=10.dp),verticalAlignment=Alignment.CenterVertically){TextButton(onClick={panel="Go to line"},contentPadding=PaddingValues(0.dp)){Text("Ln ${d.text.take(d.start).count{it=='\n'}+1}, Col ${d.start-(d.text.lastIndexOf('\n',d.start-1)+1)+1}",style=MaterialTheme.typography.labelSmall)};if(keyboard)TextButton(onClick={panel="Open tabs"},modifier=Modifier.weight(1f)){Text(d.name,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.labelSmall)}else Spacer(Modifier.weight(1f));TextButton(onClick={panel="Language"},contentPadding=PaddingValues(4.dp)){Text(d.language,style=MaterialTheme.typography.labelSmall)};if(!keyboard)Text("${d.encoding} · ${d.ending}",style=MaterialTheme.typography.labelSmall)}
  }
  if(!keyboard)Text(m.status,Modifier.fillMaxWidth().padding(horizontal=10.dp,vertical=4.dp).semantics{liveRegion=LiveRegionMode.Polite},maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.labelSmall,color=colors.onSurfaceVariant)
 }}
 if(panel in listOf("Go to line","New","Language","Editor settings","Open tabs"))AlertDialog(onDismissRequest={panel=null},title={Text(panel!!)},text={Column(Modifier.heightIn(max=380.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
  when(panel){
   "Open tabs"->m.documents.forEach{file->TextButton(onClick={m.pick(file.id);panel=null}){Text((if(file.id==m.selected)"✓ "else"")+file.name)}}
   "New"->OutlinedTextField(name,{name=it.take(120)},label={Text("File name")},singleLine=true,isError=name.isBlank()||name.any{it=='/'||it=='\\'||it.isISOControl()})
   "Go to line"->OutlinedTextField(line,{line=it.filter{ch->ch.isDigit()}.take(8)},label={Text("Line number")},singleLine=true,keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Number))
   "Language"->CodeText.languages.forEach{lang->TextButton(onClick={m.language(lang);panel=null}){Text((if(d?.language==lang)"✓ "else"")+lang)}}
   "Editor settings"->{Row(verticalAlignment=Alignment.CenterVertically){Switch(m.wrap,{m.wrap=it;m.persist()});Text("Wrap long lines")};Row(verticalAlignment=Alignment.CenterVertically){Switch(m.highlight,{m.highlight=it});Text("Syntax colors")};Text("Font size · ${m.font.toInt()}");Slider(m.font,{m.font=it;m.persist()},valueRange=10f..24f);Text("Indentation");listOf("2 spaces" to "  ","4 spaces" to "    ","Tab character" to "\t").forEach{(label,value)->TextButton(onClick={m.indent=value;m.persist()}){Text((if(m.indent==value)"✓ "else"")+label)}};Text("Local text editor. Code is not executed. UTF-8 and UTF-16 text up to 1 MB; eight open files.",style=MaterialTheme.typography.bodySmall)}
  }
 }},confirmButton={TextButton(enabled=panel!="New"||(name.isNotBlank()&&name.none{it=='/'||it=='\\'||it.isISOControl()}),onClick={when(panel){"New"->{if(name.isNotBlank()&&name.none{it=='/'||it=='\\'||it.isISOControl()}){m.new(name);panel=null}};"Go to line"->{val target=line.toIntOrNull();if(target!=null&&target>0&&d!=null){var offset=0;repeat((target-1).coerceAtMost(d.text.count{it=='\n'})){offset=d.text.indexOf('\n',offset)+1};m.selection(offset,offset);panel=null;native?.focusEditor()}};else->panel=null}}){Text(if(panel=="New")"Create"else if(panel=="Go to line")"Go"else"Done")}})
 if(confirmReplace)AlertDialog(onDismissRequest={confirmReplace=false},title={Text("Replace all matches?")},text={Text("This affects only the current file. You can Undo.")},confirmButton={TextButton(onClick={confirmReplace=false;val v=m.current;if(v!=null&&query.isNotEmpty())runCatching{val next=CodeText.replaceAll(v.text,query,replacement,matchCase);m.apply(CodeText.Edit(next,v.start.coerceAtMost(next.length)));m.message("Replaced all matches · Undo available")}.onFailure{m.message("Replacement exceeds the file limit")}}){Text("Replace all")}},dismissButton={TextButton(onClick={confirmReplace=false}){Text("Cancel")}})
 if(closing!=null)AlertDialog(onDismissRequest={closing=null},title={Text("Unsaved file changes")},text={Text("Save to Files before closing, or discard this tab’s recovered draft.")},confirmButton={TextButton(onClick={closing=null;saveFile()}){Text("Save first")}},dismissButton={Row{TextButton(onClick={closing=null}){Text("Keep editing")};TextButton(onClick={closing?.let(m::close);closing=null}){Text("Discard")}}})
}
