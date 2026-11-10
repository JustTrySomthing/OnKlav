package app.onklav.home

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.content.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.*
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged

/** State-based text input advertises rich IME support; plain Unicode emoji stay ordinary text. */
@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun MessageComposer(value:String,enabled:Boolean,onValue:(String)->Unit,onImage:(TransferableContent,Uri)->Unit,readOnly:Boolean=false){
 val state=rememberTextFieldState(value)
 val focus=remember{FocusRequester()};val keyboard=LocalSoftwareKeyboardController.current
 val change by rememberUpdatedState(onValue);val receive by rememberUpdatedState(onImage)
 LaunchedEffect(value){if(state.text.toString()!=value)state.edit{replace(0,length,value);placeCursorAtEnd()}}
 LaunchedEffect(state){snapshotFlow{state.text.toString()}.distinctUntilChanged().collect{change(it)}}
 BasicTextField(state=state,enabled=enabled,readOnly=readOnly,inputTransformation=InputTransformation.maxLength(10000),lineLimits=TextFieldLineLimits.MultiLine(maxHeightInLines=4),
  modifier=Modifier.focusRequester(focus).pointerInput(enabled,readOnly){awaitEachGesture{awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial);if(enabled&&!readOnly){focus.requestFocus();keyboard?.show()};waitForUpOrCancellation()}}.fillMaxWidth().heightIn(min=46.dp).padding(horizontal=10.dp,vertical=8.dp).testTag("message-composer").contentReceiver{content->
   content.consume{item->val uri=item.uri
    if(enabled&&!readOnly&&uri?.scheme=="content"&&content.clipMetadata.clipDescription.hasMimeType("image/*")){receive(content,uri);true}else false
   }
  },textStyle=LocalTextStyle.current.copy(fontSize=17.sp,color=MaterialTheme.colorScheme.onSurface),cursorBrush=SolidColor(MaterialTheme.colorScheme.primary),
  decorator={inner->Box{if(state.text.isEmpty())Text("Message",fontSize=17.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);inner()}})
}
