package app.onklav.home

/** Legacy project keys are ignored by PhotoEdit; HDR is no longer an editor effect. */
internal object PhotoHdr {
 val controls=emptyList<Pair<String,String>>()
 fun index(key:String)=0
 fun key(edit:PhotoEdit)="none"
 fun choose(edit:PhotoEdit,key:String)=edit
 fun apply(bitmap:android.graphics.Bitmap,edit:PhotoEdit)=Unit
}
