package app.onklav.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import app.onklav.shared.*

@Composable
fun NumberEntryDialog(number:String,change:(String)->Unit,dismiss:()->Unit,choose:()->Unit,forwarding:Boolean=false) {
    val keyColors=dialPadColors(MaterialTheme.colorScheme)
    ReferenceDialog("Enter number",dismiss,footer={
        DialogAction("Cancel",onClick=dismiss)
        DialogAction(if(forwarding)"Forward Message" else "Start Message",primary=true,enabled=number.any{it.isDigit()},onClick=choose)
    }) {
        Column(Modifier.padding(horizontal=20.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(number,{change(phoneInput(it))},Modifier.fillMaxWidth().testTag("dialpad-number"),
                placeholder={Text("Phone number",Modifier.fillMaxWidth(),textAlign=TextAlign.Center,fontSize=24.sp)},singleLine=true,
                visualTransformation=PhoneVisualTransformation,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Phone),
                shape=RoundedCornerShape(12.dp),textStyle=MaterialTheme.typography.headlineSmall.copy(textAlign=TextAlign.Center),
                colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=MaterialTheme.colorScheme.primary,unfocusedBorderColor=MaterialTheme.colorScheme.primary))
            listOf(listOf("1","2","3"),listOf("4","5","6"),listOf("7","8","9"),listOf("+","0","⌫")).forEach { keys ->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    keys.forEach { key ->
                        Surface(Modifier.weight(1f).height(56.dp).testTag("dialpad-$key").semantics{contentDescription=when(key){"⌫"->"Delete digit";"+"->"Plus";else->key}}.clickable(role=Role.Button) {
                            change(when(key){"⌫"->number.dropLast(1);"+"->if(number.isEmpty())"+"else number;else->phoneInput(number+key)})
                        },shape=RoundedCornerShape(10.dp),color=keyColors.fill,border=BorderStroke(1.dp,keyColors.outline),shadowElevation=0.dp) {
                            Box(contentAlignment=Alignment.Center){Text(key,fontSize=30.sp,color=keyColors.ink)}
                        }
                    }
                }
            }
        }
    }
}
