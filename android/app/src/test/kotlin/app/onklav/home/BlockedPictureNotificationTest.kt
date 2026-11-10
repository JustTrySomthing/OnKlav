package app.onklav.home

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class BlockedPictureNotificationTest {
 @Test fun blockedSenderCannotBypassBlockingThroughAnMmsDownloadAlert(){
  val c=ApplicationProvider.getApplicationContext<Context>();val store=ChatStore(c);val id="c".repeat(32)
  MessageNotifications.picturePending(c,id,"+15550100011","Retry the picture download")
  val notifications=shadowOf(c.getSystemService(NotificationManager::class.java));assertNotNull(notifications.getNotification("picture:$id",0))
  store.requestSync(JSONObject().put("action","blocked").put("address","5550100011").put("blocked",true))
  MessageNotifications.picturePending(c,id,"+1 (555) 010-0011","Retry the picture download")
  assertNull(notifications.getNotification("picture:$id",0))
 }
}
