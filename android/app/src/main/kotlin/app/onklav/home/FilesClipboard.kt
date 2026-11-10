package app.onklav.home

import android.content.Context
import java.io.File
import java.nio.file.Files
import java.util.UUID
import org.json.JSONObject

internal data class FilesClipboard(val row: JSONObject, val android: Boolean, val move: Boolean) {
    val id get() = row.getString("id")
    val name get() = row.getString("name")
}

internal val androidPasteFolders = mapOf(
    "android-other" to "", "android-downloads" to "Download", "android-documents" to "Documents",
    "android-images" to "Pictures", "android-audio" to "Music", "android-video" to "Movies",
)

internal fun filesWritable(folder: String, trashed: Boolean) =
    !trashed && folder.isNotEmpty() && folder !in setOf("external", "trash", "android-trash", "system") &&
        !folder.startsWith("system-") &&
        (!folder.startsWith("android-") || folder in androidPasteFolders)

internal class FilesMoveIncomplete(cause: Exception) : IllegalStateException(
    "The copy is complete, but the original could not be moved to Trash. ${cause.message.orEmpty()}", cause,
)

internal class FilesTransfer(
    private val context: Context,
    private val destination: FileSelectionSession,
    private val authorize: () -> Unit,
) {
    private data class Item(val name: String, val mime: String, val file: File?, val children: List<Item> = emptyList(), val androidPath: String? = null)
    private val external = ExternalFiles(context, "app.onklav.files")
    private var count = 0
    private var bytes = 0L

    fun paste(clipboard: FilesClipboard, folder: String, android: Boolean, path: List<String>?) {
        authorize()
        if (clipboard.android) {
            val source = shared(clipboard.id)
            val item = inspectAndroid(source, clipboard.move, 0)
            if (android) {
                val target = shared(folder, destination = true)
                check(!target.toPath().startsWith(source.toPath())) { "Choose a folder outside the source." }
                if (clipboard.move) {
                    if (source.parentFile != target) publishAndroid(source, item, target)
                    return
                }
                writeAndroid(item, target)
            } else importItem(item)
            if (clipboard.move) finishMove {
                // Trash is recoverable and Android's broker moves the tree in one rename.
                external.run(JSONObject().put("operation", "trash").put("id", clipboard.id), authorize)
            }
            return
        }
        FileSelectionSession(context).use { source ->
            source.start()
            source.event("query", clipboard.name.take(80))
            val row = find(source, clipboard.id)
            check(!row.optBoolean("protected") && !row.optBoolean("trash")) { "This item is protected." }
            if (!android) {
                source.event("options:${clipboard.id}")
                source.event(if (clipboard.move) "move" else "copy")
                if (clipboard.move) {
                    // The signed controller keeps its move selection while opening folders.
                    for (id in requireNotNull(path)) openFolder(source, id)
                    source.event("destination.accept")
                } else {
                    source.navigate(requireNotNull(path))
                    source.event("paste.here")
                }
                return
            }
            val item = inspectOnKlav(source, row, 0)
            writeAndroid(item, shared(folder, destination = true))
            if (clipboard.move) finishMove {
                source.event("home")
                source.event("query", clipboard.name.take(80))
                find(source, clipboard.id)
                source.event("options:${clipboard.id}")
                source.event("trash")
                source.event("confirm.accept")
            }
        }
    }

    private fun finishMove(remove: () -> Unit) {
        try {
            authorize()
            remove()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            throw FilesMoveIncomplete(e)
        }
    }

    private fun shared(id: String, destination: Boolean = false): File {
        val path = external.sharedPath(id, destination)
        val file = AndroidFilesAccess.checked(context, path)
        check(path != "OnKlav Trash" && !path.startsWith("OnKlav Trash/")) { "Restore this item from Trash first." }
        if (destination) {
            check(!path.startsWith("Android/")&&!AndroidFilesAccess.appManaged(path)) { "Choose a personal Android folder." }
            if (id in androidPasteFolders && !file.exists()) check(file.mkdir())
            check(file.isDirectory && file.canWrite()) { "Choose a writable Android folder." }
        }
        return file
    }

    private fun budget(depth: Int, size: Long = 0) {
        authorize()
        bytes += size
        check(depth <= 24 && ++count <= 1000 && bytes <= 2L * 1024 * 1024 * 1024) {
            "Choose a smaller file or folder to transfer."
        }
    }

    private fun inspectAndroid(file: File, moving: Boolean, depth: Int): Item {
        val root = AndroidFilesAccess.root()
        val relative = file.relativeTo(root).invariantSeparatorsPath
        AndroidFilesAccess.checked(context, relative, root)
        val directory = file.isDirectory
        check(file.isFile || directory) { "This Android item is unavailable." }
        check(!moving || !AndroidFilesAccess.protected(relative, directory)) { "This item contains protected Android files." }
        budget(depth, if (directory) 0 else file.length())
        return if (directory) Item(file.name, "inode/directory", null,
            (file.listFiles() ?: error("Folder is unreadable.")).map { inspectAndroid(it, moving, depth + 1) })
        else Item(file.name, AndroidFilesAccess.mime(file), file, androidPath = relative)
    }

    private fun find(session: FileSelectionSession, id: String): JSONObject =
        findRow(session) { it.optString("id") == id }

    private fun findRow(session: FileSelectionSession, matches: (JSONObject) -> Boolean): JSONObject {
        while (true) {
            session.state.nodes("items").firstOrNull(matches)?.let { return it }
            check(session.state.optInt("offset") + 40 < session.state.optInt("total")) { "This file or folder is no longer available." }
            session.event("next")
        }
    }

    private fun openFolder(session: FileSelectionSession, id: String) {
        find(session, id)
        session.event("entry:$id")
    }

    private fun inspectOnKlav(session: FileSelectionSession, row: JSONObject, depth: Int): Item {
        budget(depth, row.optLong("bytes"))
        val name = row.getString("name")
        check(!row.optBoolean("protected") && !row.optBoolean("trash")) { "This folder contains protected items." }
        if (!row.optBoolean("folder")) {
            val descriptor = session.choose(row)
            return Item(name, descriptor.getString("mime"), File(descriptor.getString("path")))
        }
        val id = row.getString("id")
        val originalFolder = session.state.optString("folder")
        val originalQuery = session.state.optString("query")
        session.event("entry:$id")
        val children = buildList {
            while (true) {
                addAll(session.state.nodes("items"))
                if (session.state.optInt("offset") + 40 >= session.state.optInt("total")) break
                session.event("next")
            }
        }
        val items = children.map { child ->
            session.event("query")
            inspectOnKlav(session, find(session, child.getString("id")), depth + 1)
        }
        if (originalFolder.isEmpty()) session.event("home") else session.event("up")
        if (originalQuery.isNotEmpty()) session.event("query", originalQuery)
        return Item(name, "inode/directory", null, items)
    }

    private fun importItem(item: Item) {
        authorize()
        val name = FilesBroker.cleanName(item.name)
        if (item.file != null) {
            destination.importHere(readable(item), name, item.mime, authorize)
            return
        }
        destination.event("query")
        val siblings = buildSet {
            while (true) {
                addAll(destination.state.nodes("items").map { it.getString("name").lowercase() })
                if (destination.state.optInt("offset") + 40 >= destination.state.optInt("total")) break
                destination.event("next")
            }
        }
        val available = generateSequence(1) { it + 1 }.map {
            if (it == 1) name else name.take(165) + " ($it)"
        }.first { it.lowercase() !in siblings }
        destination.event("new.folder")
        destination.event("name", available)
        destination.event("name.save")
        destination.event("query")
        val id = findRow(destination) { it.optString("name") == available }.getString("id")
        destination.event("entry:$id")
        try {
            item.children.forEach(::importItem)
        } catch (e: Exception) {
            runCatching {
                destination.event("up")
                destination.event("query")
                find(destination, id)
                destination.event("options:$id")
                destination.event("trash")
                destination.event("confirm.accept")
            }
            throw e
        }
        destination.event("up")
        destination.event("query")
    }

    private fun readable(item: Item): File {
        authorize()
        return item.androidPath?.let { AndroidFilesAccess.checked(context, it) } ?: requireNotNull(item.file)
    }

    private fun writeAndroid(item: Item, directory: File) {
        val stage = File(directory, ".onklav-transfer-${UUID.randomUUID()}")
        fun write(node: Item, target: File) {
            authorize()
            if (node.file == null) {
                check(target.mkdir()) { "Could not create the Android folder." }
                node.children.forEach { write(it, File(target, FilesBroker.cleanName(it.name))) }
            } else {
                check(target.createNewFile()) { "Two files use the same name." }
                readable(node).inputStream().use { input ->
                    target.outputStream().use { output ->
                        FilesBroker.copyBoundedLimit(input, output, 2L * 1024 * 1024 * 1024, authorize)
                    }
                }
            }
        }
        try {
            write(item, stage)
            publishAndroid(stage, item, directory)
        } finally {
            if (stage.exists()) Files.walk(stage.toPath()).use { paths ->
                paths.sorted(java.util.Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }

    private fun publishAndroid(source: File, item: Item, directory: File) {
        val name = FilesBroker.cleanName(item.name)
        val dot = if (item.file == null) -1 else name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val extension = if (dot > 0) name.substring(dot) else ""
        for (number in 1..10000) {
            val candidate = File(directory, if (number == 1) name else "${stem.take(150)} ($number)$extension")
            authorize()
            try {
                Files.move(source.toPath(), candidate.toPath())
                return
            } catch (_: java.nio.file.FileAlreadyExistsException) {
                continue
            }
        }
        error("Too many files use that name.")
    }

}
