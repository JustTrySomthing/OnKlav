package app.onklav.shared

import android.content.Context
import android.graphics.*
import android.media.ExifInterface
import android.util.AtomicFile
import android.util.LruCache
import java.io.*
import java.util.UUID
import org.json.JSONObject

class AppearanceStore(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("appearance_themes", 0)
    private val images = File(app.filesDir, "appearance-backgrounds").apply { mkdirs() }

    fun themes(): Map<String, AppearanceSpec> {
        val data = JSONObject(prefs.getString("saved", "{}") ?: "{}")
        return data
            .keys()
            .asSequence()
            .associateWith { AppearanceSpec.decode(data.getString(it)) }
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)
    }

    fun save(name: String, spec: AppearanceSpec, replace: Boolean = false) {
        require(name.isNotBlank() && name.length <= 40 && name.none { it.isISOControl() }) {
            "Enter a name up to 40 characters."
        }
        val all = themes().toMutableMap()
        val existing = all.keys.find { it.equals(name, true) }
        require(existing == null || replace) { "A theme with that name already exists." }
        require(existing != null || all.size < 40) {
            "You can save up to 40 themes. Delete one first."
        }
        existing?.let(all::remove)
        if (spec.background.startsWith("custom:"))
            require(file(spec.background).isFile) { "Background image is unavailable." }
        all[name] = spec
        val keys = retained()
        val extra = spec.background.takeIf { it.startsWith("custom:") }
        require(extra == null || extra in keys || keys.size < 100) {
            "You can keep up to 100 personal backgrounds."
        }
        write(all, if (extra == null) keys else keys + extra)
    }

    fun remove(name: String) {
        write(themes().filterKeys { it != name })
    }

    fun removeImage(key: String, appearance: Appearance) {
        val before = themes()
        val active = appearance.spec
        val target = file(key)
        val oldGallery = retained()
        write(
            before.mapValues { (_, spec) ->
                if (spec.background == key) spec.copy(background = "") else spec
            },
            oldGallery - key,
        )
        try {
            if (active.background == key) appearance.apply(active.copy(background = ""))
            check(!target.exists() || target.delete()) { "Could not remove image." }
        } catch (e: Exception) {
            write(before, oldGallery)
            if (appearance.spec != active) appearance.apply(active)
            throw e
        }
        synchronized(cache) { cache.evictAll() }
    }

    private fun write(all: Map<String, AppearanceSpec>, gallery: Set<String>? = null) {
        val data = JSONObject()
        all.forEach { (name, spec) -> data.put(name, spec.json()) }
        val edit = prefs.edit().putString("saved", data.toString())
        gallery?.let { edit.putStringSet("gallery", it) }
        check(edit.commit()) { "Could not save themes. Try again." }
    }

    private fun file(key: String): File {
        require(key.matches(Regex("custom:[a-f0-9]{32}"))) { "Unknown custom background." }
        return File(images, key.removePrefix("custom:") + ".jpg")
    }

    fun source(key: String): InputStream =
        if (key.startsWith("custom:")) file(key).inputStream()
        else {
            require(key.isNotEmpty() && key in backgroundNames) { "Unknown background." }
            app.assets.open("backgrounds/$key.webp")
        }

    private fun diskImages(): List<String> =
        images
            .listFiles()
            .orEmpty()
            .filter { it.name.matches(Regex("[a-f0-9]{32}\\.jpg")) }
            .sortedByDescending { it.lastModified() }
            .map { "custom:" + it.nameWithoutExtension }

    // A chosen photo is provisional until Backgrounds > Save or a named-theme save.
    // Existing 0.8.2 images are retained on first upgrade.
    private fun retained(): Set<String> {
        if (!prefs.contains("gallery")) {
            check(prefs.edit().putStringSet("gallery", diskImages().toSet()).commit())
        }
        return prefs.getStringSet("gallery", emptySet())!!.toSet()
    }

    fun gallery(): List<String> {
        val keys = retained()
        return diskImages().filter { it in keys }
    }

    fun retainImage(key: String) {
        if (!key.startsWith("custom:")) return
        require(file(key).isFile) { "Background image is unavailable." }
        val keys = retained()
        require(key in keys || keys.size < 100) { "You can keep up to 100 personal backgrounds." }
        check(prefs.edit().putStringSet("gallery", keys + key).commit()) {
            "Could not save background."
        }
    }

    fun importImage(bytes: ByteArray): String {
        require(gallery().size < 100) { "You can keep up to 100 personal backgrounds." }
        require(bytes.size <= 30 * 1024 * 1024) { "Choose an image smaller than 30 MB." }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        require(
            options.outWidth > 0 &&
                options.outHeight > 0 &&
                options.outWidth.toLong() * options.outHeight <= 100000000
        ) {
            "Choose a still image up to 100 megapixels."
        }
        options.inJustDecodeBounds = false
        options.inSampleSize = 1
        while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 2048) options
            .inSampleSize *= 2
        var bitmap =
            requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)) {
                "Could not decode this image."
            }
        try {
            val orientation =
                runCatching {
                        ExifInterface(ByteArrayInputStream(bytes))
                            .getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)
                    }
                    .getOrDefault(1)
            val matrix = Matrix()
            when (orientation) {
                2 -> matrix.setScale(-1f, 1f)
                3 -> matrix.setRotate(180f)
                4 -> matrix.setScale(1f, -1f)
                5 -> {
                    matrix.setRotate(90f)
                    matrix.postScale(-1f, 1f)
                }
                6 -> matrix.setRotate(90f)
                7 -> {
                    matrix.setRotate(-90f)
                    matrix.postScale(-1f, 1f)
                }
                8 -> matrix.setRotate(270f)
            }
            if (!matrix.isIdentity) {
                val corrected =
                    Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (corrected !== bitmap) bitmap.recycle()
                bitmap = corrected
            }
            if (bitmap.hasAlpha()) {
                val opaque =
                    Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
                Canvas(opaque).apply {
                    drawColor(Color.WHITE)
                    drawBitmap(bitmap, 0f, 0f, null)
                }
                bitmap.recycle()
                bitmap = opaque
            }
            val output = ByteArrayOutputStream()
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)) {
                "Could not save image."
            }
            require(output.size() <= ThemePackage.MAX_IMAGE) {
                "This image is too detailed. Choose a smaller one."
            }
            val key = "custom:" + UUID.randomUUID().toString().replace("-", "")
            val atomic = AtomicFile(file(key))
            val stream = atomic.startWrite()
            try {
                stream.write(output.toByteArray())
                atomic.finishWrite(stream)
            } catch (e: Exception) {
                atomic.failWrite(stream)
                throw e
            }
            return key
        } finally {
            bitmap.recycle()
        }
    }

    fun importTheme(bytes: ByteArray): Pair<String, AppearanceSpec> {
        val parsed = ThemePackage.read(bytes)
        val spec =
            parsed.image?.let { parsed.spec.copy(background = importImage(it)) } ?: parsed.spec
        return parsed.name to spec
    }

    /** A portable theme always includes its wallpaper, including built-in artwork. */
    fun export(name: String, spec: AppearanceSpec): ByteArray {
        val image =
            if (spec.background.isEmpty()) null
            else {
                val input =
                    requireNotNull(bitmap(spec.background, 2048)) {
                        "Background image is unavailable."
                    }
                var scaled = input
                var result: ByteArray
                var quality = 88
                try {
                    while (true) {
                        val bytes = ByteArrayOutputStream()
                        check(scaled.compress(Bitmap.CompressFormat.JPEG, quality, bytes))
                        result = bytes.toByteArray()
                        if (result.size <= 1024 * 1024) break
                        if (quality > 48) quality -= 10
                        else {
                            val smaller =
                                Bitmap.createScaledBitmap(
                                    scaled,
                                    (scaled.width * .8f).toInt().coerceAtLeast(1),
                                    (scaled.height * .8f).toInt().coerceAtLeast(1),
                                    true,
                                )
                            if (scaled !== input) scaled.recycle()
                            scaled = smaller
                        }
                    }
                    result
                } finally {
                    if (scaled !== input) scaled.recycle()
                }
            }
        return ThemePackage.pack(name, spec, image)
    }

    fun discardUnreferenced(keys: Collection<String>, active: AppearanceSpec) {
        val used = themes().values.map { it.background }.toSet() + active.background + retained()
        keys.filter { it.startsWith("custom:") && it !in used }.forEach { file(it).delete() }
    }

    fun bitmap(key: String, edge: Int): Bitmap? {
        require(edge in 1..4096) { "Invalid background size" }
        if (key.isEmpty()) return null
        val cacheKey = "${app.filesDir}:$key:$edge"
        synchronized(cache) {
            cache.get(cacheKey)?.let {
                return it
            }
        }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        source(key).use { BitmapFactory.decodeStream(it, null, options) }
        require(options.outWidth>0 && options.outHeight>0 && options.outWidth.toLong()*options.outHeight<=100_000_000) { "Background image is unavailable" }
        options.inSampleSize = 1
        while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > edge) options
            .inSampleSize *= 2
        options.inJustDecodeBounds = false
        options.inPreferredConfig = Bitmap.Config.RGB_565
        val bitmap =
            source(key).use { BitmapFactory.decodeStream(it, null, options) }
        require(options.outWidth>0 && options.outHeight>0 && options.outWidth.toLong()*options.outHeight<=100_000_000) { "Background image is unavailable" } ?: return null
        synchronized(cache) { cache.put(cacheKey, bitmap) }
        return bitmap
    }

    companion object {
        fun trimMemory() { synchronized(cache) { cache.evictAll() } }
        private val cache =
            object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
                override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
            }
    }
}
