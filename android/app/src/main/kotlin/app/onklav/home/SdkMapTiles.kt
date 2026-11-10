package app.onklav.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.io.File
import kotlin.math.*

internal object SdkMapTiles {
 const val LIMIT=85.05112878
 fun project(latitude:Double,longitude:Double,zoom:Int):Pair<Double,Double>{
  require(latitude.isFinite()&&longitude.isFinite()&&zoom in 0..19)
  val world=(1 shl zoom).toDouble();val phi=Math.toRadians(latitude.coerceIn(-LIMIT,LIMIT));val x=(((longitude+180)%360+360)%360)/360*world
  return x to ((1-ln(tan(phi)+1/cos(phi))/Math.PI)/2*world).coerceIn(0.0,world)
 }
 fun wrappedDelta(x:Double,center:Double,world:Double):Double{require(x.isFinite()&&center.isFinite()&&world.isFinite()&&world>=1);return ((x-center+world/2)%world+world)%world-world/2}
 fun attribution(node:JSONObject):String {val p=node.getJSONObject("provider");val own=p.getString("attribution");return if(p.getString("data")=="osm")"© OpenStreetMap contributors · $own" else own}
 fun source(c:Context,owner:JSONObject,f:JSONObject):File{
  val id=owner.getString("id");val handle=f.getString("id");require(id.matches(Regex("[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)+"))&&handle.matches(Regex("[a-f0-9]{32}")))
  val expected=File(c.filesDir,"app-sdk/data/$id/private-blobs-v1/$handle.data");val actual=File(f.getString("path"));require(actual.absoluteFile==expected.absoluteFile&&actual.canonicalFile==actual.absoluteFile&&!java.nio.file.Files.isSymbolicLink(actual.toPath())&&actual.isFile&&actual.length()==f.getLong("size")&&actual.length() in 1..524288&&f.getString("mime") in setOf("image/png","image/jpeg"));return actual
 }
 fun decode(c:Context,owner:JSONObject,file:JSONObject,authorize:()->Unit):Bitmap{
  authorize();val source=source(c,owner,file);val bytes=source.inputStream().use{input->val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(16384);while(true){val count=input.read(buffer);if(count<0)break;require(out.size()+count<=524288);out.write(buffer,0,count)};out.toByteArray()}
  val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds);require(bounds.outWidth==256&&bounds.outHeight==256&&bounds.outMimeType in setOf("image/png","image/jpeg")){"Choose 256×256 PNG/JPEG map tiles"}
  val result=BitmapFactory.decodeByteArray(bytes,0,bytes.size)?:error("Map tile could not be decoded")
  try{authorize();return result}catch(e:Exception){result.recycle();throw e}
 }
}
