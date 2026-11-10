package app.onklav.home
import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SdkMapTest{
 private val c=ApplicationProvider.getApplicationContext<Context>()
 @Test fun mercatorProjectionWrapsDatelineClampsPolesAndRejectsNonFinite(){assertEquals(1.0,SdkMapTiles.project(0.0,0.0,1).first,1e-9);assertEquals(1.0,SdkMapTiles.project(0.0,0.0,1).second,1e-9);assertEquals(SdkMapTiles.project(0.0,180.0,1),SdkMapTiles.project(0.0,-180.0,1));assertEquals(0.0,SdkMapTiles.project(90.0,0.0,1).second,1e-8);assertEquals(.2,SdkMapTiles.wrappedDelta(.1,3.9,4.0),1e-9);assertThrows(Exception::class.java){SdkMapTiles.project(Double.NaN,0.0,1)};assertThrows(Exception::class.java){SdkMapTiles.project(0.0,0.0,20)}}
 @Test fun osmAttributionCannotBeReplacedByProviderText(){val n=JSONObject("""{"provider":{"data":"osm","attribution":"Overlay by Demo"}}""");assertEquals("© OpenStreetMap contributors · Overlay by Demo",SdkMapTiles.attribution(n));n.getJSONObject("provider").put("data","other");assertEquals("Overlay by Demo",SdkMapTiles.attribution(n))}
 private fun owner(id:String="org.example.maps")=JSONObject().put("id",id)
 private fun file(width:Int=256,height:Int=256):JSONObject{val id="a".repeat(32);val f=File(c.filesDir,"app-sdk/data/org.example.maps/private-blobs-v1/$id.data");f.parentFile!!.mkdirs();val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);f.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle();return JSONObject().put("id",id).put("path",f.absolutePath).put("size",f.length()).put("mime","image/png")}
 @Test fun ownRasterDecodesButForeignPathsAndInvalidDimensionsFail(){val f=file();var checked=0;val bitmap=SdkMapTiles.decode(c,owner(),f){checked++};assertEquals(256,bitmap.width);assertEquals(2,checked);bitmap.recycle();assertThrows(Exception::class.java){SdkMapTiles.source(c,owner("org.example.other"),f)};assertThrows(Exception::class.java){SdkMapTiles.decode(c,owner(),file(512,256)){}}}
 @Test fun revokedOrDamagedRasterNeverReturnsABitmap(){val f=file();assertThrows(Exception::class.java){SdkMapTiles.decode(c,owner(),f){error("revoked")}};val source=File(f.getString("path"));source.writeText("not a raster");f.put("size",source.length());assertThrows(Exception::class.java){SdkMapTiles.decode(c,owner(),f){}};val valid=file();var count=0;assertThrows(Exception::class.java){SdkMapTiles.decode(c,owner(),valid){if(++count==2)error("revoked after decode")}}}
}
