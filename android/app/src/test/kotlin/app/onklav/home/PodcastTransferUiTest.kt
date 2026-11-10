package app.onklav.home

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.onklav.shared.OnKlavTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
class PodcastTransferUiTest {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 @Test fun episodeShowsMeasuredProgressCompletionAndFailure(){
  val episode="a".repeat(64);val model=androidx.lifecycle.ViewModelProvider(compose.activity).get(SdkAppModel::class.java);val key=SdkPodcasts.transferKey(model.appIdentity,episode)
  SdkPodcasts.transfers.value=mapOf(key to SdkPodcasts.Transfer("downloading",5L*1048576,10L*1048576))
  compose.activity.setContent{OnKlavTheme{SdkPodcastTransfer(model,episode,false)}}
  compose.onNodeWithText("Downloading 50%",substring=true).assertExists();compose.onNodeWithContentDescription("Episode download progress").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo,androidx.compose.ui.semantics.ProgressBarRangeInfo(.5f,0f..1f)))
  compose.runOnUiThread{SdkPodcasts.transfers.value=mapOf(key to SdkPodcasts.Transfer("complete",10L*1048576,10L*1048576,"Download complete"))}
  compose.waitForIdle();compose.onNodeWithText("Download complete").assertExists()
  compose.runOnUiThread{SdkPodcasts.transfers.value=mapOf(key to SdkPodcasts.Transfer("error",message="Unable to download: connection stalled. Tap the arrow to retry."))}
  compose.waitForIdle();compose.onNodeWithText("Unable to download",substring=true).assertExists()
  compose.runOnUiThread{SdkPodcasts.transfers.value=mapOf(key to SdkPodcasts.Transfer("cancelled",message="Download cancelled"))}
  compose.waitForIdle();compose.onNodeWithText("Download cancelled").assertExists()
 }
}
