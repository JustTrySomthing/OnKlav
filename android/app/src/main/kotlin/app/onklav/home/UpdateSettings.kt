package app.onklav.home

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.onklav.shared.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date

@Composable
internal fun UpdateSettingsDialog(close:()->Unit) {
    val context=LocalContext.current
    val model:OnKlavUpdateModel=viewModel()
    val state=model.ui
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if(context.packageManager.canRequestPackageInstalls())model.install(context)else model.installPermissionDenied()
    }
    var permissionError by remember {mutableStateOf<String?>(null)}
    LaunchedEffect(state.installRequested,state.busy,lifecycle) {
        if(state.installRequested&&!state.busy)lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if(model.ui.installRequested) {
                model.consumeInstallRequest()
                permissionError=null
                if(context.packageManager.canRequestPackageInstalls())model.install(context)
                else runCatching {permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:${context.packageName}")))}
                    .onFailure{permissionError="Couldn’t open Android’s install settings."}
            }
        }
    }
    ReferenceDialog("OnKlav updates",close,footer={
        DialogAction("Close"){close()}
        Spacer(Modifier.width(4.dp))
        when {
            state.downloading -> TextButton(onClick=model::cancelDownload) { Text("Cancel download") }
            state.release!=null -> Button(onClick=model::updateNow,enabled=!state.busy) {
                Text(if(state.ready)"Install update"else"Update now")
            }
            else -> Button(onClick=model::checkNow,enabled=!state.busy) { Text("Check now") }
        }
    },summary={
        Column(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=4.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            state.release?.let { release ->
                Text("${release.version.label} available",style=MaterialTheme.typography.titleMedium)
                if (state.downloading) {
                    LinearProgressIndicator(progress={state.downloaded.toFloat()/release.bytes},modifier=Modifier.fillMaxWidth())
                    Text("Downloading ${state.downloaded*100/release.bytes}%",style=MaterialTheme.typography.bodySmall)
                }
            }
            val error=permissionError?:state.error
            val status=if(state.checking)"Checking GitHub…"else state.message
            (error?:status)?.let {Text(it,color=if(error!=null)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)}
            if(state.checkedAt>0) {
                val locale=context.resources.configuration.locales[0]
                val skeleton=if(DateFormat.is24HourFormat(context))"MMMdHms"else"MMMdhms"
                val checked=remember(state.checkedAt,locale,skeleton) {
                    SimpleDateFormat(DateFormat.getBestDateTimePattern(locale,skeleton),locale).format(Date(state.checkedAt))
                }
                Text("Last checked on GitHub $checked",style=MaterialTheme.typography.bodySmall)
            }
        }
    }) {
        Column(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text("Installed ${BuildConfig.VERSION_NAME}",style=MaterialTheme.typography.bodySmall)
            PreferenceToggle("Check daily",model.automatic){model.changeAutomatic(it)}
            PreferenceToggle("Include release candidates",model.previews,enabled=!state.busy){model.changePreviews(it)}
            if(state.release!=null&&!state.downloading) {
                TextButton(onClick=model::checkNow,enabled=!state.busy) {Text("Check again")}
            }
        }
    }
}

@Composable
internal fun OnKlavUpdateNotice(show:Boolean,open:()->Unit) {
    val model:OnKlavUpdateModel=viewModel()
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(show,model.automatic,lifecycle) {
        if(model.automatic)lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while(true) {
                model.automaticCheck()
                delay(60_000L)
            }
        }
    }
    val release=model.ui.release
    if(show&&model.ui.prompt&&release!=null)AlertDialog(
        onDismissRequest={model.dismissPrompt()},
        title={Text("OnKlav update available")},
        text={Text(if(model.ui.ready)"Version ${release.version.label} is ready to install." else "Version ${release.version.label} is ready to download.")},
        confirmButton={TextButton(enabled=!model.ui.busy,onClick={open();model.updateNow()}){Text("Update now")}},
        dismissButton={TextButton(onClick={model.dismissPrompt()}){Text("Later")}},
    )
}
