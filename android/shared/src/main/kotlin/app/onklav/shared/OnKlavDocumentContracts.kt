package app.onklav.shared

import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.net.Uri

/** Host-module read-only viewer. WASM developers use the authorized files.open broker. */
object OnKlavDocumentContracts {
 fun view(c:Context,uri:Uri,mime:String):Intent {
  require(uri.scheme=="content") { "A user-selected content URI is required" }
  return Intent(Intent.ACTION_VIEW).setClassName(c.packageName,"app.onklav.home.DocuViewActivity")
   .setDataAndType(uri,mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
   .apply{clipData=ClipData.newRawUri("Document",uri)}
 }
}
