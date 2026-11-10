package app.onklav.home

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.style.TextDecoration

internal fun messageLinkText(text:String,color:Color,open:((String)->Unit)?):AnnotatedString=buildAnnotatedString {
 append(text)
 if(open!=null){val matcher=android.util.Patterns.WEB_URL.matcher(text)
 while(matcher.find()){
  val raw=matcher.group().trimEnd('.',',',';',':','!', '?', ')',']','}')
  val url=if(raw.startsWith("http://",true)||raw.startsWith("https://",true))raw else "https://$raw"
  val uri=android.net.Uri.parse(url)
  val target=runCatching{SdkWebPolicy.external(url)}.getOrNull()
  if(target!=null&&uri.scheme?.lowercase() in setOf("http","https")&&!uri.host.isNullOrBlank())addLink(LinkAnnotation.Clickable(target,TextLinkStyles(style=SpanStyle(color=color,textDecoration=TextDecoration.Underline))){open(target)},matcher.start(),matcher.start()+raw.length)
 }
 }
}
