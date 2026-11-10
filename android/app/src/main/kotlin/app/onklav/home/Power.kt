package app.onklav.home

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Host-only physical-button alternative. No screen reading, gestures, or guest API. */
class PowerAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() { active = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit
    override fun onUnbind(intent: Intent?): Boolean { if(active === this) active = null;return super.onUnbind(intent) }
    override fun onDestroy() { if(active === this) active = null;super.onDestroy() }
    companion object {
        @Volatile private var active: PowerAccessibilityService? = null
        internal fun enabled(c: Context): Boolean {
            val component = ComponentName(c, PowerAccessibilityService::class.java)
            return c.getSystemService(AccessibilityManager::class.java)
                .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { ComponentName(it.resolveInfo.serviceInfo.packageName,it.resolveInfo.serviceInfo.name) == component }
        }
        internal fun settings() = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        internal fun openMenu(c: Context): Boolean = runCatching { enabled(c) && active?.performGlobalAction(GLOBAL_ACTION_POWER_DIALOG) == true }.getOrDefault(false)
    }
}

/** Small setup help only; Power has no app workspace. */
@Composable internal fun PowerDirectionsDialog(close:()->Unit) {
    AlertDialog(onDismissRequest=close,title={Text("Enable Power")},text={
        Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text("1. Tap Enable Power to open Android Accessibility.")
            Text("2. Open Installed apps (sometimes called Downloaded apps).")
            Text("3. Tap OnKlav Power, turn it on, then tap Allow.")
            Text("4. Go back to OnKlav. This step will show Complete.")
            Text("If Android blocks the switch: open Android Settings → Apps → OnKlav → ⋮ → Allow restricted settings, then try again.",style=MaterialTheme.typography.bodySmall)
        }
    },confirmButton={TextButton(onClick=close){Text("Got it")}})
}

/** Compatibility entry for restored routes: dispatch once, close to Apps, no page. */
@Composable internal fun PowerWorkspace(exit:()->Unit) {
    val context=LocalContext.current
    LaunchedEffect(Unit){exit();PowerAccessibilityService.openMenu(context)}
}
