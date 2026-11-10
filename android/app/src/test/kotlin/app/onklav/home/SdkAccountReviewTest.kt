package app.onklav.home

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.onklav.shared.OnKlavTheme
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],qualifiers="w432dp-h936dp-mdpi")
class SdkAccountReviewTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 @Test fun providerReviewShowsScopesAndDoesNotOfferPrematureSignIn(){
  val manifest=JSONObject("""{"network":{"origins":["https://api.example.com"],"accounts":[{"label":"Example account","scopes":["openid","profile"],"api_origins":["https://api.example.com"]}]}}""")
  compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{Column{SdkNetworkPolicyReview(manifest)}}}}
  compose.onNodeWithText("Declared account providers").assertExists()
  compose.onNodeWithText("Requested scopes: openid, profile",substring=true).assertExists()
  compose.onNodeWithText("API services: https://api.example.com",substring=true).assertExists()
  compose.onNodeWithText("Installing does not connect an account.",substring=true).assertExists()
  compose.onAllNodes(hasClickAction() and hasText("Sign in")).assertCountEquals(0)
 }
}
