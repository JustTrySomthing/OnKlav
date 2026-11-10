package app.onklav.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.*
import app.onklav.shared.R
import org.json.JSONObject
import kotlin.math.*

internal data class ReactionView(val messages:List<Message>,val emojis:Map<String,List<String>>,val tokens:Map<String,String>)
internal data class TextReaction(val emoji:String,val text:String,val remove:Boolean=false)
internal fun textReaction(body:String):TextReaction? {
 val phrases=linkedMapOf("Loved " to "❤️","Liked " to "👍","Laughed at " to "😂","Was surprised by " to "😮","Was saddened by " to "😢","Thanked you for " to "🙏")
 val known=phrases.entries.firstOrNull{body.startsWith(it.key)}
 val generic=if(known==null)Regex("^(Reacted (.+) to |Removed (.+) from )").find(body)else null
 val prefix=known?.key?:generic?.value?:return null
 val quote=body.removePrefix(prefix);if(quote.length<2||quote.first() !in "\"“"||quote.last() !in "\"”")return null
 return TextReaction(known?.value?:generic!!.groupValues[2].ifEmpty{generic.groupValues[3]},quote.substring(1,quote.length-1),generic?.groupValues?.get(3)?.isNotBlank()==true)
}
internal fun projectReactions(rows:List<Message>,state:JSONObject,group:JSONObject?=null):ReactionView {
 val actors=mutableMapOf<String,MutableMap<String,String>>();val events=mutableMapOf<String,MutableMap<String,String>>();val hidden=mutableSetOf<String>()
 if(group==null)for(row in rows.sortedBy{it.date}){
  val reaction=textReaction(row.text)?:continue
  val candidates=rows.filter{it.key!=row.key&&it.date<=row.date&&it.text==reaction.text&&textReaction(it.text)==null}
  val target=(candidates.filter{it.sent!=row.sent}.ifEmpty{candidates}).maxByOrNull{it.date}?:continue
  hidden+=row.key;val actor=if(row.sent)"you"else"peer";val values=actors.getOrPut(target.key){mutableMapOf()};val tokens=events.getOrPut(target.key){mutableMapOf()}
  if(reaction.remove){values.remove(actor);tokens.remove(actor)}else{values[actor]=reaction.emoji;tokens[actor]=if(row.sent)"own:${reaction.emoji}"else"${row.key}:${row.date}:${reaction.emoji}"}
 }
 for(row in rows){
  if(group!=null){val target=group.child("keys").optString(row.key);val values=group.child("reactions").child(target);for(actor in values.keysList()){val emoji=values.optString(actor);if(emoji.isNotBlank()){actors.getOrPut(row.key){mutableMapOf()}[actor]=emoji;events.getOrPut(row.key){mutableMapOf()}[actor]="$actor:$emoji"}}}
  val flags=state.messageFlags(row)
  if(flags.has("reaction")){val value=flags.optString("reaction");if(value.isBlank()){actors[row.key]?.remove("you");events[row.key]?.remove("you")}else{actors.getOrPut(row.key){mutableMapOf()}["you"]=value;events.getOrPut(row.key){mutableMapOf()}["you"]="own:$value"}}
 }
 return ReactionView(rows.filterNot{it.key in hidden},actors.mapValues{it.value.values.distinct()},events.mapValues{(key,value)->key+":"+value.toSortedMap().values.joinToString("|")})
}

@Composable internal fun SpeechActivityIcon(ink:androidx.compose.ui.graphics.Color,description:String="Reading Message. Tap to stop"){
 val rotation by rememberInfiniteTransition(label="message-speech").animateFloat(0f,360f,infiniteRepeatable(tween(950,easing=LinearEasing)),label="speech-rotation")
 // Draw in a fixed viewport with an explicit origin; density must not move the pivot.
 androidx.compose.foundation.Canvas(Modifier.size(24.dp).graphicsLayer{rotationZ=rotation}.semantics{contentDescription=description}) {
  val unit=size.minDimension/24f
  withTransform({scale(unit,unit,pivot=androidx.compose.ui.geometry.Offset.Zero)}) {
   drawArc(ink,140f,265f,false,androidx.compose.ui.geometry.Offset(4f,4f),androidx.compose.ui.geometry.Size(16f,16f),style=androidx.compose.ui.graphics.drawscope.Stroke(2.5f,cap=androidx.compose.ui.graphics.StrokeCap.Butt))
   val head=androidx.compose.ui.graphics.Path().apply{moveTo(14f,12.5f);lineTo(20f,20f);lineTo(14f,20f);close()}
   drawPath(head,ink)
  }
 }

}

internal fun reactionProgress(elapsed:Float,delay:Float,duration:Float)=((elapsed-delay)/duration).coerceIn(0f,1f)
internal fun reactionOpacity(p:Float)=when {p<=0f||p>=1f->0f;p<.17f->p/.17f;p>.76f->(1f-p)/.24f;else->1f}
@Composable internal fun BoxScope.ReactionDecoration(emojis:List<String>,token:String?,seen:MutableSet<String>){
 val elapsed=remember{Animatable(4600f)}
 val density=androidx.compose.ui.platform.LocalDensity.current.density
 LaunchedEffect(token){if(!token.isNullOrBlank()&&emojis.isNotEmpty()&&seen.add(token)){elapsed.snapTo(0f);elapsed.animateTo(4600f,tween(4600,easing=LinearEasing))}}
 if(emojis.isNotEmpty()){
  Surface(Modifier.align(Alignment.BottomEnd).offset(x=(-6).dp,y=12.dp),shape=CircleShape,color=MaterialTheme.colorScheme.surface.copy(alpha=.95f),shadowElevation=2.dp){Text(emojis.joinToString(" "),Modifier.padding(horizontal=10.dp,vertical=4.dp),fontSize=18.sp)}
  if(elapsed.value<4600f){
   val time=elapsed.value
   val p=reactionProgress(time,70f,4200f)
   Text(emojis.last(),Modifier.align(Alignment.BottomCenter).graphicsLayer{alpha=reactionOpacity(p);translationY=(40f-265*p)*density;translationX=sin(p*4*PI).toFloat()*5*density;scaleX=if(p<.17f).75f+.33f*p/.17f else 1.08f-.16f*((p-.17f)/.83f);scaleY=scaleX},fontSize=80.sp)
   val plan=remember(token){val random=kotlin.random.Random(token.hashCode());List(10){i->floatArrayOf(if(i%2==0)-1f else 1f,36+random.nextFloat()*65,17+random.nextFloat()*13,155+random.nextFloat()*105,140f+i*105,3000+random.nextFloat()*350,7+random.nextFloat()*15)}}
   plan.forEach { v->val t=reactionProgress(time,v[4],v[5]);Text(emojis.last(),Modifier.align(Alignment.BottomCenter).graphicsLayer{alpha=reactionOpacity(t)*(.74f-.15f*t);translationX=v[0]*(22+(v[1]+10)*t+sin(t*PI*2).toFloat()*12)*density;translationY=(52-v[3]*t)*density;rotationZ=v[0]*v[6]*(2*t-1);scaleX=if(t<.21f).45f+.5f*t/.21f else if(t>.62f)1-.34f*(t-.62f)/.38f else 1f;scaleY=scaleX},fontSize=v[2].sp)}
  }
 }
}

/** Media-only messages have no speech action; a real caption remains readable. */
internal fun messageSpeechText(message:Message):String {
 val text=message.text.trim()
 if(message.parts.isNotEmpty() && text.lowercase(java.util.Locale.ROOT).trim('[',']','(',')') in setOf("image","photo","video","audio","attachment","picture")) return ""
 return text
}
