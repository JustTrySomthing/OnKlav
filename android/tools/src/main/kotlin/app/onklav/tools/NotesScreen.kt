package app.onklav.tools

import app.onklav.shared.OnKlavFileContracts

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.onklav.shared.*
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal fun boundedPlain(value: String, limit: Int): String {
    val clean = value.filter { !it.isISOControl() || it == '\n' || it == '\t' }.take(limit)
    return if (clean.lastOrNull()?.isHighSurrogate() == true) clean.dropLast(1) else clean
}

class NotesModel(application: Application) : AndroidViewModel(application) {
    private val core = Core(application)
    private val prefs = application.getSharedPreferences("notes", 0)
    private val writes = Mutex()
    private val persistedIds = mutableSetOf<String>()
    private val finalWrites = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var debounce: Job? = null
    var note by mutableStateOf<Note?>(null)
        private set

    var busy by mutableStateOf(true)
        private set

    var status by mutableStateOf("Opening…")
        private set

    var error by mutableStateOf<String?>(null)
    var records by mutableStateOf(emptyList<Note>())
        private set

    var pendingExport: Pair<String, ByteArray>? = null

    init {
        viewModelScope.launch {
            try {
                val id = prefs.getString("last", null)
                note =
                    if (
                        id == null || !java.io.File(application.filesDir, "notes/$id.json").exists()
                    )
                        core.create()
                    else core.load(id).also { persistedIds.add(it.id) }
                remember()
                status = "${note!!.text.length} / 100,000"
            } catch (e: Exception) {
                error = e.message
                status = "Could Not Open Notes"
            } finally {
                busy = false
            }
        }
    }

    private fun remember() {
        prefs.edit().putString("last", note?.id).apply()
    }

    private suspend fun save(snapshot: Note) {
        // A brand-new untouched editor need not create a blank library item, but
        // clearing an existing note must replace its old contents on disk.
        if (
            snapshot.id in persistedIds || snapshot.title.isNotBlank() || snapshot.text.isNotEmpty()
        ) {
            persistedIds.add(snapshot.id)
            core.save(snapshot)
        }
    }

    fun edit(title: String? = null, text: String? = null) {
        if (busy) return
        val n = note ?: return
        note =
            n.copy(
                title =
                    title?.let { boundedPlain(it.replace('\n', ' ').replace('\t', ' '), 80) }
                        ?: n.title,
                text = text?.let { boundedPlain(it, 100000) } ?: n.text,
            )
        status = "${note!!.text.length} / 100,000 · Saving…"
        debounce?.cancel()
        debounce =
            viewModelScope.launch {
                delay(500)
                persist()
            }
    }

    private suspend fun persist() {
        val snapshot = note ?: return
        writes.withLock {
            try {
                save(snapshot)
                if (note == snapshot) status = "${snapshot.text.length} / 100,000 · Saved"
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (note?.id == snapshot.id) {
                    error = e.message
                    status = "Not Saved · Export a Copy"
                }
            }
        }
    }

    fun flush() {
        debounce?.cancel()
        if (busy) return
        val snapshot = note ?: return
        finalWrites.launch(start = CoroutineStart.UNDISPATCHED) {
            writes.withLock {
                try {
                    save(snapshot)
                } catch (e: Exception) {
                    error = e.message
                    status = "Not Saved · Export a Copy"
                }
            }
        }
    }

    fun open(id: String? = null) {
        if (busy) return
        debounce?.cancel()
        busy = true
        viewModelScope.launch {
            writes.withLock {
                try {
                    note?.let { save(it) }
                    note =
                        if (id == null) core.create()
                        else core.load(id).also { persistedIds.add(it.id) }
                    remember()
                    status = "${note!!.text.length} / 100,000"
                } catch (e: Exception) {
                    error = e.message
                } finally {
                    busy = false
                }
            }
        }
    }

    fun list(query: String = "") {
        viewModelScope.launch {
            writes.withLock {
                try {
                    records = core.list(query)
                } catch (e: Exception) {
                    error = e.message
                }
            }
        }
    }

