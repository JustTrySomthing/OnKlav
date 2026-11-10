package app.onklav.home

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.zip.ZipFile

/** User-installed, signed web content. Signatures identify updates; there is no owner allowlist.
 * Verification never executes content. WebGameActivity is the only external game runtime.
 */
class GamePackages internal constructor(private val root: File, private val engines: GameEngineRegistry) {
    constructor(root: File, engine: JSONObject) : this(root, GameEngineRegistry(GameEngineBundle("godot-web", engine)))

    internal fun engineFor(game: Game): GameEngineBundle = engines.select(game.manifest)
    data class Game(val manifest: JSONObject, val publisher: String, val hash: String) {
        init { require(hash.matches(Regex("[0-9a-f]{64}")) && publisher.matches(Regex("[0-9a-f]{64}"))) }
        val id: String get() = manifest.getString("id")
        val name: String get() = manifest.getString("name")
        val versionCode: Int get() = manifest.getInt("version_code")
        val origin: String get() = "https://g-" + sha((id+":"+publisher).toByteArray()) + ".onklav.invalid"
    }
    init { require(root.isDirectory || root.mkdirs()) { "Could not open game storage" } }
    private fun record(id: String) = AtomicFile(File(root,sha(id.toByteArray())+".json"))
    private fun state(id: String): JSONObject? {
        val f=record(id)
        if(!f.baseFile.exists() && !File(f.baseFile.path+".bak").exists())return null
        return f.openRead().use { input -> require(f.baseFile.length()<=32768);JSONObject(input.readBytes().toString(Charsets.UTF_8)) }
    }
    fun installed(): List<Game> = synchronized(lock) {
        root.listFiles().orEmpty().filter { it.extension=="json" || it.name.endsWith(".json.bak") }
            .map { it.name.removeSuffix(".bak") }.distinct().sorted().mapNotNull { name ->
                val f=AtomicFile(File(root,name));val j=f.openRead().use { input -> require(f.baseFile.length()<=32768);JSONObject(input.readBytes().toString(Charsets.UTF_8)) }
                if(!j.getBoolean("enabled"))null else Game(j.getJSONObject("manifest"),j.getString("publisher"),j.getString("hash"))
            }
    }
    fun inspect(file: File): Game {
        require(file.length() in 1..MAX_PACKAGE) { "Game package exceeds 64 MB" }
        ZipFile(file).use { zip ->
            val entries=zip.entries().toList();val names=entries.map { it.name }
            require(entries.size in 5..128 && names.distinct().size==names.size && entries.all { safe(it.name) && !it.isDirectory && it.size in 0..MAX_ENTRY } && entries.sumOf { it.size }<=MAX_TOTAL) { "Invalid or oversized game archive" }
            require(setOf("manifest.json","publisher.json","signature.bin","web/index.html","web/index.pck").all { it in names }) { "Incomplete game package" }
            fun small(name:String,limit:Long):ByteArray { val e=zip.getEntry(name);require(e.size<=limit);val out=java.io.ByteArrayOutputStream();zip.getInputStream(e).use { copyBounded(it,out,limit) };return out.toByteArray() }
            val m=JSONObject(small("manifest.json",16384).toString(Charsets.UTF_8))
            require(m.getString("schema")=="onklav.package.v1" && m.getString("kind")=="game" && m.getString("runtime")=="godot.web.v1" && m.getString("entry")=="web/index.html") { "Choose an OnKlav web game" }
            val shared = engines.select(m).files
            val id=m.getString("id")
            require(id.length<=160 && id.matches(Regex("[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)+"))) { "Invalid game ID" }
            require(m.getString("profile") in setOf("arcade","turn_based") && m.getString("orientation") in setOf("portrait","landscape","sensor") && m.getJSONArray("permissions").length()==0)
            for(k in listOf("name","version","description")){val s=m.getString(k);require(s.isNotBlank() && s.length<=200 && s.none { it.code<32 })}
            val minimumHost=m.opt("min_host")
            val modes=if(m.has("play_modes")) m.optJSONArray("play_modes")
                ?: throw IllegalArgumentException("Unsupported game play modes")
            else org.json.JSONArray().put("single")
            require(modes.toString()in setOf("[\"single\"]","[\"single\",\"turn_sms\"]") && (modes.length()==1 || m.optString("profile")=="turn_based")) { "Unsupported game play modes" }
            require(m.optString("controls")=="onklav.game.controls.v2" && minimumHost is Number && minimumHost.toDouble()==minimumHost.toInt().toDouble() && minimumHost.toInt() in 155..BuildConfig.VERSION_CODE) { "Update the game SDK and OnKlav for standard game controls" }
            val code=m.get("version_code");require(code is Number && code.toDouble()==code.toInt().toDouble() && code.toInt()>0)
            val raw=small("publisher.json",32768);val pub=JSONObject(raw.toString(Charsets.UTF_8))
            require(pub.getString("algorithm")=="ECDSA-P256-SHA256")
            val public=unhex(pub.getString("public_key"));require(public.size==91)
            val signature=small("signature.bin",80);require(signature.size in 64..80)
            val key=KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(public)) as java.security.interfaces.ECPublicKey
            val expected=java.security.AlgorithmParameters.getInstance("EC").apply{init(java.security.spec.ECGenParameterSpec("secp256r1"))}.getParameterSpec(java.security.spec.ECParameterSpec::class.java)
            require(key.params.curve==expected.curve && key.params.generator==expected.generator && key.params.order==expected.order && key.params.cofactor==expected.cofactor)
            val verifier=Signature.getInstance("SHA256withECDSA");verifier.initVerify(key);verifier.update("onklav.game.signature.v1\u0000".toByteArray()+raw)
            require(verifier.verify(signature)) { "Game publisher signature is invalid" }
            val inventory=pub.getJSONObject("files_sha256")
            require(inventory.keySet()==names.toSet()-setOf("publisher.json","signature.bin")) { "Unsigned game content" }
            var expanded=0L
            for(name in inventory.keySet()) {
                require(name=="manifest.json" || (name.startsWith("web/") && name.removePrefix("web/") !in shared.keys)) { "Game cannot replace the shared engine" }
                require(inventory.getString(name).matches(Regex("[0-9a-f]{64}")))
                val actual=zip.getInputStream(zip.getEntry(name)).use { rawInput ->
                    val counted=object:java.io.FilterInputStream(rawInput){
                        override fun read(bytes:ByteArray,offset:Int,length:Int):Int {
                            val n=super.read(bytes,offset,length)
                            if(n>0){expanded+=n;require(expanded<=MAX_TOTAL){"Expanded game content exceeds limits"}}
                            return n
                        }
                    }
                    hashStream(counted,MAX_ENTRY)
                }
                require(actual==inventory.getString(name)) { "Game content changed: $name" }
            }
            return Game(m,sha(public),hash(file))
        }
    }
    fun install(file: File): Game = synchronized(lock) {
        val game=inspect(file);val old=state(game.id)
        require(game.id != LAST_LIGHT_ID) { "This game is delivered with OnKlav" }
        if (game.id == SHROOM_ID) {
            require(game.publisher == SHROOM_PUBLISHER) { "This game ID belongs to the included Shroom publisher" }
        }
        if(old!=null) {
            require(old.getString("publisher")==game.publisher) { "This game ID belongs to a different publisher" }
            require(game.versionCode>=old.getInt("highest_version_code")) { "An older game version cannot replace a newer one" }
            require(game.versionCode!=old.getInt("highest_version_code") || old.getString("hash")==game.hash) { "Publisher must increase version_code when changing a game" }
        }
        val records=root.listFiles().orEmpty().count { it.extension=="json" }
        require(old!=null || records<100) { "Game library is full" }
        val blob=File(root,game.hash+".onklav-g")
        if(!blob.exists()) {
            require(root.listFiles().orEmpty().filter { it.extension=="onklav-g" }.sumOf { it.length() }+file.length()<=1024L*1024*1024) { "Game storage is full. Remove unused games first." }
            val tmp=File.createTempFile("install-",".tmp",root)
            try { file.inputStream().use { input -> tmp.outputStream().use { out -> copyBounded(input,out,MAX_PACKAGE) } };require(hash(tmp)==game.hash);require(tmp.renameTo(blob)) { "Could not store game" } }
            finally { tmp.delete() }
        } else require(hash(blob)==game.hash) { "Stored game is damaged" }
        val next=JSONObject().put("enabled",true).put("manifest",game.manifest).put("publisher",game.publisher).put("hash",game.hash).put("highest_version_code",game.versionCode)
        write(record(game.id),next)
        // Unlink older immutable packages only after the new record commits. An open runtime ZipFile
        // keeps its old inode until close, so installing an update cannot change a running match.
        if(old!=null && old.getString("hash")!=game.hash)File(root,old.getString("hash")+".onklav-g").delete()
        catalogEpoch.value++
        game
    }
    fun remove(id:String) = synchronized(lock) {
        val old=state(id) ?: error("Game is not installed")
        old.put("enabled",false);write(record(id),old)
        File(root,old.getString("hash")+".onklav-g").delete()
        catalogEpoch.value++
        // Publisher/version tombstones remain; web saves remain until explicit save removal.
    }
    fun open(id:String): Pair<Game,ZipFile> = synchronized(lock) {
        val row=state(id) ?: error("Game is not installed");require(row.getBoolean("enabled"))
        val file=File(root,row.getString("hash")+".onklav-g");val game=inspect(file)
        require(game.id==id && game.publisher==row.getString("publisher") && game.hash==row.getString("hash"))
        game to ZipFile(file)
    }
    /** Load the APK-owned game while retaining the standalone game’s save origin. */
    fun openIncluded(context: Context, id: String): Pair<Game, ZipFile> {
        val (filename, publisher) = when (id) {
            SHROOM_ID -> "shroom.zip" to SHROOM_PUBLISHER
            LAST_LIGHT_ID -> "last-light.zip" to LAST_LIGHT_PUBLISHER
            else -> error("Unknown included game")
        }
        val staged = File.createTempFile("included-game-", ".zip", context.cacheDir)
        try {
            context.assets.open("included-games/$filename").use { input ->
                staged.outputStream().use { output -> copyBounded(input, output, MAX_PACKAGE) }
            }
            // This path only reads a fixed asset authenticated by Android’s APK signer.
            // User imports still go through inspect() and must carry a publisher signature.
            val manifest = ZipFile(staged).use { zip ->
                require(zip.getEntry("web/index.html") != null && zip.getEntry("web/index.pck") != null)
                zip.getInputStream(zip.getEntry("manifest.json")).bufferedReader(Charsets.UTF_8).use {
                    JSONObject(it.readText())
                }
            }
            val included = Game(manifest, publisher, hash(staged))
            require(included.id == id)
            return synchronized(lock) {
                val installed = state(id)
                if (id == SHROOM_ID && installed?.getBoolean("enabled") == true &&
                    installed.getString("publisher") == SHROOM_PUBLISHER &&
                    installed.getInt("highest_version_code") > included.versionCode
                ) open(id) else included to ZipFile(staged)
            }
        } finally {
            // ZipFile retains the open descriptor after unlink on Android.
            staged.delete()
        }
    }

    fun packageFile(id:String):File = synchronized(lock) {
        val row=state(id)?:error("Game is not installed");require(row.getBoolean("enabled"));File(root,row.getString("hash")+".onklav-g")
    }
    companion object {
        const val LAST_LIGHT_ID = "app.onklav.lastlight"
        private val LAST_LIGHT_PUBLISHER = sha("OnKlav included Last Light Express publisher v1".toByteArray(Charsets.UTF_8))
        const val SHROOM_ID = "app.onklav.shroom"
        private const val SHROOM_PUBLISHER = "c31e44e88ae4fac46d56d1dbd05054e31b713df8ca9142a4b60bda7f16773dd5"
        private val lock=Any()
        internal val catalogEpoch = kotlinx.coroutines.flow.MutableStateFlow(0L)
        const val MAX_PACKAGE=64L*1024*1024
        const val MAX_ENTRY=64L*1024*1024
        const val MAX_TOTAL=128L*1024*1024
        fun open(context:Context)=GamePackages(File(context.noBackupFilesDir,"game-packages"), GameEngineRegistry.load(context.assets))
        fun safe(name:String)=name.length<=200 && name.matches(Regex("[a-zA-Z0-9_.-]+(/[a-zA-Z0-9_.-]+)*")) && name.split('/').none { it=="." || it==".." }
        fun sha(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
        fun hash(file:File)=file.inputStream().use { hashStream(it,MAX_PACKAGE) }
        fun hashStream(input:InputStream,limit:Long):String { val d=MessageDigest.getInstance("SHA-256");val b=ByteArray(32768);var total=0L;while(true){val n=input.read(b);if(n<0)break;total+=n;require(total<=limit);d.update(b,0,n)};return d.digest().joinToString(""){"%02x".format(it)} }
        fun copyBounded(input:InputStream,out:java.io.OutputStream,limit:Long) { val b=ByteArray(32768);var size=0L;while(true){val n=input.read(b);if(n<0)break;size+=n;require(size<=limit){"Game package is too large"};out.write(b,0,n)} }
        private fun write(file:AtomicFile,value:JSONObject) { val out=file.startWrite();try{out.write(value.toString().toByteArray(Charsets.UTF_8));file.finishWrite(out)}catch(e:Exception){file.failWrite(out);throw e} }
        private fun unhex(s:String):ByteArray { require(s.matches(Regex("[0-9a-f]+")) && s.length%2==0);return s.chunked(2).map{it.toInt(16).toByte()}.toByteArray() }
        private fun JSONObject.keySet()=keys().asSequence().toSet()
    }
}
