package app.onklav.home

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
@LooperMode(LooperMode.Mode.PAUSED)
class SdkPermissionRecoveryTest {
 @Test fun revokedSessionKeepsVisibleFrameAndPendingEditsUntilExplicitReopen()=runBlocking {
  val app=ApplicationProvider.getApplicationContext<Application>();val model=SdkAppModel(app)
  model.open("app.onklav.notes");val original=model.frame.toString();model.edit("note.text","Keep this local draft")
  AppSdk.call(app,JSONObject().put("action","permissions").put("id","app.onklav.notes").put("grants",JSONArray()))
  model.open("app.onklav.notes")
  assertTrue(model.sessionUnavailable);assertTrue(model.accessNotice);assertTrue(model.hasUnsaved("note.text"));assertEquals(original,model.frame.toString())
  model.keepVisibleDraft();assertFalse(model.accessNotice);assertTrue(model.sessionUnavailable);assertTrue(model.hasUnsaved("note.text"))
  AppSdk.call(app,JSONObject().put("action","permissions").put("id","app.onklav.notes").put("grants",JSONArray(listOf("storage.private"))))
  model.reopenDiscardingDraft();assertFalse(model.sessionUnavailable);assertFalse(model.accessNotice);assertFalse(model.hasUnsaved("note.text"));assertNotEquals("Keep this local draft",model.frame.child("state").child("note").optString("text"))
 }
}
