package app.onklav.home

import android.content.res.AssetManager
import org.json.JSONObject

/** Only engines bundled by the host are eligible. A signed game cannot supply an asset directory. */
internal class GameEngineBundle(val directory: String, descriptor: JSONObject) {
    private val version = descriptor.getString("version")
    val files: Map<String, String> = descriptor.getJSONObject("files_sha256").let { hashes ->
        hashes.keys().asSequence().associateWith { hashes.getString(it) }
    }

    init {
        require(files.isNotEmpty() && files.all { (name, hash) ->
            GamePackages.safe(name) && '/' !in name && hash.matches(Regex("[0-9a-f]{64}"))
        }) { "Invalid bundled game engine inventory" }
    }

    fun matches(manifest: JSONObject): Boolean {
        val required = manifest.optJSONObject("engine_assets") ?: return false
        return manifest.optString("engine") == version &&
            required.length() == files.size && files.all { (name, hash) -> required.optString(name) == hash }
    }

    fun asset(filename: String): String? = if (filename in files) "$directory/$filename" else null
}

internal class GameEngineRegistry(val primary: GameEngineBundle, private val retained: List<GameEngineBundle> = emptyList()) {
    fun select(manifest: JSONObject): GameEngineBundle =
        (listOf(primary) + retained).firstOrNull { it.matches(manifest) }
            ?: error("This game requires a different OnKlav engine bundle")

    companion object {
        fun load(assets: AssetManager): GameEngineRegistry {
            fun read(directory: String) = GameEngineBundle(directory, assets.open("$directory/engine.json")
                .bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) })
            val retained = assets.list("godot-web-compat").orEmpty().sorted().map { id ->
                require(id.matches(Regex("[a-zA-Z0-9_-]{1,64}"))) { "Invalid retained engine directory" }
                read("godot-web-compat/$id")
            }
            return GameEngineRegistry(read("godot-web"), retained)
        }
    }
}
