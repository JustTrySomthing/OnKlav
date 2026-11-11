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

internal data class ReactionMark(val emoji: String, val token: String)
internal data class ReactionView(val messages: List<Message>, val marks: Map<String, Map<String, ReactionMark>>) {
    val emojis: Map<String, List<String>>
        get() = marks.mapValues { (_, actors) -> actors.values.map { it.emoji }.distinct() }
}

internal data class TextReaction(val emoji: String, val text: String, val remove: Boolean = false)
private data class Tapback(val emoji: String, val add: String, val remove: String)

private val tapbacks = listOf(
    Tapback("❤️", "Loved", "Removed a heart from"),
    Tapback("👍", "Liked", "Removed a like from"),
    Tapback("👎", "Disliked", "Removed a dislike from"),
    Tapback("😂", "Laughed at", "Removed a laugh from"),
    Tapback("‼️", "Emphasized", "Removed an exclamation from"),
    Tapback("❓", "Questioned", "Removed a question mark from"),
    Tapback("😮", "Was surprised by", "Removed 😮 from"),
    Tapback("😢", "Was saddened by", "Removed 😢 from"),
    Tapback("🙏", "Thanked you for", "Removed 🙏 from"),
)
private val genericReaction = Regex("^(?:Reacted (?:with )?(.+?) to|Removed (.+?) from) (.+)$", RegexOption.DOT_MATCHES_ALL)
private val emojiReaction = Regex("^(.+?) to (.+)$", RegexOption.DOT_MATCHES_ALL)
private val reactionFailureStatuses = setOf("Failed", "Delivery failed", "Status unknown")
private val simpleEmojiCodePoints = setOf(0xa9, 0xae, 0x23, 0x2a)

internal fun reactionSms(emoji: String, target: String, remove: Boolean): String {
    val standard = tapbacks.firstOrNull { it.emoji == emoji }
    val phrase = if (remove) standard?.remove ?: "Removed $emoji from"
        else standard?.add ?: "Reacted $emoji to"
    return "$phrase \"$target\""
}

private fun unquoteReaction(quote: String): String? {
    if (quote.length < 2) return null
    val paired = (quote.first() == '"' && quote.last() == '"') ||
        (quote.first() == '“' && quote.last() == '”')
    return if (paired) quote.substring(1, quote.length - 1) else null
}

private fun reactionEmoji(value: String): Boolean =
    value.isNotBlank() && value.codePointCount(0, value.length) <= 25 &&
        value.codePoints().allMatch { cp ->
            cp in 0x1f000..0x1faff || cp in 0x2000..0x3300 && !Character.isWhitespace(cp) ||
                cp in 0xfe0e..0xfe0f || cp in 0xe0020..0xe007f ||
                cp in 0x30..0x39 || cp in simpleEmojiCodePoints
        } && value.codePoints().anyMatch { it > 0x7f }

internal fun textReaction(body: String): TextReaction? {
    val text = body.trim()
    for (entry in tapbacks) {
        if (text.startsWith("${entry.add} ")) {
            return unquoteReaction(text.removePrefix("${entry.add} "))?.let { TextReaction(entry.emoji, it) }
        }
        if (text.startsWith("${entry.remove} ")) {
            return unquoteReaction(text.removePrefix("${entry.remove} "))?.let { TextReaction(entry.emoji, it, true) }
        }
    }
    val generic = genericReaction.matchEntire(text)
    if (generic != null) {
        val removed = generic.groupValues[2]
        val emoji = generic.groupValues[1].ifEmpty { removed }
        val quote = unquoteReaction(generic.groupValues[3]) ?: return null
        return if (reactionEmoji(emoji)) TextReaction(emoji, quote, removed.isNotEmpty()) else null
    }
    val short = emojiReaction.matchEntire(text) ?: return null
    val emoji = short.groupValues[1]
    return if (reactionEmoji(emoji)) unquoteReaction(short.groupValues[2])?.let { TextReaction(emoji, it) } else null
}

private fun reactionText(text: String) = text.replace("\r\n", "\n").replace('\r', '\n').trim()

