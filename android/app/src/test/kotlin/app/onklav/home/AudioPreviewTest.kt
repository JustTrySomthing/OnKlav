package app.onklav.home

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class AudioPreviewTest{
 private fun source(d:File,seconds:Int,rate:Int=8000,channels:Int=1,sample:(Int)->Float):AudioSource{
  val f=File(d,"source.pcm");AudioPcmWriter(f).use{w->repeat(seconds*rate){i->repeat(channels){ch->w.sample(sample(i)*(if(ch==0)1f else .5f))}}};return AudioSource(f,rate,channels,(seconds*rate).toLong(),"Audio.wav")
 }
 private fun wave(f:File):FloatArray{val b=ByteBuffer.wrap(f.readBytes()).order(ByteOrder.LITTLE_ENDIAN);b.position(44);return FloatArray(b.remaining()/2){b.short/32768f}}
 @Test fun previewIsBoundedStartsAtPlayheadAndRespectsSelectionAndTempo(){
  val s=AudioSource(File("unused.pcm"),48000,2,48000L*3600,"Hour.wav");val e=AudioEdit(listOf(AudioRegion(0,s.frames)),tempo=1.5f)
  val p=AudioPreviewPlan.make(s,e,.4f.. .6f,.5f,false)
  assertEquals(s.frames/2,p.start);assertTrue(p.edit.frames/e.tempo<=48000*12+1);assertTrue(p.end<=s.frames*.6);assertEquals(.5f,p.fraction(0,12000),.0001f);assertEquals(p.endFraction,p.fraction(12000,12000),.0001f)
  val outside=AudioPreviewPlan.make(s,e,.4f.. .6f,.8f,false);assertEquals((.4f*s.frames).toLong(),outside.start)
  val original=AudioPreviewPlan.make(s,e,.4f.. .6f,.5f,true);assertEquals(1f,original.edit.tempo);assertEquals(0f,original.edit.pitch);assertFalse(original.edit.limiter)
 }
 @Test fun midTrackPreviewDoesNotRestartBeginningOrEndFades(){val d=createTempDir();try{
  val s=source(d,60){.2f};val e=AudioEdit(listOf(AudioRegion(0,s.frames)),fadeIn=10f,fadeOut=10f);val p=AudioPreviewPlan.make(s,e,0f..1f,1f/3,false);val out=File(d,"preview.wav")
  AudioEditEngine.render(s,p.edit,out,File(d,"work"),null,context=p.context);val samples=wave(out);assertEquals(.2f,samples.first(),.0001f);assertEquals(.2f,samples.last(),.0001f);assertEquals(8000*12,samples.size)
 }finally{d.deleteRecursively()}}
 @Test fun reversedPreviewUsesTheSameRegionOrderAsTheWaveform(){val d=createTempDir();try{
  val s=source(d,30){i->if(i<80000).1f else if(i<160000).2f else .3f};val e=AudioEdit(listOf(AudioRegion(0,s.frames)),reverse=true);val p=AudioPreviewPlan.make(s,e,0f..1f,.1f,false);val out=File(d,"reverse.wav")
  AudioEditEngine.render(s,p.edit,out,File(d,"work"),null,context=p.context);val a=wave(out);assertEquals(.3f,a.first(),.0001f);assertEquals(.2f,a.last(),.0001f);assertEquals(.3f,AudioEditEngine.peaks(s,e,30)[0],.0001f)
 }finally{d.deleteRecursively()}}
 @Test fun interpolationAcrossBlockBoundariesDoesNotRepeatedlyReadTheDisk(){val d=createTempDir();try{
  val s=source(d,5){i->i/50000f};AudioPcmReader(s.file,1).use{r->repeat(10000){assertEquals(16383/50000f,r.get(16383,0),.0001f);assertEquals(16384/50000f,r.get(16384,0),.0001f)};assertEquals(2,r.blockLoads)}
 }finally{d.deleteRecursively()}}
 @Test(timeout=30000) fun longEditedProjectAuditionsTwelveSecondsWithIndependentPitchAndTempo(){val d=createTempDir();try{
  val rate=48000;val s=source(d,20,rate,2){i->(.2*sin(2*PI*320*i/rate)).toFloat()};val e=AudioEdit(List(360){AudioRegion(0,s.frames)},pitch=7f,tempo=1.4f);val p=AudioPreviewPlan.make(s,e,0f..1f,.8f,false);val out=File(d,"audition.wav")
  val before=System.nanoTime();val result=AudioEditEngine.render(s,p.edit,out,File(d,"work"),null,context=p.context);val elapsed=(System.nanoTime()-before)/1e9
  assertTrue(result.frames<=rate*12+2);assertTrue(out.length()<rate*12*4+100);val a=wave(out);var crossings=0;val start=rate*4;val end=rate*8;for(i in start+1 until end)if(a[(i-1)*2]<0&&a[i*2]>=0)crossings++;val hz=crossings*rate.toDouble()/(end-start);assertEquals(320*2.0.pow(7.0/12),hz,15.0)
  for(i in start until end step 500)assertEquals(a[i*2]*.5f,a[i*2+1],.0001f)
  val report=File(System.getProperty("onklav.project.root"),"docs/evidence/audio-editor-refresh/preview-timing.json");report.parentFile.mkdirs();report.writeText("{\"project_seconds\":7200,\"preview_frames\":${result.frames},\"rate\":48000,\"channels\":2,\"host_render_seconds\":$elapsed,\"phone_timing_verified\":false}")
 }finally{d.deleteRecursively()}}
 @Test fun originalComparisonDoesNotApplyTheDefaultLimiter(){val d=createTempDir();try{
  val s=source(d,1){.999f};val e=AudioEdit(listOf(AudioRegion(0,s.frames)),gain=12f,limiter=true)
  val plan=AudioPreviewPlan.make(s,e,0f..1f,0f,true);val out=File(d,"dry.wav")
  AudioEditEngine.render(s,plan.edit,out,File(d,"work"),null,context=plan.context)
  assertEquals(.999f,wave(out)[100],.0001f)
 }finally{d.deleteRecursively()}}

}