    fun delete(id: String) {
        if (busy) return
        debounce?.cancel()
        busy = true
        viewModelScope.launch {
            writes.withLock {
                try {
                    core.delete(id)
                    persistedIds.remove(id)
                    if (note?.id == id) {
                        note = core.create()
                        remember()
                        status = "0 / 100,000"
                    }
                    records = core.list()
                } catch (e: Exception) {
                    error = e.message
                } finally {
                    busy = false
                }
            }
        }
    }

    fun prepareExport(): String? {
        val n = note ?: return null
        pendingExport = n.label to (n.label + "\n\n" + n.text).toByteArray(Charsets.UTF_8)
        flush()
        return n.label.replace(Regex("[^\\p{L}\\p{N} ._-]"), "_").take(60) + ".txt"
    }

    fun export(uri: Uri?) {
        val captured = pendingExport
        pendingExport = null
        if (uri == null) return
        viewModelScope.launch {
            try {
                check(captured != null) { "Choose Export again to save this note" }
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use {
                        it.write(captured.second)
                    } ?: error("Could not open destination")
                }
                status = "Saved to Files"
            } catch (e: Exception) {
                error = e.message
            }
        }
    }
}

@Composable
fun NotesScreen(exit: () -> Unit) {
    val model: NotesModel = viewModel()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var library by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Note?>(null) }
    var editorValue by
        remember(model.note?.id) { mutableStateOf(TextFieldValue(model.note?.text.orEmpty())) }
    val exporter =
        rememberLauncherForActivityResult(
            OnKlavFileContracts.CreateDocument("text/plain"),
            model::export,
        )
    DisposableEffect(lifecycle, model) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) model.flush()
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose {
            lifecycle.lifecycle.removeObserver(observer)
            model.flush()
        }
    }
    BackHandler {
        if (library) library = false
        else {
            model.flush()
            exit()
        }
    }
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
            PageHeader(
                "Notes",
                leading = {
                    ClosePageButton {
                        model.flush()
                        exit()
                    }
                },
            ) {
                CompactAction(Icons.Outlined.FileDownload, "Export", model.note != null) {
                    exporting = true
                }
                Box { CompactAction(Icons.Outlined.Settings, "Notes Settings") { menu = true } }

                CompactAction(Icons.Outlined.FolderOpen, "Saved Notes") {
                    model.flush()
                    model.list()
                    library = true
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Column(Modifier.fillMaxSize()) {
                    val n = model.note
                    if (n != null)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp)) {
                            BasicTextField(
                                n.title,
                                { model.edit(title = it) },
                                enabled = !model.busy,
                                singleLine = true,
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                textStyle =
                                    MaterialTheme.typography.bodyLarge.copy(
                                        color = MaterialTheme.colorScheme.onBackground,
                                        fontSize = 20.sp,
                                    ),
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .padding(vertical = 12.dp),
                                decorationBox = { field ->
                                    if (n.title.isEmpty())
                                        Text(
                                            "Untitled Note",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 20.sp,
                                        )
                                    field()
                                },
                            )
                            BasicTextField(
                                editorValue,
                                { value ->
                                    val clean = boundedPlain(value.text, 100000)
                                    editorValue =
                                        if (clean == value.text) value
                                        else
                                            TextFieldValue(
                                                clean,
                                                TextRange(
                                                    value.selection.end.coerceAtMost(clean.length)
                                                ),
                                            )
                                    if (clean != model.note?.text) model.edit(text = clean)
                                },
                                enabled = !model.busy,
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                textStyle =
                                    MaterialTheme.typography.bodyLarge.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = LocalAppearanceSpec.current.size.sp,
                                        lineHeight = (LocalAppearanceSpec.current.size * 1.18f).sp,
                                    ),
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .weight(1f)
                                        .background(
                                            MaterialTheme.colorScheme.surface,
                                            RoundedCornerShape(14.dp),
                                        )
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                decorationBox = { field ->
                                    if (n.text.isEmpty())
                                        Text(
                                            "Write or Paste Your Note…",
                                            fontSize = LocalAppearanceSpec.current.size.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    field()
                                },
                            )
                        }
                    else
                        Box(
                            Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (model.busy) CircularProgressIndicator() else Text(model.status)
                        }
                }
                SavedNotesDrawer(library, model, { library = false }, { deleting = it })
            }
            Text(
                model.status,
                Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 6.dp),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (menu)
        NotesPopup("Notes", { menu = false }, footer = { NotesFooter("Close") { menu = false } }) {
            NotesMenuItem(
                text = { Text("Paste") },
                onClick = {
                    menu = false
                    val cb = context.getSystemService(ClipboardManager::class.java)
                    val clip = cb.primaryClip
                    if (clip != null && clip.itemCount > 0) {
                        val start = editorValue.selection.min
                        val end = editorValue.selection.max
                        val inserted =
                            boundedPlain(
                                clip.getItemAt(0).coerceToText(context).toString(),
                                100000 - editorValue.text.length + end - start,
                            )
                        val updated = editorValue.text.replaceRange(start, end, inserted)
                        editorValue = TextFieldValue(updated, TextRange(start + inserted.length))
                        model.edit(text = updated)
                    }
                },
            )
            NotesMenuItem(
                text = { Text("Copy Text") },
                onClick = {
                    menu = false
                    model.note?.let {
                        context
                            .getSystemService(ClipboardManager::class.java)
                            .setPrimaryClip(ClipData.newPlainText(it.label, it.text))
                    }
                },
            )
            NotesMenuItem(
                text = { Text("About Saved Notes") },
                onClick = {
                    menu = false
                    about = true
                },
            )
        }
    deleting?.let { n ->
        NotesMessagePopup(
            "Delete Saved Item?",
            n.label,
            { deleting = null },
            "Delete",
            true,
            "Cancel",
        ) {
            deleting = null
            model.delete(n.id)
        }
    }
    if (about)
        NotesMessagePopup(
            "Notes",
            "Saved privately on this phone. Use Download to save a copy in Files, or Share to send it.",
            { about = false },
            "Done",
            onPrimary = { about = false },
        )
    if (exporting)
        NotesPopup(
            "Export",
            { exporting = false },
            footer = { NotesFooter("Cancel") { exporting = false } },
        ) {
            Column {
                NotesChoice(
                    onClick = {
                        exporting = false
                        model.prepareExport()?.let(exporter::launch)
                    }
                ) {
                    Text("Save to Files")
                }
                NotesChoice(
                    onClick = {
                        exporting = false
                        val snapshot = model.note
                        if (snapshot != null)
                            scope.launch {
                                try {
                                    model.flush()
                                    val data =
                                        (snapshot.label + "\n\n" + snapshot.text).toByteArray(
                                            Charsets.UTF_8
                                        )
                                    val uri =
                                        withContext(Dispatchers.IO) {
                                            ExportProvider.stage(
                                                context,
                                                snapshot.label.take(60) + ".txt",
                                                data,
                                            )
                                        }
                                    val intent =
                                        Intent(Intent.ACTION_SEND)
                                            .setType("text/plain")
                                            .putExtra(Intent.EXTRA_STREAM, uri)
                                            .putExtra(
                                                Intent.EXTRA_TEXT,
                                                snapshot.label + "\n\n" + snapshot.text,
                                            )
                                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    intent.clipData = ClipData.newRawUri(snapshot.label, uri)
                                    context.startActivity(
                                        Intent.createChooser(intent, "Share " + snapshot.label)
                                    )
                                } catch (e: Exception) {
                                    model.error = e.message
                                }
                            }
                    }
                ) {
                    Text("Share")
                }
                NotesChoice(
                    onClick = {
                        exporting = false
                        model.note?.let { n ->
                            context
                                .getSystemService(ClipboardManager::class.java)
                                .setPrimaryClip(
                                    ClipData.newPlainText(n.label, n.label + "\n\n" + n.text)
                                )
                        }
                    }
                ) {
                    Text("Copy")
                }
            }
        }
    model.error?.let {
        NotesMessagePopup(
            "Could Not Complete",
            it,
            { model.error = null },
            "OK",
            onPrimary = { model.error = null },
        )
    }
}

