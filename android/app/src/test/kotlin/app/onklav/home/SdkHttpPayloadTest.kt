package app.onklav.home
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class SdkHttpPayloadTest{
 @Test fun binaryPreservesNonUtf8BytesAndHasNoTextAlias(){val r=SdkHttpPayload.result(200,byteArrayOf(0,-1,-128,10),JSONObject(),true);assertEquals("00ff800a",r.getString("hex"));assertFalse(r.has("text"));assertEquals("",SdkHttpPayload.result(304,byteArrayOf(),JSONObject(),true).getString("hex"))}
 @Test fun textRetainsStrictUtf8AndExistingByteBounds(){assertEquals("café",SdkHttpPayload.result(200,"café".toByteArray(),JSONObject(),false).getString("text"));assertThrows(Exception::class.java){SdkHttpPayload.result(200,byteArrayOf(-1),JSONObject(),false)};assertThrows(Exception::class.java){SdkHttpPayload.result(200,byteArrayOf(0),JSONObject(),false)};assertThrows(Exception::class.java){SdkHttpPayload.result(200,ByteArray(131073),JSONObject(),false)}}
 @Test fun binaryRejectsOversizeBeforeHexExpansion(){assertEquals(524288,SdkHttpPayload.result(200,ByteArray(262144),JSONObject(),true).getString("hex").length);assertThrows(Exception::class.java){SdkHttpPayload.result(200,ByteArray(262145),JSONObject(),true)};assertThrows(Exception::class.java){SdkHttpPayload.result(999,byteArrayOf(),JSONObject(),true)}}
}
