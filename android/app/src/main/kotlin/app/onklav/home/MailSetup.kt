package app.onklav.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.onklav.shared.ClosePageButton
import androidx.compose.ui.unit.dp

/** Enrollment options only; historical accounts remain parseable and manageable. */
internal object MailSetup {
 val labels=listOf("GMX","Yahoo","Google","Private domain")
 fun provider(label:String)=when(label){"GMX"->"GMX";"Yahoo"->"Yahoo";"Google"->"Gmail";"Private domain"->"Other";else->error("Unknown email setup option")}
 fun label(provider:String)=when(provider){"Other"->"Private domain";"Gmail"->"Google";else->provider}
 const val gmxSignup = "https://www.gmx.com/mail/create-email-address/"
 const val gmxImapHelp = "https://support.gmx.com/pop-imap/toggle.html"
 const val gmxPasswordHelp = "https://support.gmx.com/security/2fa/application-specific-passwords.html"
 fun requiresAppPassword(provider: String) = provider in setOf("Gmail", "Yahoo")
 fun hasDirections(provider: String) = provider == "GMX" || requiresAppPassword(provider)
 fun accountSettings(provider: String, email: String = "") = when (provider) {
  "GMX" -> if (email.substringAfterLast('@').lowercase() in setOf("gmx.de", "gmx.net")) "https://www.gmx.net/" else "https://www.gmx.com/"
  "Gmail" -> "https://myaccount.google.com/apppasswords"
  "Yahoo" -> "https://login.yahoo.com/account/security"
  else -> error("No provider account settings")
 }
 const val googlePasswordHelp="https://support.google.com/accounts/answer/185833"
 const val yahooPasswordHelp="https://help.yahoo.com/kb/SLN15241.html"
 fun passwordHelp(provider: String) = when (provider) {
  "GMX" -> gmxPasswordHelp
  "Gmail" -> googlePasswordHelp
  "Yahoo" -> yahooPasswordHelp
  else -> error("No provider password guide")
 }
 fun passwordSteps(provider:String)=if(provider=="GMX") "1. Sign in to GMX in your browser. In email settings, enable POP3 and IMAP access.\n2. If you use two-factor authentication, create an app password named OnKlav under Security Options.\n3. Return here with your full email address and your GMX password or app password." else if(provider=="Gmail")"1. Open your Google Account and turn on 2-Step Verification.\n2. Open App passwords, name the app OnKlav, and create a password.\n3. Return here and enter your full email address and the generated app password.\n\nSome work, school, security-key-only or Advanced Protection accounts do not offer app passwords. Check the help link for your account." else "1. Open Yahoo Account Security.\n2. Under External connections, choose Create app password (or Generate app password). Name it OnKlav and generate a password.\n3. Return here and enter your full email address and that app password.\n\nIf Yahoo does not offer app passwords for your account, check its help page."

}
@Composable internal fun MailSetupChoices(createGmx: (() -> Unit)? = null, choose:(String)->Unit){
 Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)){
  MailSetup.labels.forEach { label ->
   FilledTonalButton(onClick = { choose(MailSetup.provider(label)) }, modifier = Modifier.fillMaxWidth(),
       contentPadding = PaddingValues(16.dp), shape = RoundedCornerShape(14.dp)) {
    Icon(Icons.Outlined.Email, null)
    Spacer(Modifier.width(10.dp))
    Text(label)
   }
   if (label == "GMX" && createGmx != null) {
    TextButton(onClick = createGmx, modifier = Modifier.fillMaxWidth()) { Text("Create a GMX account") }
   }
  }
 }
}
@Composable internal fun MailSetupPage(
 title: String,
 closeLabel: String,
 onClose: () -> Unit,
 closeEnabled: Boolean = true,
 footer: (@Composable ColumnScope.() -> Unit)? = null,
 content: @Composable ColumnScope.() -> Unit,
) {
 Dialog(
  onDismissRequest = {},
  properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false,
   usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
 ) {
  Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
   Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), horizontalAlignment = Alignment.CenterHorizontally) {
    Row(Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(start = 20.dp, end = 8.dp),
     verticalAlignment = Alignment.CenterVertically) {
     Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
     ClosePageButton(enabled = closeEnabled, description = closeLabel, onClick = onClose)
    }
    HorizontalDivider()
    Column(Modifier.weight(1f).widthIn(max = 560.dp).fillMaxWidth()
     .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
     verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    if (footer != null) {
     HorizontalDivider()
     Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp), content = footer)
    }
   }
  }
 }
}

@Composable internal fun MailAppPasswordDirections(provider:String,close:()->Unit,web:(String)->Unit,email:String=""){
 fun open(url: String) { close(); web(url) }
 MailSetupPage("Connect ${MailSetup.label(provider)}", "Close setup directions", close) {
  Text(MailSetup.passwordSteps(provider))
  if (provider == "GMX") TextButton(onClick = { open(MailSetup.gmxImapHelp) }) { Text("How to enable IMAP") }
  TextButton(onClick={open(MailSetup.passwordHelp(provider))}){Text("App password instructions")}
  TextButton(onClick={open(MailSetup.accountSettings(provider, email))}){Text("Open account settings")}
  if (provider == "GMX") TextButton(onClick = { open(MailSetup.gmxSignup) }) { Text("Create a GMX account") }
 }
}
