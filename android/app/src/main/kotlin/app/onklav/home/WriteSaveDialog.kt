@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.onklav.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.onklav.shared.R

internal val writeExportFormats=listOf("docx" to "DOCX","pdf" to "PDF","html" to "HTML","txt" to "TXT","write" to "Write")
@Composable internal fun WriteSaveDialog(format:String,busy:Boolean,onFormat:(String)->Unit,onSave:()->Unit,onDiscard:()->Unit,onExport:()->Unit,onClose:()->Unit,status:String=""){
 AlertDialog(onDismissRequest=onClose,text={Column(Modifier.fillMaxWidth().heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
  Button(onClick=onSave,enabled=!busy,modifier=Modifier.fillMaxWidth()){Icon(painterResource(R.drawable.photo_save),null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text("Save (Overwrite)")}
  Button(onClick=onDiscard,enabled=!busy,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=androidx.compose.ui.graphics.Color(0xFF555861),contentColor=androidx.compose.ui.graphics.Color.White)){Text("Close (Discard Changes)")}
  Text("Autosave protects your draft. Discard restores the version from opening or your last Save.",style=MaterialTheme.typography.bodySmall)
  if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
  if(status.startsWith("Not saved")||status.startsWith("Unable")||status.contains("failed",true))Text(status,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
  HorizontalDivider();Text("Export a copy",style=MaterialTheme.typography.titleSmall)
  FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){writeExportFormats.forEach{(key,label)->FilterChip(selected=key==format,enabled=!busy,onClick={onFormat(key)},label={Text(label)})}}
  Text(when(format){"docx"->"Word document · text, formatting and embedded images.";"pdf"->"PDF · fixed pages for sharing or printing.";"html"->"Web document · formatted text, links and embedded images.";"txt"->"Plain text · removes formatting and images.";else->"Editable Write document · preserves Write formatting and images."},style=MaterialTheme.typography.bodySmall)
 }},confirmButton={TextButton(enabled=!busy,onClick=onExport){Text("Export ${writeExportFormats.first{it.first==format}.second}")}},dismissButton={TextButton(onClick=onClose){Text("Cancel")}})
}
