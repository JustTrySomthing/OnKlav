package app.onklav.home
import org.junit.Test
import org.junit.Assert.*
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk=[29])
class CarrierMediaPlanTest {
 @Test fun messageBudgetCapsCarrierLimitAndAccountsForEarlierAttachments(){
  assertEquals(1_000_000,CarrierMedia.messageLimit(10_000_000))
  assertEquals(600_000,CarrierMedia.messageLimit(600_000))
  assertEquals(20_000,CarrierMedia.messageLimit(20_000))
  assertEquals(300_000,CarrierMedia.messageLimit(null))
  assertEquals(300_000,CarrierMedia.messageLimit(0))
  val parts=org.json.JSONArray().put(org.json.JSONObject().put("bytes",200_000))
  assertEquals(82_590,CarrierMedia.attachmentBudget(300_000,"é",parts))
  assertEquals(0,CarrierMedia.attachmentBudget(200_000,"",parts))
  assertEquals(99_488,CarrierMedia.batchBudget(200_000,2))
 }
 @Test fun trimRecommendationsHonorAudioVideoAndActualCarrierBudgets(){
  for(budget in listOf(30000,280000,1000000))for(video in listOf(false,true)){
   val duration=CarrierMedia.recommended(budget,video,true)
   assertTrue(duration>=0);assertTrue(duration<=if(video)60000 else 180000)
   if(duration>=500)CarrierMedia.validate(1000,1000+duration,duration+2000,budget,video,true)
   assertThrows(IllegalArgumentException::class.java){CarrierMedia.validate(0,duration+1000,duration+2000,budget,video,true)}
  }
 }
 @Test fun invalidRangesAndUnavailableBudgetsCannotProduceAClip(){
  assertThrows(IllegalArgumentException::class.java){CarrierMedia.validate(-1,1000,2000,280000,true,true)}
  assertThrows(IllegalArgumentException::class.java){CarrierMedia.validate(0,3000,2000,280000,true,true)}
  assertThrows(IllegalArgumentException::class.java){CarrierMedia.validate(0,100,2000,280000,false,true)}
  assertEquals(0,CarrierMedia.recommended(10,true,true))
 }
 @Test fun floatingReactionMotionFadesInAndOutWithoutChangingItsMessage(){
  assertEquals(0f,reactionProgress(0f,140f,3000f));assertEquals(1f,reactionProgress(4600f,140f,3000f))
  assertEquals(0f,reactionOpacity(0f));assertEquals(0f,reactionOpacity(1f));assertEquals(1f,reactionOpacity(.5f))
 }
}
