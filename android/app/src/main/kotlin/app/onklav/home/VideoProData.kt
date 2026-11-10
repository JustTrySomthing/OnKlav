package app.onklav.home

import org.json.JSONObject
import kotlin.math.*

internal data class VideoRate(val at:Long,val factor:Float){fun json()=JSONObject().put("at",at).put("factor",factor);companion object{fun parse(j:JSONObject)=VideoRate(j.getLong("at"),j.getDouble("factor").toFloat())}}
internal fun videoRateAt(source:Long,speed:Float,rates:List<VideoRate>)=(speed*(rates.lastOrNull{it.at<=source}?.factor?:1f)).coerceIn(.25f,4f)
internal fun videoOutputDuration(start:Long,end:Long,speed:Float,rates:List<VideoRate>):Long{
    val points=listOf(start)+rates.map{it.at}.filter{it>start&&it<end}+end
    return points.zipWithNext().sumOf{(a,b)->((b-a)/videoRateAt(a,speed,rates).toDouble())}.roundToLong().coerceAtLeast(1)
}
internal fun videoSourceAt(c:VideoClip,output:Long):Long{
    var remaining=output.coerceAtLeast(0).toDouble();val points=listOf(c.start)+c.rates.map{it.at}.filter{it>c.start&&it<c.end}+c.end
    for((a,b)in points.zipWithNext()){val speed=videoRateAt(a,c.speed,c.rates);val length=(b-a)/speed.toDouble();if(remaining<=length)return(a+remaining*speed).roundToLong().coerceIn(c.start,c.end);remaining-=length};return c.end
}
internal fun videoSpeedCurve(c:VideoClip,preset:String):VideoClip{
    if(preset=="Normal")return c.copy(rates=emptyList())
    val rates=(0 until 32).map{i->val t=i/31f;val f=when(preset){"Speed up"->.5f+1.5f*t;"Slow down"->2f-1.5f*t;else->1.8f-1.4f*sin(PI*t).toFloat()};VideoRate(c.start+(c.end-c.start)*i/32,f)}
    return videoClampClip(c.copy(rates=rates))
}
internal data class VideoKeyframe(val at:Long,val zoom:Float=1f,val x:Float=0f,val y:Float=0f,val rotation:Float=0f,val opacity:Float=1f,val easing:String="Linear",val curveStart:Float=0f,val curveEnd:Float=1f,val cx1:Float=.25f,val cy1:Float=.1f,val cx2:Float=.25f,val cy2:Float=1f){
    fun json()=JSONObject().put("at",at).put("zoom",zoom).put("x",x).put("y",y).put("rotation",rotation).put("opacity",opacity).put("easing",easing).put("curveStart",curveStart).put("curveEnd",curveEnd).put("cx1",cx1).put("cy1",cy1).put("cx2",cx2).put("cy2",cy2)
    fun checked(duration:Long){require(listOf(cx1,cy1,cx2,cy2).all{it.isFinite()&&it in 0f..1f});require(easing in videoEasings&&curveStart.isFinite()&&curveEnd.isFinite()&&curveStart>=0f&&curveEnd<=1f&&curveStart<curveEnd);require(at in 0..duration&&zoom.isFinite()&&zoom in .1f..4f&&listOf(x,y).all{it.isFinite()&&it in -1f..1f}&&rotation.isFinite()&&rotation in -360f..360f&&opacity.isFinite()&&opacity in 0f..1f)}
    companion object{fun parse(j:JSONObject)=VideoKeyframe(j.getLong("at"),j.getDouble("zoom").toFloat(),j.getDouble("x").toFloat(),j.getDouble("y").toFloat(),j.getDouble("rotation").toFloat(),j.getDouble("opacity").toFloat(),j.optString("easing","Linear"),j.optDouble("curveStart",0.0).toFloat(),j.optDouble("curveEnd",1.0).toFloat(),j.optDouble("cx1",.25).toFloat(),j.optDouble("cy1",.1).toFloat(),j.optDouble("cx2",.25).toFloat(),j.optDouble("cy2",1.0).toFloat())}
}
internal fun videoPose(c:VideoClip,at:Long):VideoKeyframe{
    val t=at.coerceIn(0,c.duration);if(c.keyframes.isEmpty()){val f=t.toFloat()/c.duration;val z=if(c.motion)c.zoom+(c.endZoom-c.zoom)*f*f*(3-2*f)else c.zoom;return VideoKeyframe(t,z,c.panX,c.panY)}
    val a=c.keyframes.lastOrNull{it.at<=t}?:c.keyframes.first();val b=c.keyframes.firstOrNull{it.at>=t}?:c.keyframes.last();val raw=if(a.at==b.at)0f else(t-a.at).toFloat()/(b.at-a.at);val low=videoEase(a,a.curveStart);val high=videoEase(a,a.curveEnd);val f=if(high-low<.000001f)raw else (videoEase(a,a.curveStart+(a.curveEnd-a.curveStart)*raw)-low)/(high-low)
    fun mix(x:Float,y:Float)=x+(y-x)*f
    return VideoKeyframe(t,mix(a.zoom,b.zoom),mix(a.x,b.x),mix(a.y,b.y),mix(a.rotation,b.rotation),mix(a.opacity,b.opacity))
}
internal fun videoSetKey(c:VideoClip,key:VideoKeyframe):VideoClip{val prior=c.keyframes.firstOrNull{it.at==key.at};val value=if(prior!=null)key.copy(easing=prior.easing,curveStart=prior.curveStart,curveEnd=prior.curveEnd,cx1=prior.cx1,cy1=prior.cy1,cx2=prior.cx2,cy2=prior.cy2)else key;return c.copy(motion=false,keyframes=(c.keyframes.filter{it.at!=key.at}+value).sortedBy{it.at}).also{require(it.keyframes.size<=100){"Use up to 100 keyframes per clip"}}}
internal val videoEasings=listOf("Linear","Smooth","Ease in","Ease out","Hold","Custom")
internal fun videoEase(name:String,t:Float):Float=when(name){"Smooth"->t*t*(3-2*t);"Ease in"->t*t;"Ease out"->1-(1-t)*(1-t);"Hold"->if(t>=1f)1f else 0f;else->t}
/** Keep the original easing interval when cutting through a segment. */
internal fun videoSplitKeys(c:VideoClip,at:Long,left:Boolean):List<VideoKeyframe>{
    if(c.keyframes.isEmpty())return emptyList()
    val boundary=videoPose(c,at);val a=c.keyframes.lastOrNull{it.at<=at};val b=c.keyframes.firstOrNull{it.at>at}
    val crossing=a!=null&&b!=null&&a.at<at
    val cut=if(crossing)a!!.curveStart+(a.curveEnd-a.curveStart)*(at-a.at).toFloat()/(b!!.at-a.at)else 0f
    return if(left)c.keyframes.filter{it.at<at}.map{if(crossing&&it.at==a!!.at)it.copy(curveEnd=cut)else it}+boundary
    else listOf(if(crossing)boundary.copy(at=0,easing=a!!.easing,curveStart=cut,curveEnd=a.curveEnd,cx1=a.cx1,cy1=a.cy1,cx2=a.cx2,cy2=a.cy2)else (a?.takeIf{it.at==at}?:boundary).copy(at=0))+c.keyframes.filter{it.at>at}.map{it.copy(at=it.at-at)}
}
internal data class VideoLayer(val id:String=videoId(),val clip:VideoClip,val offset:Long=0,val x:Float=.5f,val y:Float=.5f,val scale:Float=.35f,val rotation:Float=0f,val opacity:Float=1f,val chroma:Boolean=false,val keyColor:Int=0xFF00FF00.toInt(),val keyTolerance:Float=.35f,val mask:Int=0,val feather:Float=.03f){
    fun json()=JSONObject().put("id",id).put("clip",clip.json()).put("offset",offset).put("x",x).put("y",y).put("scale",scale).put("rotation",rotation).put("opacity",opacity).put("chroma",chroma).put("keyColor",keyColor).put("keyTolerance",keyTolerance).put("mask",mask).put("feather",feather)
    companion object{fun parse(j:JSONObject)=VideoLayer(j.getString("id"),VideoClip.parse(j.getJSONObject("clip")),j.getLong("offset"),j.getDouble("x").toFloat(),j.getDouble("y").toFloat(),j.getDouble("scale").toFloat(),j.getDouble("rotation").toFloat(),j.getDouble("opacity").toFloat(),j.optBoolean("chroma"),j.optInt("keyColor",0xFF00FF00.toInt()),j.optDouble("keyTolerance",.35).toFloat(),j.optInt("mask"),j.optDouble("feather",.03).toFloat()).also{require(it.keyTolerance.isFinite()&&it.keyTolerance in .05f.. .8f&&it.mask in 0..2&&it.feather.isFinite()&&it.feather in 0f.. .2f);require(it.offset>=0&&it.scale.isFinite()&&it.scale in .1f..1f&&listOf(it.x,it.y).all{v->v.isFinite()&&v in -1f..1f}&&it.rotation.isFinite()&&it.rotation in -360f..360f&&it.opacity.isFinite()&&it.opacity in 0f..1f)}}
}
internal data class VideoCaption(val start:Long,val end:Long,val text:String)
internal fun videoParseCaptions(text:String):List<VideoCaption>{
    require(text.length<=2_000_000){"Choose a subtitle file smaller than 2 MB"}
    fun time(s:String):Long{val parts=s.trim().replace(',','.').split(':');require(parts.size in 2..3);val seconds=parts.last().toDouble();val minutes=parts[parts.size-2].toLong();val hours=if(parts.size==3)parts[0].toLong()else 0L;require(seconds.isFinite()&&seconds>=0&&seconds<60&&minutes in 0..59&&hours in 0..12);return((hours*3600+minutes*60)*1000+seconds*1000).roundToLong()}
    val result=mutableListOf<VideoCaption>();val lines=text.removePrefix("\uFEFF").replace("\r","").lines();var i=0
    while(i<lines.size){val line=lines[i++];if("-->" !in line)continue;val span=line.split("-->");require(span.size==2){"Invalid subtitle timing"};val a=time(span[0]);val b=time(span[1].trim().substringBefore(' '));val body=mutableListOf<String>();while(i<lines.size&&lines[i].isNotBlank())body+=lines[i++];val value=body.joinToString("\n").replace(Regex("<[^>]*>"),"").trim();require(a<b&&b<=43_200_000&&value.length in 1..1000){"Invalid subtitle cue"};result+=VideoCaption(a,b,value);require(result.size<=5000){"Use up to 5,000 subtitle cues"}}
    require(result.isNotEmpty()){ "No timed SRT or WebVTT captions found" };return result.sortedBy{it.start}
}
internal fun videoAddCaptions(p:VideoProject,cues:List<VideoCaption>):VideoProject{var offset=0L;val clips=p.clips.map{c->val start=p.clipStart(p.clips.indexOf(c));offset=if(p.clips.indexOf(c)<p.clips.lastIndex)p.clipStart(p.clips.indexOf(c)+1)else p.duration;val added=cues.mapNotNull{q->val a=max(start,q.start);val b=min(offset,q.end);if(a<b)VideoTitle(text=q.text,start=a-start,end=b-start,y=-.7f,caption=true)else null};require(c.titles.size+added.size<=200){"Use up to 200 captions/text layers per clip"};c.copy(titles=c.titles+added)};require(clips.sumOf{it.titles.size}<=5000);require(clips!=p.clips){"The captions fall outside this project"};return p.copy(clips=clips)}
internal fun videoCaptionsSrt(p:VideoProject):String{var offset=0L;val cues=p.clips.flatMap{c->val start=p.clipStart(p.clips.indexOf(c));c.titles.filter{it.caption}.map{VideoCaption(start+it.start,start+it.end,it.text)}}.sortedBy{it.start};require(cues.isNotEmpty()){ "There are no captions to export" }
    fun time(ms:Long)="%02d:%02d:%02d,%03d".format(java.util.Locale.ROOT,ms/3_600_000,ms/60_000%60,ms/1000%60,ms%1000)
    return cues.mapIndexed{i,q->"${i+1}\n${time(q.start)} --> ${time(q.end)}\n${q.text}\n"}.joinToString("\n")
}
internal fun videoClampProject(p:VideoProject):VideoProject=p.copy(beats=p.beats.filter{it<=p.duration},layers=p.layers.mapNotNull{layer->val remaining=p.duration-layer.offset;if(remaining<100)null else{val end=videoSourceAt(layer.clip,remaining);if(end-layer.clip.start<100)null else layer.copy(clip=videoClampClip(layer.clip.copy(end=end)))}})

internal val videoTransitions=listOf("None","Crossfade","Wipe left","Wipe right","Wipe up","Wipe down")
internal fun videoOverlap(clips:List<VideoClip>,index:Int):Long{if(index<=0||index>=clips.size)return 0;val c=clips[index];return if(c.transition=="None")0 else min(c.transitionMs,min(clips[index-1].duration/2,c.duration/2))}
internal fun videoEase(k:VideoKeyframe,t:Float):Float{
    if(k.easing!="Custom")return videoEase(k.easing,t)
    fun bezier(s:Float,a:Float,b:Float):Float{val u=1-s;return 3*u*u*s*a+3*u*s*s*b+s*s*s}
    var low=0f;var high=1f;repeat(22){val mid=(low+high)*.5f;if(bezier(mid,k.cx1,k.cx2)<t)low=mid else high=mid};return bezier((low+high)*.5f,k.cy1,k.cy2)
}
