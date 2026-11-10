package app.onklav.shared

import app.onklav.shared.OnKlavFileContracts

import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import kotlinx.coroutines.*

@Composable
fun AppearanceDialog(dismiss: () -> Unit) {
    val appearance = LocalAppearance.current
    val context = LocalContext.current
    val store = remember { AppearanceStore(context) }
    val scope = rememberCoroutineScope()
    var draftJson by rememberSaveable { mutableStateOf(appearance.spec.json()) }
    val draft = remember(draftJson) { AppearanceSpec.decode(draftJson) }
    fun edit(value: AppearanceSpec) {
        draftJson = value.json()
    }
    var page by rememberSaveable { mutableStateOf("appearance") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var help by remember { mutableStateOf(false) }
    var saveName by remember { mutableStateOf<String?>(null) }
    var replace by remember { mutableStateOf<Pair<String, AppearanceSpec>?>(null) }
    var backgrounds by rememberSaveable { mutableStateOf(false) }
    var exportHelp by rememberSaveable { mutableStateOf(false) }
    var importedKeys by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var deleting by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(store.themes()) }
    var pendingImport by rememberSaveable { mutableStateOf<String?>(null) }
    var importedName by rememberSaveable { mutableStateOf("") }
    var importedHasImage by rememberSaveable { mutableStateOf(false) }
    var transferOpen by rememberSaveable { mutableStateOf(false) }
    var transferStatus by rememberSaveable {
        mutableStateOf("Save a copy to Files or share it with another OnKlav user.")
    }
    var exportSpec by rememberSaveable { mutableStateOf("") }
    var exportName by rememberSaveable { mutableStateOf("My Theme") }
    fun job(action: suspend () -> Unit) {
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
    fun save(name: String, spec: AppearanceSpec, overwrite: Boolean = false) {
        job {
            withContext(Dispatchers.IO) { store.save(name.trim(), spec, overwrite) }
            saved = store.themes()
            saveName = null
            replace = null
            if (page == "import") {
                edit(spec)
                pendingImport = null
                page = "appearance"
            }
        }
    }
    val themePicker =
        rememberLauncherForActivityResult(OnKlavFileContracts.OpenDocument()) { uri ->
            if (uri != null)
                job {
                    val imported =
                        withContext(Dispatchers.IO) {
                            val bytes =
                                context.contentResolver.openInputStream(uri)?.use {
                                    ThemePackage.bounded(it, ThemePackage.MAX_ARCHIVE)
                                } ?: error("Could not open theme.")
                            store.importTheme(bytes)
                        }
                    pendingImport?.let {
                        store.discardUnreferenced(
                            listOf(AppearanceSpec.decode(it).background),
                            appearance.spec,
                        )
                    }
                    importedKeys =
                        ArrayList(
                            importedKeys +
                                listOf(imported.second.background).filter {
                                    it.startsWith("custom:")
                                }
                        )
                    importedHasImage = imported.second.background.startsWith("custom:")
                    importedName = imported.first
                    pendingImport = imported.second.json()
                    page = "import"
                }
        }
    val exporter =
        rememberLauncherForActivityResult(
            OnKlavFileContracts.CreateDocument("application/octet-stream")
        ) { uri ->
            if (uri != null && exportSpec.isNotEmpty())
                job {
                    val snapshot = AppearanceSpec.decode(exportSpec)
                    withContext(Dispatchers.IO) {
                        val bytes = store.export(exportName, snapshot)
                        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                            ?: error("Could not write theme file.")
                    }
                    transferStatus =
                        "File saved. You can share it from Files or use Share File here."
                }
        }
    val textExporter =
        rememberLauncherForActivityResult(OnKlavFileContracts.CreateDocument("text/plain")) {
            uri ->
            if (uri != null)
                job {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri, "wt")?.use {
                            it.write(appearanceHelp.toByteArray(Charsets.UTF_8))
                        } ?: error("Could not write help file.")
                    }
                    transferStatus =
                        "File saved. You can share it from Files or use Share File here."
                }
        }
    DisposableEffect(Unit) {
        onDispose {
            var c = context
            while (c is android.content.ContextWrapper && c !is android.app.Activity) c =
                c.baseContext
            if ((c as? android.app.Activity)?.isChangingConfigurations != true)
                store.discardUnreferenced(importedKeys, appearance.spec)
        }
    }
    fun export(name: String, spec: AppearanceSpec) {
        exportName = name
        exportSpec = spec.json()
        runCatching { exporter.launch(name.replace(Regex("[^\\p{L}\\p{N} ._-]"), "_").take(60) + ".onklav-t") }.onFailure { error="This phone could not open the file picker." }
    }
    fun transfer(name: String, spec: AppearanceSpec) {
        exportHelp = false
        exportName = name
        exportSpec = spec.json()
        transferStatus = "Save a copy to Files or share it with another OnKlav user."
        page = "appearance"
        transferOpen = true
    }
    fun share(name: String, spec: AppearanceSpec) {
        job {
            val uri =
                withContext(Dispatchers.IO) {
                    ExportProvider.stage(
                        context,
                        if (exportHelp) "$name.txt" else "$name.onklav-t",
                        if (exportHelp) appearanceHelp.toByteArray(Charsets.UTF_8)
                        else store.export(name, spec),
                        if (exportHelp) "txt" else "onklav-t",
                    )
                }
            val intent =
                Intent(Intent.ACTION_SEND)
                    .setType(if (exportHelp) "text/plain" else "application/octet-stream")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.clipData = ClipData.newRawUri("OnKlav Theme", uri)
            context.startActivity(Intent.createChooser(intent, "Share $name"))
        }
    }
    fun back() {
        if (busy) return
        if (page == "appearance") {
            if (!busy) dismiss()
        } else if (page == "import") {
            pendingImport?.let {
                store.discardUnreferenced(
                    listOf(AppearanceSpec.decode(it).background),
                    appearance.spec,
                )
            }
            pendingImport = null
            page = "appearance"
        } else page = "appearance"
    }
    @Composable
    fun editorPage(which: String) {
        ReferenceDialog(
            when (which) {
                "themes" -> "Load & Save Themes"
                "backgrounds" -> "Backgrounds & Overlay"
                "fonts" -> "Font Style"
                "import" -> "Import Theme"
                else -> "Appearance"
            },
            ::back,
            large = which != "fonts" && which != "import",
            covered =
                (which == "appearance" &&
                    (page != "appearance" || backgrounds || help || transferOpen)) ||
                    saveName != null ||
                    replace != null ||
                    deleting != null ||
                    error != null,
            heading =
                if (which == "appearance") {
                    {
                        Surface(
                            onClick = { edit(draft.copy(dark = !draft.dark)) },
                            enabled = !busy,
                            modifier =
                                Modifier.heightIn(min = 48.dp).semantics {
                                    contentDescription =
                                        if (draft.dark) "Dark. Switch to Light"
                                        else "Light. Switch to Dark"
                                    selected = draft.dark
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.background,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant),
                        ) {
                            Text(
                                if (draft.dark) "Dark" else "Light",
                                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                fontSize = 14.sp,
                                lineHeight = 18.sp,
                            )
                        }
                        Surface(
                            onClick = { help = true },
                            enabled = !busy,
                            modifier =
                                Modifier.size(48.dp).semantics {
                                    contentDescription = "Appearance Help"
                                },
                            color = MaterialTheme.colorScheme.background,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    "?",
                                    fontSize = 21.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else null,
            footer = {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                if (which == "appearance") {
                    DialogAction("Cancel", enabled = !busy, onClick = dismiss)
                    DialogAction("Apply", primary = true, enabled = !busy) {
                        job {
                            withContext(Dispatchers.IO) {
                                if (draft.background.startsWith("custom:"))
                                    store.source(draft.background).close()
                            }
                            appearance.apply(draft)
                            dismiss()
                        }
                    }
                } else if (which == "import") {
                    DialogAction("Close", enabled = !busy, onClick = ::back)
                    DialogAction(
                        "Import",
                        primary = true,
                        enabled = !busy && pendingImport != null,
                    ) {
                        val spec = AppearanceSpec.decode(requireNotNull(pendingImport))
                        val existing = saved.keys.find { it.equals(importedName, true) }
                        if (existing != null) replace = importedName to spec
                        else save(importedName, spec)
                    }
                } else
                    DialogAction(
                        if (which == "fonts") "Cancel" else "Close",
                        enabled = !busy,
                        onClick = ::back,
                    )
            },
        ) {
            Column(
                Modifier.padding(
                    start = if (which == "fonts") 0.dp else if (which == "themes") 16.dp else 20.dp,
                    end = if (which == "fonts") 0.dp else if (which == "themes") 16.dp else 20.dp,
                    top = 8.dp,
                    bottom = 12.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(if (which == "fonts") 0.dp else 10.dp),
            ) {
                when (which) {
                    "appearance" -> {
                        Column {
                            MessagePreview(draft)
                            val index = fontSizes.indexOf(draft.size).coerceAtLeast(0)
                            val sizeNames = listOf("Small", "Default", "Large", "Larger", "Largest")
                            Row(
                                Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Font Size", fontSize = 15.sp)
                                AppearanceSlider(
                                    index.toFloat(),
                                    {
                                        edit(
                                            draft.copy(size = fontSizes[it.toInt().coerceIn(0, 4)])
                                        )
                                    },
                                    enabled = !busy,
                                    range = 0f..4f,
                                    steps = 3,
                                    modifier =
                                        Modifier.weight(1f)
                                            .height(52.dp)
                                            .padding(start = 8.dp)
                                            .semantics {
                                                contentDescription = "Font Size"
                                                stateDescription = sizeNames[index]
                                            },
                                )
                            }
                            ChoiceButton(
                                "Font Style · ${fontNames[draft.font]}",
                                modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
                                minimumHeight = 48.dp,
                                enabled = !busy,
                            ) {
                                page = "fonts"
                            }
                            AppearanceDivider()
                            Text(
                                "Message Color",
                                Modifier.padding(vertical = 8.dp),
                                fontSize = 17.sp,
                                lineHeight = 23.sp,
                            )
                            BoxWithConstraints {
                                val columns = (maxWidth / 48.dp).toInt().coerceIn(1, 5)
                                Column {
                                    paletteOrder.chunked(columns).forEach { row ->
                                        Row(Modifier.fillMaxWidth()) {
                                            row.forEach { id ->
                                                Box(
                                                    Modifier.weight(1f)
                                                        .height(48.dp)
                                                        .semantics {
                                                            contentDescription = paletteNames[id]
                                                            selected = draft.palette == id
                                                        }
                                                        .clickable(enabled = !busy) {
                                                            edit(draft.copy(palette = id))
                                                        },
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Box(
                                                        Modifier.size(30.dp)
                                                            .border(
                                                                2.dp,
                                                                if (id == draft.palette)
                                                                    MaterialTheme.colorScheme
                                                                        .onSurface
                                                                else Color.Transparent,
                                                                CircleShape,
                                                            ),
                                                        contentAlignment = Alignment.Center,
                                                    ) {
                                                        Box(
                                                            Modifier.size(24.dp)
                                                                .clip(CircleShape)
                                                                .background(swatchBrush(draft, id))
                                                        )
                                                    }
                                                }
                                            }
                                            repeat(columns - row.size) {
                                                Spacer(Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }
                            AppearanceDivider()
                            Row(
                                Modifier.fillMaxWidth()
                                    .heightIn(min = 52.dp)
                                    .toggleable(
                                        value = draft.gradient,
                                        enabled = !busy,
                                        role = Role.Switch,
                                        onValueChange = { edit(draft.copy(gradient = it)) },
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Gradient Bubbles", Modifier.weight(1f), fontSize = 16.sp)
                                AppearanceSwitch(draft.gradient, enabled = !busy)
                            }
                            AppearanceDivider()
                            ChoiceButton(
                                "Backgrounds & Overlay",
                                modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
                                enabled = !busy,
                            ) {
                                backgrounds = true
                            }
                            ChoiceButton(
                                "Load & Save Themes",
                                modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
                                enabled = !busy,
                            ) {
                                page = "themes"
                            }
                        }
                    }
                    "fonts" ->
                        fontNames.forEachIndexed { i, name ->
                            OnKlavTheme(appearance, preview = appearance.spec.copy(font = i)) {
                                TextButton(
                                    onClick = {
                                        edit(draft.copy(font = i))
                                        page = "appearance"
                                    },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                                    contentPadding =
                                        PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                                ) {
                                    Text(
                                        (if (draft.font == i) "✓  " else "") + name,
                                        Modifier.fillMaxWidth(),
                                        fontSize = 18.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    "themes" -> {
                        ChoiceButton("Save Current Theme", enabled = !busy) { saveName = "" }
                        ChoiceButton("Restore Default Theme", enabled = !busy) {
                            edit(AppearanceSpec())
                            page = "appearance"
                        }
                        if (saved.isNotEmpty()) {
                            AppearanceDivider()
                            Text("Your Themes", fontSize = 18.sp)
                        }
                        @Composable
                        fun card(name: String, spec: AppearanceSpec, personalTheme: Boolean) {
                            Surface(
                                color = MaterialTheme.colorScheme.background,
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Column(
                                    Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            name + " · " + if (spec.dark) "Dark" else "Light",
                                            Modifier.weight(1f),
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        if (personalTheme)
                                            CompactAction(
                                                Icons.Outlined.Close,
                                                "Delete Theme: $name",
                                                !busy,
                                            ) {
                                                deleting = name
                                            }
                                    }
                                    MessagePreview(spec, compact = true)
                                    ChoiceButton(
                                        "Load Theme",
                                        modifier =
                                            Modifier.semantics {
                                                contentDescription = "Load Theme: $name"
                                            },
                                        enabled = !busy,
                                    ) {
                                        edit(spec)
                                        page = "appearance"
                                    }
                                    ChoiceButton("Export or Share", enabled = !busy) {
                                        transfer(name, spec)
                                    }
                                }
                            }
                        }
                        saved.forEach { (name, spec) -> card(name, spec, true) }
                        AppearanceDivider()
                        Text("Preset Themes", fontSize = 18.sp)
                        (presetThemes.drop(12).reversed() + presetThemes.take(12)).forEach {
                            (name, spec) ->
                            card(name, spec, false)
                        }
                        AppearanceDivider()
                        ChoiceButton("Import Theme File", enabled = !busy) {
                            page = "import"
                            runCatching { themePicker.launch(arrayOf("*/*")) }.onFailure {error="This phone could not open the file picker."}
                        }
                        ChoiceButton("Export Current Theme", enabled = !busy) {
                            transfer("My Theme", draft)
                        }
                        ChoiceButton("Download Theme Template", enabled = !busy) {
                            transfer("OnKlav Theme Template", draft)
                            export("OnKlav Theme Template", draft)
                        }
                    }
                    "import" -> {
                        Text(
                            if (pendingImport == null)
                                "Choose a theme to check and preview. Your appearance stays unchanged until you import and apply it."
                            else
                                "Checked and ready to import. Tap Apply in Appearance when you are happy with the preview.",
                            fontSize = 16.sp,
                        )
                        ChoiceButton(
                            if (pendingImport == null) "Choose Theme File"
                            else "Choose Another File",
                            enabled = !busy,
                        ) {
                            runCatching { themePicker.launch(arrayOf("*/*")) }.onFailure {error="This phone could not open the file picker."}
                        }
                        pendingImport?.let {
                            val spec = AppearanceSpec.decode(it)
                            Text(
                                "$importedName\n${if(spec.dark) "Dark" else "Light"} · ${paletteNames[spec.palette]} · text size ${spec.size} · ${fontNames[spec.font]}\n${if(importedHasImage) "Includes Background Image" else "No Included Image"}",
                                fontSize = 16.sp,
                            )
                            MessagePreview(spec, compact = true)
                        }
                    }
                }
            }
        }
    }
    editorPage("appearance")
    if (page != "appearance") editorPage(page)
    if (backgrounds)
        BackgroundDialog(
            draft,
            { chosen ->
                edit(
                    draft.copy(
                        background = chosen.background,
                        overlayDark = chosen.overlayDark,
                        overlayLight = chosen.overlayLight,
                    )
                )
            },
            { key ->
                if (draft.background == key) edit(draft.copy(background = ""))
                saved = store.themes()
            },
            { backgrounds = false },
        )
    if (transferOpen)
        ReferenceDialog(
            "Export or Share",
            { if (!busy) transferOpen = false },
            footer = { DialogAction("Close", enabled = !busy) { transferOpen = false } },
        ) {
            Column(
                Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(transferStatus, fontSize = 16.sp)
                ChoiceButton("Save File", enabled = !busy) {
                    if (exportHelp) runCatching { textExporter.launch("OnKlav-Appearance-Help.txt") }.onFailure {error="This phone could not open the file picker."}
                    else export(exportName, AppearanceSpec.decode(exportSpec))
                }
                ChoiceButton("Share File", enabled = !busy) {
                    share(exportName, AppearanceSpec.decode(exportSpec))
                }
            }
        }
    saveName?.let { name ->
        ReferenceDialog(
            "Save Theme",
            { if (!busy) saveName = null },
            footer = {
                DialogAction("Cancel", enabled = !busy) { saveName = null }
                DialogAction("Save", primary = true, enabled = !busy && name.trim().isNotEmpty()) {
                    val existing = saved.keys.find { it.equals(name.trim(), true) }
                    if (existing != null) replace = name.trim() to draft
                    else save(name.trim(), draft)
                }
            },
        ) {
            OutlinedTextField(
                name,
                { saveName = it.filterNot(Char::isISOControl).take(40) },
                Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth(),
                singleLine = true,
                label = { Text("Theme Name") },
                shape = RoundedCornerShape(10.dp),
            )
        }
    }
    replace?.let { (name, spec) ->
        ReferenceDialog(
            "Replace Saved Theme?",
            { replace = null },
            footer = {
                DialogAction("Cancel", enabled = !busy) { replace = null }
                DialogAction("Replace", primary = true, enabled = !busy) { save(name, spec, true) }
            },
        ) {
            Text(
                "Replace “$name” with these appearance settings?",
                Modifier.padding(24.dp),
                fontSize = 16.sp,
            )
        }
    }
    deleting?.let { name ->
        ReferenceDialog(
            "Delete Theme?",
            { deleting = null },
            footer = {
                DialogAction("Cancel", enabled = !busy) { deleting = null }
                DialogAction("Delete", destructive = true, enabled = !busy) {
                    job {
                        withContext(Dispatchers.IO) { store.remove(name) }
                        saved = store.themes()
                        deleting = null
                    }
                }
            },
        ) {
            Text(
                "Permanently delete “$name”? This cannot be undone.",
                Modifier.padding(24.dp),
                fontSize = 16.sp,
            )
        }
    }
    if (help)
        ReferenceDialog(
            "Appearance Help",
            { help = false },
            large = true,
            footer = {
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        DialogAction("Copy All") {
                            val clipboard =
                                context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                    as android.content.ClipboardManager
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText("Appearance Help", appearanceHelp)
                            )
                            android.widget.Toast.makeText(
                                    context,
                                    "Help copied",
                                    android.widget.Toast.LENGTH_SHORT,
                                )
                                .show()
                        }
                        DialogAction("Save Text File", primary = true) {
                            help = false
                            exportHelp = true
                            exportName = "OnKlav Appearance Help"
                            exportSpec = draft.json()
                            transferStatus = "Save a copy to Files or share it."
                            transferOpen = true
                            runCatching { textExporter.launch("OnKlav-Appearance-Help.txt") }.onFailure {error="This phone could not open the file picker."}
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        DialogAction("Close") { help = false }
                    }
                }
            },
        ) {
            SelectionContainer {
                Text(
                    appearanceHelp,
                    Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                )
            }
        }
    ErrorDialog(error) { error = null }
}

@Composable
private fun AppearanceDivider() {
    HorizontalDivider(
        Modifier.padding(top = 18.dp, bottom = 14.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .22f),
    )
}
