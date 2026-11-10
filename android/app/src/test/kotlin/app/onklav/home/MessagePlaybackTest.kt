package app.onklav.home

import org.junit.Assert.*
import org.junit.Test

class MessagePlaybackTest {
 @Test fun normalPlaybackNeverRequiresRateControl(){
  assertEquals(1f,messagePlaybackSpeed(1f,1f){error("Normal playback must not call a codec rate API")},0f)
 }
 @Test fun unsupportedRateKeepsTheActualSpeed(){
  val speed=messagePlaybackSpeed(1.5f,1f){throw IllegalArgumentException("Unsupported codec rate")}
  assertEquals(1f,speed,0f)
 }
 @Test fun supportedRateAndReturnToNormalApplyExplicitly(){
  val applied=mutableListOf<Float>()
  var speed=messagePlaybackSpeed(2f,1f){applied+=it}
  speed=messagePlaybackSpeed(1f,speed){applied+=it}
  assertEquals(listOf(2f,1f),applied);assertEquals(1f,speed,0f)
 }
 @Test fun invalidPlayerStateIsNotDisguisedAsAnUnsupportedRate(){
  assertThrows(IllegalStateException::class.java){messagePlaybackSpeed(1.5f,1f){throw IllegalStateException("Released player")}}
 }
}
