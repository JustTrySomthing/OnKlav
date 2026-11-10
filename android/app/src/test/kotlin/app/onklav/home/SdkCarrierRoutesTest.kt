package app.onklav.home
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class SdkCarrierRoutesTest{
 private fun owner(id:String="org.example.one",pub:String="a".repeat(64))=JSONObject().put("id",id).put("publisher",pub)
 @Test fun appAndPublisherAddressesSeparateIdenticalSessionPayloads(){
  val a=owner();val b=owner("org.example.two");val other=owner(pub="b".repeat(64));val wire="!OKW1|P|"+"1".repeat(32)+"|0|wt1"
  assertEquals("c922ba908f8c64644437114b84c5045c",SdkCarrierRoutes.route(a))
  assertNotEquals(SdkCarrierRoutes.route(a),SdkCarrierRoutes.route(b));assertNotEquals(SdkCarrierRoutes.route(a),SdkCarrierRoutes.route(other))
  val encoded=SdkCarrierRoutes.encode(a,wire);val decoded=SdkCarrierRoutes.decode(encoded,listOf(a,b))!!;assertEquals(a,decoded.first);assertEquals(wire,decoded.second)
  assertNull(SdkCarrierRoutes.decode(encoded,listOf(b,other)));assertNull(SdkCarrierRoutes.decode(encoded,listOf(a,a)))
 }
 @Test fun legacyMessagesOnlyAddressTheBuiltInAppAndVoiceMetadataRoundTrips(){
  val a=owner();val builtin=owner("app.onklav.walkie");assertNull(SdkCarrierRoutes.decode("!OKW1|data",listOf(a)))
  assertEquals(builtin,SdkCarrierRoutes.decode("!OKW1|data",listOf(a,builtin))!!.first)
  val voice="!OKV1|"+"1".repeat(32)+"|"+"2".repeat(32)+"|1|audio/amr|"+"a".repeat(64)
  assertEquals(voice,SdkCarrierRoutes.decode(SdkCarrierRoutes.encode(a,voice),listOf(a))!!.second)
  assertNull(SdkCarrierRoutes.decode("!OKW2|../escape|payload",listOf(a)))
 }
 @Test fun oversizedLegacyAndRoutedMetadataAreRejectedBeforeOwnerRouting(){
  val builtin=owner("app.onklav.walkie")
  assertNull(SdkCarrierRoutes.decode("!OKW1|"+"x".repeat(421),listOf(builtin)))
  assertNull(SdkCarrierRoutes.decode("!OKV1|"+"x".repeat(421),listOf(builtin)))
  assertNull(SdkCarrierRoutes.decode("!OKW2|"+"x".repeat(421),listOf(owner())))
 }

}
