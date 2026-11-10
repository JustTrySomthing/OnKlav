package app.onklav.home
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
/** Shared provider adapter; usable by any granted SDK app, never selected by app ID. */
internal object SdkVideoPolicy {
    val hosts=setOf("youtube.com","www.youtube.com","m.youtube.com","music.youtube.com","consent.youtube.com","youtu.be")
    private fun parsed(raw:String)=raw.takeIf { it.length<=2048 && it.none { c->c.isWhitespace()||c.isISOControl()||c=='\\' } }?.toHttpUrlOrNull()?.takeIf { it.scheme=="https" && it.port==443 && it.username.isEmpty() && it.password.isEmpty() }
    fun page(raw:String)=parsed(raw)?.host in hosts
    fun account(raw:String):Boolean { val u=parsed(raw) ?: return false;val p="/"+u.pathSegments.joinToString("/");return u.host=="accounts.google.com" || ((u.host=="youtube.com"||u.host.endsWith(".youtube.com")) && (p=="/signin"||p.startsWith("/signin/")||p=="/account"||p.startsWith("/account_")||p=="/ServiceLogin")) }
    fun video(raw:String):String { val u=parsed(raw)?.takeIf { page(raw)&&!account(raw) } ?: return "";val parts=u.pathSegments;val id=when { u.host=="youtu.be"->parts.firstOrNull();u.encodedPath=="/watch"->u.queryParameter("v");parts.size>=2&&parts[0] in setOf("shorts","live","embed")->parts[1];else->null };return id?.takeIf { Regex("[A-Za-z0-9_-]{11}").matches(it) }.orEmpty() }
    fun blocked(raw:String):Boolean { val u=parsed(raw) ?: return false;return setOf("doubleclick.net","googlesyndication.com","googleadservices.com").any { u.host==it || u.host.endsWith(".$it") } || (u.host in setOf("www.google.com","www.youtube.com","m.youtube.com") && u.encodedPath.startsWith("/pagead/")) }
    fun sameDocument(a:String,b:String):Boolean { val av=video(a);val bv=video(b);return if(av.isNotEmpty()||bv.isNotEmpty()) av.isNotEmpty()&&av==bv else parsed(a)==parsed(b) }
}
