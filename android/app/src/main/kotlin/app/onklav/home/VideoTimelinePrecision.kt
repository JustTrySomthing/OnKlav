package app.onklav.home

import kotlin.math.abs
import kotlin.math.roundToLong

internal fun videoPreciseTime(ms:Long):String{val t=ms.coerceAtLeast(0);return "%d:%02d.%03d".format(t/60000,t/1000%60,t%1000)}

/** Integer frame indices avoid accumulating rounded millisecond drift at 24/30/60 fps. */
internal fun videoStepFrame(at:Long,direction:Int,fps:Int,duration:Long):Long {
    val frame=(at.coerceIn(0,duration)*fps/1000.0).roundToLong()+direction.coerceIn(-1,1)
    return (frame.coerceAtLeast(0)*1000.0/fps).roundToLong().coerceIn(0,duration)
}
internal fun videoTimelineSnap(p:VideoProject,at:Long,tolerance:Long,excludeClip:String?=null):Long {
    val duration=p.duration;val value=at.coerceIn(0,duration);val limit=tolerance.coerceIn(0,250)
    var best=value;var distance=limit+1
    fun consider(anchor:Long){if(anchor in 0..duration){val delta=abs(anchor-value);if(delta<=limit&&delta<distance){best=anchor;distance=delta}}}
    consider(0);consider(duration)
    var start=0L
    p.clips.forEachIndexed { i,c->
        val length=c.duration
        if(c.id!=excludeClip){consider(start);consider(start+length);c.titles.forEach{consider(start+it.start);consider(start+it.end)}}
        start+=length-videoOverlap(p.clips,i+1)
    }
    p.layers.forEach{consider(it.offset);consider(it.offset+it.clip.duration)}
    p.music.forEach{consider(it.offset);consider(it.offset+it.duration)}
    p.beats.forEach(::consider)
    return best
}
internal fun videoPositionMs(text:String,duration:Long):Long? {
    val seconds=text.trim().replace(',','.').toDoubleOrNull()?:return null
    if(!seconds.isFinite()||seconds<0||seconds>duration/1000.0)return null
    return (seconds*1000).roundToLong().takeIf{it in 0..duration}
}
