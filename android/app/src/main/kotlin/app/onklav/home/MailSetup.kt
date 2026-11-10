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
 val labels=listOf("Yahoo","Google","Private domain")
 fun provider(label:String)=when(label){"Yahoo"->"Yahoo";"Google"->"Gmail";"Private domain"->"Other";else->error("Unknown email setup option")}
 fun label(provider:String)=when(provider){"Other"->"Private domain";"Gmail"->"Google";else->provider}
 const val googlePasswordHelp="https://support.google.com/accounts/answer/185833"
 const val yahooPasswordHelp="https://help.yahoo.com/kb/SLN15241.html"
 fun passwordHelp(provider:String)=if(provider=="Gmail")googlePasswordHelp else yahooPasswordHelp
 fun passwordSteps(provider:String)=if(provider=="Gmail")"1. Open your Google Account and turn on 2-Step Verification.\n2. Open App passwords, name the app OnKlav, and create a password.\n3. Return here and enter your full email address and the generated app password.\n\nSome work, school, security-key-only or Advanced Protection accounts do not offer app passwords. Check the help link for your account." else "1. Open Yahoo Account Security.\n2. Under External connections, choose Create app password (or Generate app password). Name it OnKlav and generate a password.\n3. Return here and enter your full email address and that app password.\n\nIf Yahoo does not offer app passwords for your account, check its help page."

}
@Composable internal fun MailSetupChoices(choose:(String)->Unit){
 Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)){
  MailSetup.labels.forEach{label->FilledTonalButton(onClick={choose(MailSetup.provider(label))},modifier=Modifier.fillMaxWidth(),contentPadding=PaddingValues(16.dp),shape=RoundedCornerShape(14.dp)){
   Icon(Icons.Outlined.Email,null);Spacer(Modifier.width(10.dp));Text(label)
  }}
 }
}
@Composable internal fun MailAppPasswordDirections(provider:String,close:()->Unit,web:(String)->Unit){
 AlertDialog(onDismissRequest=close,title={Text("Connect ${MailSetup.label(provider)}")},text={Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
  Text(MailSetup.passwordSteps(provider))
  TextButton(onClick={web(MailSetup.passwordHelp(provider))}){Text("App password instructions")}
  TextButton(onClick={web(if(provider=="Gmail")"https://myaccount.google.com/apppasswords"else"https://login.yahoo.com/account/security")}){Text("Open account settings")}
 }},confirmButton={TextButton(onClick=close){Text("Done")}})
}