internal fun projectReactions(rows: List<Message>, state: JSONObject, group: JSONObject? = null): ReactionView {
    val marks = mutableMapOf<String, MutableMap<String, ReactionMark>>()
    val hidden = mutableSetOf<String>()
    val parsed = rows.filter { it.parts.isEmpty() }.associate { it.key to textReaction(it.text) }
    val originals = rows.filter { parsed[it.key] == null }.groupBy { reactionText(it.text) }
    val groupKeys = group?.child("keys")
    val groupReactions = group?.child("reactions")

    for (row in rows) {
        val target = groupKeys?.optString(row.key) ?: continue
        val reactions = groupReactions?.child(target) ?: continue
        for (actor in reactions.keysList()) {
            val emoji = reactions.optString(actor)
            val key = if (actor == "You") "you" else phoneKey(actor)
            if (emoji.isNotBlank() && key.isNotBlank()) {
                marks.getOrPut(row.key) { linkedMapOf() }[key] = ReactionMark(emoji, emoji)
            }
        }
    }

    for (row in rows.sortedWith(compareBy<Message> { it.date }.thenBy { it.id })) {
        val reaction = parsed[row.key] ?: continue
        if (row.pending || row.status in reactionFailureStatuses) continue
        val actor = if (row.sent) "you" else if (group == null) "peer" else phoneKey(row.sender)
        if (actor.isBlank()) continue
        val quote = reactionText(reaction.text)
        val candidates = originals[quote].orEmpty().filter {
            it.date < row.date || it.date == row.date && it.id < row.id
        }
        val preferred = candidates.filter { it.sent != row.sent }
        val targets = if (group != null && !row.sent) {
            preferred.ifEmpty { candidates.filter { phoneKey(it.sender) != actor } }
        } else preferred.ifEmpty { candidates }
        if (quote.isEmpty() && (targets.size != 1 || targets.single().parts.isEmpty())) continue
        val target = targets.maxWithOrNull(compareBy<Message> { it.date }.thenBy { it.id }) ?: continue
        hidden += row.key
        val actors = marks.getOrPut(target.key) { linkedMapOf() }
        if (reaction.remove) actors.remove(actor)
        else {
            actors.remove(actor)
            actors[actor] = ReactionMark(reaction.emoji, if (row.sent) reaction.emoji else row.key)
        }
    }

    for (row in rows) {
        val flags = state.messageFlags(row)
        if (!flags.has("reaction")) continue
        val emoji = flags.optString("reaction")
        val actors = marks.getOrPut(row.key) { linkedMapOf() }
        if (emoji.isBlank()) actors.remove("you")
        else if (actors["you"]?.emoji != emoji) actors["you"] = ReactionMark(emoji, emoji)
    }
    return ReactionView(rows.filterNot { it.key in hidden }, marks.filterValues { it.isNotEmpty() })
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
@Composable internal fun BoxScope.ReactionDecoration(
 messageKey: String,
 marks: Map<String, ReactionMark>,
 seen: MutableMap<String, Map<String, ReactionMark>>,
){
 val emojis = marks.values.map { it.emoji }.distinct()
 var active by remember { mutableStateOf<ReactionMark?>(null) }
 val elapsed=remember{Animatable(4600f)}
 val density=androidx.compose.ui.platform.LocalDensity.current.density
 LaunchedEffect(marks) {
  val previous = seen[messageKey].orEmpty()
  val incoming = marks.entries.lastOrNull { (actor, mark) -> previous[actor] != mark }?.value
  if (marks.isEmpty()) seen.remove(messageKey) else seen[messageKey] = marks
  if (incoming != null) {
   active = incoming
   elapsed.snapTo(0f)
   elapsed.animateTo(4600f, tween(4600, easing = LinearEasing))
  }
  active = null
 }
 if(emojis.isNotEmpty()){
  Surface(Modifier.align(Alignment.BottomEnd).offset(x=(-6).dp,y=12.dp),shape=CircleShape,color=MaterialTheme.colorScheme.surface.copy(alpha=.95f),shadowElevation=2.dp){Text(emojis.joinToString(" "),Modifier.padding(horizontal=10.dp,vertical=4.dp),fontSize=18.sp)}
  val animation = active
  if(animation != null && elapsed.value<4600f){
   val time=elapsed.value
   val p=reactionProgress(time,70f,4200f)
   Text(animation.emoji,Modifier.align(Alignment.BottomCenter).graphicsLayer{alpha=reactionOpacity(p);translationY=(40f-265*p)*density;translationX=sin(p*4*PI).toFloat()*5*density;scaleX=if(p<.17f).75f+.33f*p/.17f else 1.08f-.16f*((p-.17f)/.83f);scaleY=scaleX},fontSize=80.sp)
   val plan=remember(animation){val random=kotlin.random.Random(animation.token.hashCode());List(10){i->floatArrayOf(if(i%2==0)-1f else 1f,36+random.nextFloat()*65,17+random.nextFloat()*13,155+random.nextFloat()*105,140f+i*105,3000+random.nextFloat()*350,7+random.nextFloat()*15)}}
   plan.forEach { v->val t=reactionProgress(time,v[4],v[5]);Text(animation.emoji,Modifier.align(Alignment.BottomCenter).graphicsLayer{alpha=reactionOpacity(t)*(.74f-.15f*t);translationX=v[0]*(22+(v[1]+10)*t+sin(t*PI*2).toFloat()*12)*density;translationY=(52-v[3]*t)*density;rotationZ=v[0]*v[6]*(2*t-1);scaleX=if(t<.21f).45f+.5f*t/.21f else if(t>.62f)1-.34f*(t-.62f)/.38f else 1f;scaleY=scaleX},fontSize=v[2].sp)}
  }
 }
}

/** Media-only messages have no speech action; a real caption remains readable. */
internal fun messageSpeechText(message:Message):String {
 val text=message.text.trim()
 if(message.parts.isNotEmpty() && text.lowercase(java.util.Locale.ROOT).trim('[',']','(',')') in setOf("image","photo","video","audio","attachment","picture")) return ""
 return text
}
