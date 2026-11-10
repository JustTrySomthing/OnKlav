package app.onklav.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
@Composable internal fun MailAppPasswordDirections(provider:String,close:()->Unit,web:(String)->Unit,email:String=""){
 fun open(url: String) { close(); web(url) }
 AlertDialog(onDismissRequest=close,title={Text("Connect ${MailSetup.label(provider)}")},text={Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
  Text(MailSetup.passwordSteps(provider))
  Text("Account settings open in your browser. Return to OnKlav when you’re ready to connect.",
      style = MaterialTheme.typography.bodySmall)
  if (provider == "GMX") TextButton(onClick = { open(MailSetup.gmxImapHelp) }) { Text("How to enable IMAP") }
  TextButton(onClick={open(MailSetup.passwordHelp(provider))}){Text("App password instructions")}
  TextButton(onClick={open(MailSetup.accountSettings(provider, email))}){Text("Open account settings")}
 }},confirmButton={TextButton(onClick=close){Text("Done")}})
}
