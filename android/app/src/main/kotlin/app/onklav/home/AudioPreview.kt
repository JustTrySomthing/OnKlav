package app.onklav.home

import kotlin.math.*

/** A bounded audition in edited timeline coordinates. Export always uses the full edit. */
internal data class AudioPreviewPlan(val edit:AudioEdit,val start:Long,val end:Long,val total:Long,val context:AudioEditEngine.Context){
 val startFraction get()=start.toFloat()/total
 val endFraction get()=end.toFloat()/total
 fun fraction(positionMs:Long,durationMs:Long)=startFraction+(endFraction-startFraction)*(positionMs.toFloat()/durationMs.coerceAtLeast(1)).coerceIn(0f,1f)
 companion object{
  const val SECONDS=12
  fun make(source:AudioSource,edit:AudioEdit,selection:ClosedFloatingPointRange<Float>,cursor:Float,original:Boolean):AudioPreviewPlan{
   val sound=if(original)AudioEdit(edit.regions,reverse=edit.reverse,limiter=false)else edit
   val total=sound.frames;require(total>0)
   val a=(selection.start*total).toLong().coerceIn(0,total-1);val b=(selection.endInclusive*total).toLong().coerceIn(a+1,total)
   val at=(cursor*total).toLong();val start=if(at in a until b)at else a
   val end=min(b,start+max(1L,(source.rate*SECONDS.toDouble()*sound.tempo).toLong()))
   val cropped=if(sound.reverse)sound.trim(total-end,total-start)else sound.trim(start,end)
   return AudioPreviewPlan(cropped,start,end,total,AudioEditEngine.Context((start/sound.tempo).toLong(),(total/sound.tempo).toLong()))
  }
 }
}
