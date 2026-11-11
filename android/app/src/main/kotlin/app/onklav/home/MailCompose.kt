package app.onklav.home

import android.graphics.drawable.GradientDrawable
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@Composable internal fun MailCompose(m:MailModel,onAttach:()->Unit,onSend:()->Unit,onDelete:()->Unit){val d=m.draft?:return;val colors=MaterialTheme.colorScheme;var editor by remember(d.id){mutableStateOf<WriteNativeEditor?>(null)};var cc by remember(d.id){mutableStateOf(d.cc.isNotBlank()||d.bcc.isNotBlank())};var from by remember{mutableStateOf(false)};var palette by remember{mutableStateOf<String?>(null)};var link by remember{mutableStateOf(false)};var url by remember{mutableStateOf("")};var copy by remember{mutableStateOf(false)};var selection by remember{mutableIntStateOf(0)};val editable=d.phase=="draft"
 val density = LocalDensity.current
 val cursorColor = colors.onSurface.toArgb()
 val cursor = remember(cursorColor, density) {
  GradientDrawable().apply {
   setColor(cursorColor)
   setSize(with(density) { 2.dp.roundToPx() }, with(density) { 24.dp.roundToPx() })
  }
 }
 fun format(kind:String,value:String?="1"){editor?.let{e->val active=WriteFormatting.active(e.editableText,e.selectionStart,e.selectionEnd,kind);e.format(kind,if(active&&kind in listOf("bold","italic","underline","strike"))null else value);selection++}}
 BoxWithConstraints(Modifier.fillMaxSize()){val bodyHeight=(maxHeight-240.dp).coerceIn(180.dp,420.dp);Column(Modifier.fillMaxSize()){
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){app.onklav.shared.ClosePageButton(enabled=!m.busy,description=if(editable)"Save draft and close"else"Close draft",onClick=m::closeDraft);Spacer(Modifier.weight(1f));IconButton(onClick=onDelete,enabled=!m.busy){Icon(Icons.Outlined.Delete,"Delete local draft")};IconButton(onClick=onAttach,enabled=editable&&!m.busy){Icon(Icons.Outlined.AttachFile,"Attach files")};FilledTonalButton(onClick=onSend,enabled=editable&&!m.busy&&runCatching{MailMime.addresses(d.to).isNotEmpty()}.getOrDefault(false),modifier=Modifier.semantics{contentDescription="Send email"},contentPadding=PaddingValues(horizontal=12.dp)){Icon(Icons.Outlined.Send,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("Send")}}
   if(editable)Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(2.dp)){
    listOf("bold" to "B","italic" to "I","underline" to "U","strike" to "S̶").forEach{(kind,label)->val active=selection.let{editor?.let{e->WriteFormatting.active(e.editableText,e.selectionStart,e.selectionEnd,kind)}?:false};TextButton(onClick={format(kind)},enabled=!m.busy,modifier=Modifier.width(44.dp).semantics{contentDescription=kind;selected=active},colors=ButtonDefaults.textButtonColors(contentColor=if(active)colors.onSecondaryContainer else colors.onSurface),contentPadding=PaddingValues(4.dp)){Text(label,Modifier.background(if(active)colors.secondaryContainer else Color.Transparent).padding(5.dp),fontWeight=if(kind=="bold")androidx.compose.ui.text.font.FontWeight.Bold else null)}}
    IconButton(onClick={editor?.paragraph("list","bullet")},enabled=!m.busy){Icon(Icons.Outlined.FormatListBulleted,"Bulleted list")};IconButton(onClick={editor?.paragraph("list","number")},enabled=!m.busy){Icon(Icons.Outlined.FormatListNumbered,"Numbered list")};IconButton(onClick={editor?.paragraph("list","none")},enabled=!m.busy){Icon(Icons.Outlined.FormatClear,"Remove list")};IconButton(onClick={palette="color"},enabled=!m.busy){Icon(Icons.Outlined.FormatColorText,"Text color")};IconButton(onClick={palette="highlight"},enabled=!m.busy){Icon(Icons.Outlined.FormatColorFill,"Text background color")};IconButton(onClick={link=true},enabled=!m.busy){Icon(Icons.Outlined.Link,"Insert link")};IconButton(onClick={editor?.paragraph("align","ALIGN_NORMAL")},enabled=!m.busy){Icon(Icons.Outlined.FormatAlignLeft,"Align left")};IconButton(onClick={editor?.paragraph("align","ALIGN_CENTER")},enabled=!m.busy){Icon(Icons.Outlined.FormatAlignCenter,"Align center")};IconButton(onClick={editor?.paragraph("align","ALIGN_OPPOSITE")},enabled=!m.busy){Icon(Icons.Outlined.FormatAlignRight,"Align right")}
   }
  HorizontalDivider(color=colors.outlineVariant.copy(alpha=.4f))
  if(!editable){Column(Modifier.fillMaxWidth().padding(12.dp)){Text(if(d.phase=="sent")"Sent" else"Delivery unconfirmed",style=MaterialTheme.typography.titleSmall);if(d.phase!="sent")Text("Check your provider’s Sent folder before sending another copy.",style=MaterialTheme.typography.bodySmall);TextButton(onClick={copy=true},enabled=!m.busy){Text("Create a new draft from this message")}}}
  Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
   Box{TextButton(onClick={from=true},enabled=editable&&!m.busy){Text("From: ${m.accounts.firstOrNull{it.id==d.account}?.email.orEmpty()}",Modifier.weight(1f));Icon(Icons.Outlined.ExpandMore,null)};DropdownMenu(expanded=from,onDismissRequest={from=false}){m.accounts.forEach{a->DropdownMenuItem(text={Text(a.email)},onClick={m.changeDraft(d.copy(account=a.id));from=false})}}}
   Row(verticalAlignment=Alignment.CenterVertically){MailRecipientField("To",d.to,m,editable&&!m.busy,Modifier.weight(1f)){m.changeDraft(d.copy(to=it))};TextButton(onClick={cc=!cc}){Text("Cc/Bcc")}}
   if(cc){MailRecipientField("Cc",d.cc,m,editable&&!m.busy){m.changeDraft(d.copy(cc=it))};MailRecipientField("Bcc",d.bcc,m,editable&&!m.busy){m.changeDraft(d.copy(bcc=it))}}
   MailField("Subject",d.subject,editable&&!m.busy){m.changeDraft(d.copy(subject=it.take(998)))}
   Surface(shape=MaterialTheme.shapes.medium,color=colors.surfaceContainerLow,modifier=Modifier.fillMaxWidth()){
    AndroidView(factory={ctx->WriteNativeEditor(ctx,MAIL_APP).apply{hint="Write your message…";load(d.doc,0,0,0);editor=this}},update={e->editor=e;e.isEnabled=editable&&!m.busy;if(e.textCursorDrawable !== cursor)e.textCursorDrawable=cursor;e.setTextColor(cursorColor);e.setHintTextColor(colors.onSurfaceVariant.toArgb());e.setLinkTextColor(colors.primary.toArgb());e.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP,16f);if(e.documentId!=d.doc.id)e.load(d.doc,0,0,0);e.changed={text,_->m.draft?.let{live->m.changeDraft(live.copy(doc=WriteDocument.capture(live.doc.id,"Email",text)))}};e.selected={_,_->selection++}},modifier=Modifier.fillMaxWidth().height(bodyHeight).semantics{contentDescription="Email message body"})
   }
   d.files.forEach{f->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Outlined.AttachFile,null);Column(Modifier.weight(1f).padding(8.dp)){Text(f.name,style=MaterialTheme.typography.bodyMedium);Text(mailSize(f.size),style=MaterialTheme.typography.labelSmall,color=colors.onSurfaceVariant)};if(editable)IconButton(onClick={m.removeAttachment(f)},enabled=!m.busy){Icon(Icons.Outlined.Close,"Remove ${f.name}")}}}
   Spacer(Modifier.height(16.dp))
  }
 }
 }
 if(palette!=null)MailDialog(if(palette=="color")"Text color" else"Text background",{palette=null}){val values=listOf("Theme" to colors.onSurface,"Black" to Color.Black,"White" to Color.White,"Red" to Color(0xffbd3030),"Orange" to Color(0xffc36c14),"Gold" to Color(0xffb38c00),"Green" to Color(0xff237b45),"Blue" to Color(0xff2468c5),"Purple" to Color(0xff8050ad));values.chunked(3).forEach{row->Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){row.forEach{(name,color)->OutlinedButton(onClick={format(palette!!,color.toArgb().toString());palette=null},modifier=Modifier.weight(1f),contentPadding=PaddingValues(4.dp)){Box(Modifier.size(16.dp).background(color,CircleShape));Text(name,style=MaterialTheme.typography.labelSmall,modifier=Modifier.padding(start=3.dp))}}}};TextButton(onClick={format(palette!!,null);palette=null}){Text("Remove color")}}
 if(link)AlertDialog(onDismissRequest={link=false},title={Text("Insert link")},text={MailField("https://…",url){url=it}},confirmButton={TextButton(onClick={try{format("link",safeLink(url));link=false}catch(e:Exception){m.message(e.message.orEmpty(),true)}}){Text("Apply")}},dismissButton={TextButton(onClick={link=false}){Text("Cancel")}})
 if(copy)AlertDialog(onDismissRequest={copy=false},title={Text("Create another copy?")},text={Text("Check Sent first if delivery was unconfirmed. Sending this new draft may deliver the message again.")},confirmButton={TextButton(onClick={copy=false;m.changeDraft(d.copy(id=mailId(),phase="draft",doc=d.doc.copy(id=mailId())) )}){Text("Create draft")}},dismissButton={TextButton(onClick={copy=false}){Text("Cancel")}})
}