@Composable
private fun SavedNotesDrawer(
    visible: Boolean,
    model: NotesModel,
    dismiss: () -> Unit,
    remove: (Note) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    AnimatedVisibility(
        visible,
        enter = androidx.compose.animation.fadeIn(tween(180)),
        exit = androidx.compose.animation.fadeOut(tween(180)),
    ) {
        Box(Modifier.fillMaxSize().background(Color(0x66000000)).clickable(onClick = dismiss))
    }
    AnimatedVisibility(
        visible,
        enter = slideInHorizontally(tween(420,easing=androidx.compose.animation.core.FastOutSlowInEasing)) { it },
        exit = slideOutHorizontally(tween(360,easing=androidx.compose.animation.core.FastOutSlowInEasing)) { it },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            Surface(
                Modifier.align(Alignment.TopEnd).width(minOf(maxWidth * .90f, 360.dp))
                    .fillMaxHeight()
                    .testTag("saved-notes-drawer")
                    .clickable(enabled = false) {}
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Saved Notes", Modifier.weight(1f), fontSize = 19.sp)
                        CompactAction(Icons.Outlined.Close, "Close Saved Notes", onClick = dismiss)
                    }
                    TextButton(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        onClick = {
                            dismiss()
                            model.open()
                        },
                    ) {
                        Text("+ New")
                    }
                    OutlinedTextField(
                        query,
                        { query = it },
                        placeholder = { Text("Search Saved Items") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    )
                    val shown =
                        model.records.filter {
                            it.label.contains(query, true) || it.text.contains(query, true)
                        }
                    LazyColumn(Modifier.weight(1f)) {
                        items(shown, key = { it.id }) { n ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(
                                    Modifier.weight(1f)
                                        .clickable {
                                            dismiss()
                                            model.open(n.id)
                                        }
                                        .padding(horizontal = 8.dp)
                                ) {
                                    Text(
                                        n.label,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        fontSize = 16.sp,
                                    )
                                    Text(
                                        DateFormat.getDateInstance().format(Date(n.updated)),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (n.text.isNotBlank())
                                        Text(
                                            n.text.replace('\n', ' '),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                }
                                CompactAction(Icons.Outlined.Close, "Delete ${n.label}") {
                                    remove(n)
                                }
                            }
                        }
                        if (shown.isEmpty())
                            item {
                                Text(
                                    if (query.isBlank()) "No Saved Items Yet"
                                    else "No Matching Items",
                                    Modifier.padding(16.dp),
                                )
                            }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotesPopup(
    title: String,
    dismiss: () -> Unit,
    footer: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    ReferenceDialog(title, dismiss, footer = footer, content = content)
}

@Composable
private fun NotesFooter(
    label: String,
    destructive: Boolean = false,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    DialogAction(label, destructive = destructive, primary = primary, onClick = onClick)
}

@Composable
private fun NotesChoice(onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Box(
            Modifier.heightIn(min = 60.dp).padding(horizontal = 28.dp, vertical = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            content()
        }
    }
}

@Composable
private fun NotesMenuItem(text: @Composable () -> Unit, onClick: () -> Unit) =
    NotesChoice(onClick, text)

@Composable
private fun NotesMessagePopup(
    title: String,
    message: String,
    dismiss: () -> Unit,
    primary: String,
    destructive: Boolean = false,
    secondary: String? = null,
    onPrimary: () -> Unit,
) {
    NotesPopup(
        title,
        dismiss,
        footer = {
            secondary?.let { NotesFooter(it, onClick = dismiss) }
            NotesFooter(primary, destructive = destructive, primary = true, onClick = onPrimary)
        },
    ) {
        Text(
            message,
            Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 8.dp),
            fontSize = 16.sp,
            lineHeight = 19.sp,
        )
    }
}
