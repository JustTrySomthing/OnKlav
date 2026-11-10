package app.onklav.home

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.ContactsContract
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.onklav.shared.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable internal fun ThemeStar(selected:Boolean,change:(Boolean)->Unit,modifier:Modifier=Modifier,ink:Color=MaterialTheme.colorScheme.primary){
 Box(modifier.size(44.dp).clickable(interactionSource=remember{MutableInteractionSource()},indication=null,onClickLabel=if(selected)"Unstar"else"Star"){change(!selected)},contentAlignment=Alignment.Center){
  Icon(painterResource(if(selected)R.drawable.ref_message_star_filled else R.drawable.ref_message_star),if(selected)"Unstar Message"else"Star Message",Modifier.size(28.dp),tint=if(selected)ink else MaterialTheme.colorScheme.onSurface)
 }
}

@Composable internal fun ContactAvatar(address:String,name:String,modifier:Modifier=Modifier,revision:Int=0,photoUri:String?=null,contactId:Long=0){
 val context=LocalContext.current
 val bitmap by produceState<android.graphics.Bitmap?>(null,address,revision,photoUri,contactId){
  value=withContext(Dispatchers.IO){runCatching{
   if((address.isBlank()&&photoUri==null&&contactId<=0)||address.contains(';')||address.contains(',')||context.checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED)return@runCatching null
   val photo=photoUri?:if(contactId>0)"content://com.android.contacts/contacts/$contactId/photo"else context.contentResolver.query(Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,Uri.encode(address)),arrayOf(ContactsContract.PhoneLookup.PHOTO_URI),null,null,null)?.use{if(it.moveToFirst())it.getString(0)else null}?:return@runCatching null
   ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver,Uri.parse(photo))){decoder,info,_->val scale=(128f/maxOf(info.size.width,info.size.height)).coerceAtMost(1f);decoder.setTargetSize((info.size.width*scale).toInt().coerceAtLeast(1),(info.size.height*scale).toInt().coerceAtLeast(1));decoder.allocator=ImageDecoder.ALLOCATOR_SOFTWARE}
  }.getOrNull()}
 }
 Surface(modifier.clip(CircleShape),shape=CircleShape,color=MaterialTheme.colorScheme.primaryContainer){
  if(bitmap!=null)Image(bitmap!!.asImageBitmap(),name,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
  else Box(contentAlignment=Alignment.Center){Text(name.take(1).uppercase(),color=MaterialTheme.colorScheme.onPrimaryContainer,fontSize=22.sp)}
 }
}

/** Stable across contact formatting, US country prefix, and group-recipient order. */
fun messageDraftKey(address: String): String {
    val peers =
        address
            .split(';', ',')
            .map(::phoneKey)
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
            .joinToString(";")
    return "peer:" +
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(peers.toByteArray(Charsets.UTF_8))
            .take(16)
            .joinToString("") { "%02x".format(it) }
}

/** Inbox reference: time today, short month/day otherwise, year for older years. */
internal fun conversationTimestamp(at:Long,now:Long=System.currentTimeMillis(),locale:java.util.Locale=java.util.Locale.getDefault(),zone:java.util.TimeZone=java.util.TimeZone.getDefault(),use24Hour:Boolean=false):String {
 val a=java.util.Calendar.getInstance(zone,locale).apply{timeInMillis=at};val b=java.util.Calendar.getInstance(zone,locale).apply{timeInMillis=now}
 val pattern=if(a.get(java.util.Calendar.YEAR)==b.get(java.util.Calendar.YEAR)&&a.get(java.util.Calendar.DAY_OF_YEAR)==b.get(java.util.Calendar.DAY_OF_YEAR)){if(use24Hour)"HH:mm"else"h:mm a"}else if(a.get(java.util.Calendar.YEAR)==b.get(java.util.Calendar.YEAR))"MMM d"else"MMM d, yyyy"
 return java.text.SimpleDateFormat(pattern,locale).apply{timeZone=zone}.format(java.util.Date(at))
}
