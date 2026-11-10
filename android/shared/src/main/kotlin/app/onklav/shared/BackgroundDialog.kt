package app.onklav.shared

import app.onklav.shared.OnKlavFileContracts

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import kotlinx.coroutines.*

/** Independent background draft: Cancel does not alter the Appearance editor. */
@Composable
fun BackgroundDialog(
    initial: AppearanceSpec,
    accept: (AppearanceSpec) -> Unit,
    removed: (String) -> Unit,
    dismiss: () -> Unit,
) {
    val appearance = LocalAppearance.current
    val context = LocalContext.current
    val store = remember { AppearanceStore(context) }
    val scope = rememberCoroutineScope()
    var snapshot by rememberSaveable { mutableStateOf(initial.json()) }
    val draft = remember(snapshot) { AppearanceSpec.decode(snapshot) }
    var personal by remember { mutableStateOf(store.gallery()) }
    var pending by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var own by rememberSaveable {
        mutableStateOf(initial.background.takeIf { it.startsWith("custom:") } ?: "")
    }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<String?>(null) }
    val ready by
        produceState(draft.background.isEmpty(), draft.background) {
            value = false
            value =
                draft.background.isEmpty() ||
                    withContext(Dispatchers.IO) {
                        runCatching { store.bitmap(draft.background, 1000) != null }
                            .getOrDefault(false)
                    }
        }
    fun work(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Could not complete this action."
            } finally {
                busy = false
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            var c = context
            while (c is android.content.ContextWrapper && c !is android.app.Activity) c =
                c.baseContext
            if ((c as? android.app.Activity)?.isChangingConfigurations != true)
                store.discardUnreferenced(pending, appearance.spec)
        }
    }
    val picker =
        rememberLauncherForActivityResult(OnKlavFileContracts.OpenDocument()) { uri ->
            if (uri != null)
                work {
                    val key =
                        withContext(Dispatchers.IO) {
                            val bytes =
                                context.contentResolver.openInputStream(uri)?.use {
                                    ThemePackage.bounded(it, 30 * 1024 * 1024)
                                } ?: error("Could not open image.")
                            store.importImage(bytes)
                        }
                    pending = ArrayList(pending + key)
                    own = key
                    snapshot = draft.copy(background = key).json()
                }
        }
    OnKlavTheme(appearance, preview = initial) {
        val fullHeight =
            (LocalContext.current.resources.displayMetrics.heightPixels /
                    LocalDensity.current.density)
                .dp
        Dialog(
            { if (!busy) dismiss() },
            properties =
                DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
        ) {
            Surface(
                Modifier.widthIn(max = 460.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .heightIn(max = minOf((LocalConfiguration.current.screenHeightDp.dp - 48.dp).coerceAtLeast(160.dp), 730.dp))
                    .testTag("dialog-Backgrounds"),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp)) {
                    Text(
                        "Backgrounds",
                        Modifier.align(Alignment.CenterHorizontally),
                        fontSize = 24.sp,
                        lineHeight = 33.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Column(
                        Modifier.weight(1f, false)
                            .verticalScroll(rememberScrollState())
                            .padding(top = 20.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        MessagePreview(draft, backgroundPicker = true)
                        Column(Modifier.padding(horizontal = 2.dp, vertical = 2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Background Opacity",
                                    Modifier.weight(1f),
                                    fontSize = 16.sp,
                                    color =
                                        MaterialTheme.colorScheme.onSurface.copy(
                                            alpha = if (draft.background.isEmpty()) .38f else 1f
                                        ),
                                )
                                Text(
                                    if (draft.background.isEmpty()) "—"
                                    else
                                        "${100-if(draft.dark)draft.overlayDark else draft.overlayLight}%",
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            AppearanceSlider(
                                (100 - if (draft.dark) draft.overlayDark else draft.overlayLight)
                                    .toFloat(),
                                {
                                    val overlay = 100 - it.toInt()
                                    snapshot =
                                        (if (draft.dark) draft.copy(overlayDark = overlay)
                                            else draft.copy(overlayLight = overlay))
                                            .json()
                                },
                                Modifier.semantics { contentDescription = "Background Opacity" },
                                enabled = !busy && draft.background.isNotEmpty(),
                            )
                        }
                        HorizontalDivider(
                            Modifier.padding(top = 4.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .22f),
                        )
                        ChoiceButton(
                            "Reset to Default Background",
                            outlined = false,
                            fontSize = 15.sp,
                            enabled = !busy,
                        ) {
                            snapshot = draft.copy(background = AppearanceSpec().background).json()
                        }
                        val current =
                            if (draft.background.isEmpty()) "No Background"
                            else backgroundNames[draft.background] ?: "My Background"
                        Text(
                            if (ready) "Current: $current" else "Current: Image Unavailable",
                            Modifier.align(Alignment.CenterHorizontally),
                            fontSize = 15.sp,
                        )
                        Surface(
                            onClick = { snapshot = draft.copy(background = "").json() },
                            modifier =
                                Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
                                    selected = draft.background.isEmpty()
                                },
                            color = MaterialTheme.colorScheme.background,
                            shape = RoundedCornerShape(12.dp),
                            border =
                                if (draft.background.isEmpty())
                                    BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
                                else null,
                        ) {
                            Text("No Background", Modifier.padding(14.dp), fontSize = 15.sp)
                        }
                        @Composable
                        fun tiles(entries: List<Pair<String, String>>) {
                            entries.chunked(2).forEach { row ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    row.forEach { (key, name) ->
                                        Surface(
                                            onClick = {
                                                if (!busy)
                                                    snapshot = draft.copy(background = key).json()
                                            },
                                            modifier =
                                                Modifier.weight(1f).height(145.dp).semantics {
                                                    contentDescription = name
                                                    selected = draft.background == key
                                                },
                                            shape = RoundedCornerShape(14.dp),
                                            border =
                                                if (draft.background == key)
                                                    BorderStroke(
                                                        3.dp,
                                                        MaterialTheme.colorScheme.primary,
                                                    )
                                                else null,
                                        ) {
                                            Box {
                                                OnKlavBackground(
                                                    Modifier.matchParentSize(),
                                                    draft.copy(
                                                        background = key,
                                                        overlayDark = 0,
                                                        overlayLight = 0,
                                                    ),
                                                    540,
                                                )
                                                Text(
                                                    name,
                                                    Modifier.align(Alignment.BottomStart)
                                                        .fillMaxWidth()
                                                        .background(Color(0xdd17202c))
                                                        .padding(9.dp),
                                                    color = Color.White,
                                                    fontSize = 14.sp,
                                                )
                                                if (key.startsWith("custom:"))
                                                    TextButton(
                                                        { deleting = key },
                                                        Modifier.align(Alignment.TopEnd)
                                                            .size(44.dp),
                                                        contentPadding = PaddingValues(0.dp),
                                                    ) {
                                                        Text(
                                                            "×",
                                                            fontSize = 22.sp,
                                                            color =
                                                                MaterialTheme.colorScheme.onSurface,
                                                            modifier =
                                                                Modifier.semantics {
                                                                    contentDescription =
                                                                        "Delete personal $name"
                                                                },
                                                        )
                                                    }
                                            }
                                        }
                                    }
                                    if (row.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                        tiles(backgroundNames.filterKeys { it.isNotEmpty() }.toList())
                        HorizontalDivider(
                            Modifier.padding(top = 6.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .22f),
                        )
                        Text("Your Backgrounds", fontSize = 18.sp)
                        val keys =
                            (personal +
                                    listOf(own, draft.background).filter {
                                        it.startsWith("custom:")
                                    })
                                .distinct()
                        if (keys.isEmpty())
                            Text(
                                "Save your own images here to use them again later.",
                                fontSize = 14.sp,
                            )
                        else tiles(keys.mapIndexed { index, key -> key to "Background ${index+1}" })
                    }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    ChoiceButton(
                        if (busy) "Preparing Image…"
                        else if (own.isEmpty()) "Choose Your Own Image" else "Choose Another Image",
                        enabled = !busy,
                        outlined = false,
                        fontSize = 15.sp,
                    ) {
                        runCatching { picker.launch(arrayOf("image/*")) }.onFailure {error="This phone could not open the image picker."}
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        @Composable
                        fun action(label: String, primary: Boolean, onClick: () -> Unit) {
                            Surface(
                                onClick,
                                Modifier.weight(1f).heightIn(min = 52.dp),
                                enabled = !busy && (!primary || ready),
                                shape = RoundedCornerShape(12.dp),
                                color =
                                    if (primary) sentColor(initial)
                                    else MaterialTheme.colorScheme.background,
                                contentColor =
                                    if (primary) sentInk(initial)
                                    else MaterialTheme.colorScheme.onSurface,
                                border =
                                    if (primary)
                                        BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                    else null,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(label, fontSize = 15.sp)
                                }
                            }
                        }
                        action("Cancel", false, dismiss)
                        action("Save", true) {
                            work {
                                withContext(Dispatchers.IO) { store.retainImage(draft.background) }
                                accept(draft)
                                dismiss()
                            }
                        }
                    }
                }
            }
        }
        deleting?.let { key ->
            ReferenceDialog(
                "Delete background?",
                { deleting = null },
                footer = {
                    DialogAction("Cancel", enabled = !busy) { deleting = null }
                    DialogAction("Delete", destructive = true, enabled = !busy) {
                        work {
                            withContext(Dispatchers.IO) { store.removeImage(key, appearance) }
                            if (own == key) own = ""
                            if (draft.background == key)
                                snapshot = draft.copy(background = "").json()
                            removed(key)
                            personal = store.gallery()
                            deleting = null
                        }
                    }
                },
            ) {
                Text(
                    "Remove this saved image from the app? Themes using it will keep their colors and use no background. Your original photo is not deleted.",
                    Modifier.padding(24.dp),
                    fontSize = 16.sp,
                )
            }
        }
        ErrorDialog(error) { error = null }
    }
}
