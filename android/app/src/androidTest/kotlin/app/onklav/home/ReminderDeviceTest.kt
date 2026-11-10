package app.onklav.home

import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.ViewAction
import androidx.test.espresso.UiController
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.*
import app.onklav.shared.OnKlavTheme
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun <T : android.view.View> change(type: Class<T>, update: (T) -> Unit) = object : ViewAction {
        override fun getConstraints(): org.hamcrest.Matcher<android.view.View> = isAssignableFrom(type)
        override fun getDescription() = "Set reminder picker value"
        override fun perform(controller: UiController, view: android.view.View) {
            update(type.cast(view)!!)
            controller.loopMainThreadUntilIdle()
        }
    }

    @Test fun choosePersistRescheduleAndRemove() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertFalse("Keep the existing default messenger", MessagingTransport.role(context))
        val key = "qa:reply-reminder"
        val store = ChatStore(context)
        var selected by mutableLongStateOf(0)
        compose.runOnUiThread {
            compose.activity.setContent {
                OnKlavTheme { ReplyReminderChooser(selected, {}, { selected = it }) }
            }
        }
        // Pixel capture is optional on headless ATD images with drawing disabled.
        if (InstrumentationRegistry.getArguments().getString("capture", "true") == "true") {
        runCatching { compose.onNodeWithTag("dialog-Remind Me to Reply").captureToImage().asAndroidBitmap() }.getOrNull()?.let { bitmap ->
            java.io.File(context.cacheDir, "qa-reminder-chooser.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        }
        compose.onNodeWithText("Tomorrow").performClick()
        compose.runOnIdle { assertTrue(selected > System.currentTimeMillis()) }
        compose.onNodeWithText("Choose Date & Time…").performClick()
        val custom = java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.DAY_OF_MONTH, 2)
            set(java.util.Calendar.HOUR_OF_DAY, 13)
            set(java.util.Calendar.MINUTE, 45)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        onView(isAssignableFrom(android.widget.DatePicker::class.java)).perform(
            change(android.widget.DatePicker::class.java) {
                it.updateDate(custom.get(java.util.Calendar.YEAR), custom.get(java.util.Calendar.MONTH),
                    custom.get(java.util.Calendar.DAY_OF_MONTH))
            })
        onView(withId(android.R.id.button1)).perform(click())
        onView(isAssignableFrom(android.widget.TimePicker::class.java)).perform(
            change(android.widget.TimePicker::class.java) { it.hour = 13; it.minute = 45 })
        onView(withId(android.R.id.button1)).perform(click())
        compose.runOnIdle { assertEquals(custom.timeInMillis, selected) }
        store.requestSync(JSONObject().put("action", "message").put("key", key)
            .put("remind_at", selected).put("thread", 1).put("reminder_title", "QA"))
        ReplyReminders.schedule(context, key, selected)
        assertEquals(selected, ChatStore(context).requestSync(JSONObject().put("action", "snapshot"))
            .flags(key).getLong("remind_at"))
        val finished = java.util.concurrent.CountDownLatch(1)
        context.sendOrderedBroadcast(
            android.content.Intent(context, ReplyReminderReceiver::class.java)
                .setAction("remind").putExtra("key", key),
            null,
            object : android.content.BroadcastReceiver() {
                override fun onReceive(c: android.content.Context, i: android.content.Intent) {
                    finished.countDown()
                }
            }, android.os.Handler(android.os.Looper.getMainLooper()), 0, null, null,
        )
        assertTrue("Queued alarm completed", finished.await(20, java.util.concurrent.TimeUnit.SECONDS))
        assertEquals("Old queued alarms must not consume a rescheduled reminder", selected,
            store.requestSync(JSONObject().put("action", "snapshot")).flags(key).getLong("remind_at"))
        assertFalse(context.getSystemService(android.app.NotificationManager::class.java)
            .activeNotifications.any { it.id == key.hashCode() })
        compose.onNodeWithText("Choose Date & Time…").assertIsDisplayed()
        compose.onNodeWithText("In an Hour").performClick()
        compose.runOnIdle {
            assertTrue(kotlin.math.abs(selected - System.currentTimeMillis() - 3_600_000L) < 60_000L)
        }
        compose.onNodeWithText("Remove Reminder").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0L, selected) }
        store.requestSync(JSONObject().put("action", "message").put("key", key).put("remind_at", 0))
        ReplyReminders.schedule(context, key, 0)
        assertEquals(0L, store.requestSync(JSONObject().put("action", "snapshot")).flags(key).optLong("remind_at"))

        store.requestSync(JSONObject().put("action", "message").put("key", key)
            .put("remind_at", System.currentTimeMillis() + 3_600_000L))
        compose.runOnUiThread {
            compose.activity.setContent { OnKlavTheme { MessengerSettings("Reminders", {}) } }
        }
        compose.waitUntil(15_000) { compose.onAllNodesWithText("QA").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("QA").performClick()
        compose.onNodeWithText("Open Message").assertIsDisplayed()
        compose.onNodeWithText("Reschedule").performClick()
        compose.onNodeWithText("Tomorrow").performClick()
        compose.waitUntil(15_000) {
            store.requestSync(JSONObject().put("action", "snapshot")).flags(key).optLong("remind_at") >
                System.currentTimeMillis() + 3_600_000L
        }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("Choose Date & Time…").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText("QA").performClick()
        compose.onNodeWithText("Remove Reminder").performClick()
        compose.waitUntil(15_000) {
            store.requestSync(JSONObject().put("action", "snapshot")).flags(key).optLong("remind_at") == 0L
        }
    }
}
