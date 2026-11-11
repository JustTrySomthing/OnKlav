package app.onklav.home

import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
                    .onFailure{permissionError="This phone could not open update installation permission. Try again."}
            }
        }
    }
    ReferenceDialog("OnKlav updates",close,footer={DialogAction("Close"){close()}}) {
        Column(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Installed: OnKlav ${BuildConfig.VERSION_NAME}",style=MaterialTheme.typography.titleMedium)
            Text("Get updates directly from OnKlav’s GitHub releases. Your apps, games, messages and settings stay on this phone.")
            PreferenceToggle("Check automatically",model.automatic){model.changeAutomatic(it)}
            Text("Checks daily and shows available updates on the Apps tab. Downloads start when you tap Update now.",style=MaterialTheme.typography.bodySmall)
            PreferenceToggle("Include release candidates",model.previews,enabled=!state.busy){model.changePreviews(it)}
            Button(onClick={model.checkNow()},enabled=!state.busy,modifier=Modifier.fillMaxWidth()) {Text(if(state.busy&&!state.downloading)"Checking…"else"Check for updates")}
            Text(state.message)
            state.release?.let { release ->
                Text("OnKlav ${release.version.label} · ${"%.1f".format(java.util.Locale.US,release.bytes/1_000_000.0)} MB",style=MaterialTheme.typography.titleMedium)
                if(state.downloading) {
                    LinearProgressIndicator(progress={state.downloaded.toFloat()/release.bytes},modifier=Modifier.fillMaxWidth())
                    Text("${state.downloaded*100/release.bytes}% downloaded")
                    TextButton(onClick={model.cancelDownload()}){Text("Cancel download")}
                } else Button(onClick={model.updateNow()},enabled=!state.busy,modifier=Modifier.fillMaxWidth()) {
                    Text(if(state.ready)"Install update"else"Update now")
                }
            }
            (permissionError?:state.error)?.let { Text(it,color=MaterialTheme.colorScheme.error) }
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
