package app.onklav.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale

/** Decimal-comma paste is accepted; invalid or out-of-range input never changes an edit. */
internal fun audioExactValue(text:String,range:ClosedFloatingPointRange<Float>,scale:Float=1f):Float? {
    if(!scale.isFinite() || scale<=0f)return null
    val input=text.trim().replace('\u2212','-')
    if(!input.matches(Regex("[+-]?(?:[0-9]+(?:[.,][0-9]*)?|[.,][0-9]+)")))return null
    val number=input.replace(',','.').toDoubleOrNull()?:return null
    val value=number/scale
    if(!value.isFinite() || value<range.start || value>range.endInclusive)return null
    return value.toFloat().takeIf{it.isFinite() && it in range}
}

internal fun audioExactText(value:Float,scale:Float=1f):String =
    String.format(Locale.US,"%.3f",value*scale).trimEnd('0').trimEnd('.').let{if(it=="-0")"0"else it}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun AudioHint(label:String,content:@Composable ()->Unit){
    TooltipBox(positionProvider=TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip={PlainTooltip{Text(label)}},state=rememberTooltipState(),content=content)
}

@Composable internal fun AudioExactValueDialog(title:String,value:Float,range:ClosedFloatingPointRange<Float>,enabled:Boolean,onDismiss:()->Unit,onApply:(Float)->Unit){
    val scale=if(title=="Tempo"||title=="Reduce noise")100f else 1f
    val unit=when(title){"Tempo","Reduce noise"->"%";"Fade in","Fade out"->"seconds";"Pitch"->"semitones";else->"dB"}
    val initial=audioExactText(value,scale)
    var input by rememberSaveable(title,stateSaver=TextFieldValue.Saver){mutableStateOf(TextFieldValue(initial,TextRange(0,initial.length)))}
    val parsed=audioExactValue(input.text,range,scale)
    val limits="${audioExactText(range.start,scale)} to ${audioExactText(range.endInclusive,scale)} $unit"
    val focus=remember{FocusRequester()}
    val apply={if(enabled&&parsed!=null)onApply(parsed)}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Set $title")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedTextField(value=input,onValueChange={input=it},enabled=enabled,singleLine=true,
                modifier=Modifier.fillMaxWidth().focusRequester(focus).testTag("audio-exact-input"),
                label={Text(title)},suffix={Text(unit)},isError=parsed==null,
                supportingText={Text("Allowed: $limits")},
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal,imeAction=ImeAction.Done),
                keyboardActions=KeyboardActions(onDone={apply()}))
            Text("Apply updates the value and slider together.",style=MaterialTheme.typography.bodySmall)
            LaunchedEffect(Unit){focus.requestFocus()}
        }
    },confirmButton={TextButton(enabled=enabled&&parsed!=null,onClick={apply()}){Text("Apply")}},
        dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
}

/** The caption stays compact; error/overflow details are explicit and retain a snapshot. */
@Composable internal fun AudioStatusMessage(status:String,error:Boolean,modifier:Modifier=Modifier,dialogTitle:String="Audio status",detailsDescription:String="Show audio status details"){
    val message=status.ifEmpty{"Quick preview · up to 12 s"}
    var overflow by remember(message){mutableStateOf(false)}
    var details by rememberSaveable{mutableStateOf<String?>(null)}
    val colors=MaterialTheme.colorScheme
    Row(modifier.semantics{liveRegion=LiveRegionMode.Polite},verticalAlignment=Alignment.CenterVertically){
        Text(message,Modifier.weight(1f),style=MaterialTheme.typography.labelSmall,
            color=if(error)colors.error else colors.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis,
            onTextLayout={overflow=it.hasVisualOverflow})
        if(status.isNotEmpty()&&(error||overflow))AudioHint("Read full message"){
            IconButton(onClick={details=message},modifier=Modifier.size(48.dp)){
                Icon(Icons.Outlined.Info,detailsDescription,tint=if(error)colors.error else colors.onSurfaceVariant)
            }
        }
    }
    details?.let{snapshot->
        AlertDialog(onDismissRequest={details=null},title={Text(dialogTitle)},text={
            SelectionContainer{Text(snapshot,Modifier.verticalScroll(rememberScrollState()).testTag("audio-status-detail"))}
        },confirmButton={TextButton(onClick={details=null}){Text("Close")}})
    }
}
