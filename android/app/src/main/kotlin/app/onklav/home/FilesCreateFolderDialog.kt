package app.onklav.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/** Presentation only: creation still passes through the signed Files controller. */
@Composable internal fun FilesCreateFolderDialog(model:SdkAppModel,onClose:()->Unit){
 val state=model.frame.child("state")
 var name by remember{mutableStateOf(state.optString("name"))}
 val enabled=!model.busy&&model.pending==null
 fun create(){if(enabled&&name.isNotBlank()){model.edit("name",name);model.action("name.save")}}
 AlertDialog(onDismissRequest=onClose,icon={Icon(Icons.Outlined.AddCircle,null)},title={Text("New folder")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
  Text("Create in ${state.optString("title").takeIf{state.optString("folder").isNotBlank()}?:"Documents"}",style=MaterialTheme.typography.bodyMedium)
  OutlinedTextField(name,{name=it.take(180)},enabled=enabled,label={Text("Folder name")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("sdk-name"),keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={create()}))
  state.optString("status").takeIf{it.isNotBlank()}?.let{Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
 }},confirmButton={Button(modifier=Modifier.testTag("sdk-name.save"),enabled=enabled&&name.isNotBlank(),onClick={create()}){Text("Create folder")}},dismissButton={TextButton(enabled=enabled,onClick=onClose){Text("Cancel")}})
}
