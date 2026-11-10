package app.onklav.home

import android.content.Context
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import app.onklav.shared.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],qualifiers="w432dp-h936dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class SettingsUiHostTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val c:Context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun click(text:String) {
        compose.waitUntil(15000) { pump();runCatching {compose.onNodeWithText(text).assertExists()}.isSuccess }
        val node=compose.onNodeWithText(text)
        if(runCatching {node.assertIsDisplayed()}.isFailure)node.reveal(compose) { pump() }
        node.performClick();pump()
    }
    private fun pump(){repeat(4){org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(16));compose.mainClock.advanceTimeBy(100)}}
    @Test(timeout=90000) fun combinedAppDocumentsBackupIsReachable() {
        compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{BackupSettingsDialog{}}}}
        click("App Documents Backup")
        compose.onNodeWithText("Save Backup").assertIsEnabled()
        compose.onNodeWithText("Choose Backup").assertIsEnabled()
        click("Close")
        compose.onNodeWithText("App Documents Backup").assertExists()
    }
    @Test(timeout=90000) fun sdkDocumentBackupsAreReachableAndReturnToSettings() {
        compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{BackupSettingsDialog{}}}}
        for(title in listOf("App Documents Backup","Browser Backup","YouTube Backup","RSS Backup","Read Aloud Backup","Files Backup","Annotated Images Backup")){
            click(title)
            compose.onNodeWithText("Save Backup").assertIsEnabled()
            compose.onNodeWithText("Choose Backup").assertIsEnabled()
            click("Close")
        }
        compose.onNodeWithText("Save App Backup").assertExists()
    }
    @Test(timeout=90000) fun notesBackupIsReachableAndReturnsToTheMetadataDialog() {
        compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{BackupSettingsDialog{}}}}
        click("Notes Backup")
        compose.onNodeWithText("Save Notes Backup").assertIsEnabled()
        compose.onNodeWithText("Choose Notes Backup").assertIsEnabled()
        click("Close")
        compose.onNodeWithText("Save App Backup").assertExists()
    }
    @Test(timeout=90000) fun historyExportAndImportCheckAreReachable() {
        compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{BackupSettingsDialog{}}}}
        click("Messages and Media Backup")
        compose.onNodeWithText("Save messages and media").assertIsEnabled()
        compose.onNodeWithText("Choose backup to restore").reveal(compose) { pump() }.assertIsEnabled()
        click("Close")
        compose.onNodeWithText("Save App Backup").assertExists()
    }
    @Test(timeout=90000) fun checkedHistoryReviewShowsCountsAndKeepsProviderRestoreDisabled() {
        var closed=false
        val review=HistoryImport.Review(4,2,3,1024,3,2,1,1,1,2,3,4,"a".repeat(64))
        compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{HistoryRestoreReviewDialog(review){closed=true}}}}
        compose.onNodeWithText("4 SMS · 2 MMS · 3 parts").assertExists()
        compose.onNodeWithText("Restore messages").assertDoesNotExist()
        compose.onNodeWithText("Review restoration").assertDoesNotExist()
        compose.onNodeWithText("Enable carrier phone testing in Setup",substring=true).assertExists()
        click("Close");assertTrue(closed)
    }
    @Test(timeout=90000) fun comparisonShowsReadOnlyCandidatesAndKeepsRestoreDisabled() {
        var compared=false;var closed=false
        val data=HistoryImport.Comparison(4,2,1,1,2,1,1,1)
        val review=HistoryImport.Review(7,1,3,1024,3,4,1,0,1,2,3,4,"a".repeat(64),data)
        compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{HistoryRestoreReviewDialog(review,onCompare={compared=true}){closed=true}}}}
        click("Compare with phone");assertTrue(compared)
        compose.onNodeWithText("4 Already Present · 2 New Candidates").assertExists()
        compose.onNodeWithText("1 Need Review · 1 Skipped").assertExists()
        compose.onNodeWithText("Restore messages").assertDoesNotExist()
        compose.onNodeWithText("Review restoration").assertDoesNotExist()
        click("Close");assertTrue(closed)
    }
    @Test(timeout=90000) fun settingsRemainReachableWithLargeText() {
        compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(1f,1.5f)) { SettingsPane{} }
        }}}
        listOf("Appearance","Profile","Setup","Sounds","Security","Storage","Read Aloud","Backups","Games","Apps","Walkie Talkie","Quiet Hours","Reminders","Help","About","Advanced").forEach{
            compose.onNodeWithText(it).reveal(compose) { pump() }.assertIsDisplayed()
        }
    }
    @Test(timeout=90000) fun nestedSettingsSaveAndCancelAreRealAndReturnToParent() {
        val p=c.getSharedPreferences("notification_preferences",0);p.edit().clear().commit()
        val route=mutableStateOf("Sounds");var closed=false
        compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{SettingsDialogs(route.value){closed=true}}}}
        click("Notifications");click("Vibrate");click("Save")
        assertFalse(p.getBoolean("vibrate",true));compose.onNodeWithText("Sounds").assertExists();assertFalse(closed)
        click("Notifications");click("Vibrate");click("Cancel")
        assertFalse(p.getBoolean("vibrate",true))
        compose.runOnIdle{route.value="Security"};pump();click("Spam")
        compose.onNodeWithText("Spam Screening").assertExists();click("Cancel")
        compose.onNodeWithText("Security").assertExists();click("Blocked Messages")
        compose.onNodeWithTag("dialog-Blocked Messages").assertExists();click("Close")
        compose.onNodeWithText("Security").assertExists()
    }
    @Test(timeout=90000) fun quietHoursUsesStagedRepeatChoicesAndPersistsOnlyOnSave() {
        val prefs=c.getSharedPreferences("quiet_hours",0);prefs.edit().clear().commit()
        var closed=false
        compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{MessengerSettings("Quiet Hours"){closed=true}}}}
        click("Enable Schedule");click("Repeat · Every Day");click("Monday");click("Cancel")
        compose.onNodeWithText("Repeat · Every Day").assertExists()
        click("Repeat · Every Day");click("Monday");click("Done");click("Save")
        compose.waitUntil(15000){pump();closed}
        assertTrue(prefs.getBoolean("enabled",false));assertEquals(126,prefs.getInt("days",0))
    }
    @Test(timeout=90000) fun walkieSettingsChangeTheSignedAppsPrivateStoreWithoutAddingPermissions() {
        runBlocking{AppSdk.bootstrap(c)}
        AppSdk.sync(c,JSONObject().put("action","permissions").put("id","app.onklav.walkie").put("grants",org.json.JSONArray(listOf("storage.private"))))
        AppSdk.sync(c,JSONObject().put("action","walkie_preferences").put("args",JSONObject().put("auto_play",true).put("auto_send",true).put("beeps",true)))
        var closed=false
        compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{WalkieSettingsDialog{closed=true}}}}
        try {compose.waitUntil(15000){pump();runCatching{compose.onNodeWithText("Save").assertIsEnabled()}.isSuccess}}
        catch(e:Throwable){
            val tree=compose.onAllNodes(isRoot(),useUnmergedTree=true).fetchSemanticsNodes().indices.joinToString("\n"){compose.onAllNodes(isRoot(),useUnmergedTree=true)[it].printToString()}
            throw AssertionError("Walkie preferences failed to become ready:\n$tree",e)
        }
        click("Auto Send");click("Save")
        compose.waitUntil(15000){pump();closed}
        val actual=AppSdk.sync(c,JSONObject().put("action","walkie_preferences")) as JSONObject
        assertFalse(actual.getJSONObject("preferences").getBoolean("auto_send"))
        val walkie=runBlocking{AppSdk.list(c)}.first{it.getJSONObject("manifest").getString("id")=="app.onklav.walkie"}
        assertEquals("[\"storage.private\"]",walkie.getJSONArray("grants").toString())
    }
    @Test(timeout=90000) fun walkiePreferencesCanRecoverAfterPrivateStoragePermissionIsRestored(){
        runBlocking{AppSdk.bootstrap(c)}
        val id="app.onklav.walkie"
        AppSdk.sync(c,JSONObject().put("action","permissions").put("id",id).put("grants",org.json.JSONArray()))
        compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{WalkieSettingsDialog{}}}}
        compose.waitUntil(15000){pump();compose.onAllNodesWithText("Try Again").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Loading Walkie preferences…").assertDoesNotExist()
        compose.onNodeWithText("Save").assertIsNotEnabled()
        AppSdk.sync(c,JSONObject().put("action","permissions").put("id",id).put("grants",org.json.JSONArray(listOf("storage.private"))))
        click("Try Again")
        compose.waitUntil(15000){pump();runCatching{compose.onNodeWithText("Save").assertIsEnabled()}.isSuccess}
        compose.onNodeWithText("Try Again").assertDoesNotExist()
    }
    @Test(timeout=90000) fun appearanceDraftCancelAndApplyAndNestedBackgroundCancel() {
        val appearance=Appearance(c);appearance.apply(AppearanceSpec())
        val open=mutableStateOf(true)
        compose.runOnUiThread{compose.activity.setContent{OnKlavTheme(appearance){if(open.value)AppearanceDialog{open.value=false}}}}
        compose.onNodeWithContentDescription("Dark. Switch to Light").performClick();pump()
        assertTrue(appearance.spec.dark)
        click("Backgrounds & Overlay");click("Reset to Default Background");click("Cancel")
        compose.onNodeWithTag("dialog-Appearance").assertExists()
        click("Cancel");assertTrue(appearance.spec.dark)
        compose.runOnIdle{open.value=true};pump()
        compose.onNodeWithContentDescription("Dark. Switch to Light").performClick();click("Apply")
        compose.waitUntil(15000){pump();!open.value}
        assertFalse(appearance.spec.dark);assertFalse(Appearance(c).spec.dark)
    }
    @Test(timeout=60000)fun helpAboutAndAdvancedHaveSeparateWorkingMenus(){
        val route=mutableStateOf("Help");var closed=false
        compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{SettingsDialogs(route.value){closed=true}}}}
        click("Getting Started");compose.onNodeWithText("Use Apps",substring=true).assertIsDisplayed();click("Close");compose.onNodeWithText("Android Help").assertExists();compose.onNodeWithText("App Permissions").assertDoesNotExist()
        compose.runOnIdle{route.value="About"};pump();click("About OnKlav");compose.onNodeWithText("Messages, Groups",substring=true).assertExists();click("Close");click("Privacy Policy");compose.onNodeWithTag("dialog-Privacy Policy").assertExists();click("Close");click("Open-source Licenses");compose.onNodeWithText("AndroidX / Compose",substring=true).assertExists();click("Close")
        compose.runOnIdle{route.value="Advanced"};pump();compose.onNodeWithText("App Permissions").assertExists();compose.onNodeWithText("Sending SIM").assertExists();compose.onNodeWithText("Open-source Licenses").assertDoesNotExist();click("Close");assertTrue(closed)
    }

}
