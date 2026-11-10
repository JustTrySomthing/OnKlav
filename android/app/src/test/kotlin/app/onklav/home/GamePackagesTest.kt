package app.onklav.home

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class GamePackagesTest {
    private val shared=JSONObject().put("index.js","1".repeat(64)).put("index.wasm","2".repeat(64))
    private val engine=JSONObject().put("version","4.7.2").put("files_sha256",shared)
    private fun key()=KeyPairGenerator.getInstance("EC").apply{initialize(java.security.spec.ECGenParameterSpec("secp256r1"))}.generateKeyPair()
    private fun fixture(dir:File,code:Int=1,key:KeyPair=key(),extra:Map<String,ByteArray> = emptyMap(), modes:List<String> = listOf("single"), profile:String="arcade"):File {
        val m=JSONObject().put("schema","onklav.package.v1").put("kind","game").put("runtime","godot.web.v1").put("entry","web/index.html").put("engine","4.7.2").put("engine_assets",shared).put("id","test.example.game").put("name","Example").put("version","1.$code").put("version_code",code).put("description","Example game").put("orientation","portrait").put("profile",profile).put("permissions",org.json.JSONArray()).put("controls","onklav.game.controls.v2").put("min_host",157).put("play_modes",org.json.JSONArray(modes))
        val files=linkedMapOf("manifest.json" to m.toString().toByteArray(),"web/index.html" to "<canvas/>".toByteArray(),"web/index.pck" to "GDPC-example".toByteArray());files.putAll(extra)
        val inv=JSONObject();files.forEach{(n,b)->inv.put(n,GamePackages.sha(b))}
        val pub=JSONObject().put("algorithm","ECDSA-P256-SHA256").put("public_key",key.public.encoded.joinToString(""){"%02x".format(it)}).put("files_sha256",inv).toString().toByteArray()
        val sig=Signature.getInstance("SHA256withECDSA").apply{initSign(key.private);update("onklav.game.signature.v1\u0000".toByteArray()+pub)}.sign()
        files["publisher.json"]=pub;files["signature.bin"]=sig
        return File.createTempFile("fixture-",".onklav-g",dir).apply{ZipOutputStream(outputStream()).use{z->files.forEach{(n,b)->z.putNextEntry(ZipEntry(n));z.write(b);z.closeEntry()}}}
    }
    @Test fun includedShroomKeepsOriginalSaveOriginAndLandscape() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val (game, archive) = GamePackages.open(context).openIncluded(context, GamePackages.SHROOM_ID)
        archive.use {
            assertEquals(GamePackages.SHROOM_ID, game.id)
            assertTrue(game.versionCode >= 5)
            assertEquals("https://g-" + GamePackages.sha("app.onklav.shroom:c31e44e88ae4fac46d56d1dbd05054e31b713df8ca9142a4b60bda7f16773dd5".toByteArray()) + ".onklav.invalid", game.origin)
            assertEquals("landscape", game.manifest.getString("orientation"))
            assertEquals("[\"single\"]", game.manifest.getJSONArray("play_modes").toString())
            assertTrue(it.getInputStream(it.getEntry("web/index.pck")).use { input -> input.read() } >= 0)
        }
    }

    @Test fun includedShroomIgnoresOldVisibilityFlag() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val id = GamePackages.SHROOM_ID
        assertTrue(platformGames.any { it.id == id && it.artwork == "shroom" })
        visibilityPrefs(context).edit().putBoolean("game:$id", true).commit()
        assertFalse(hidden(context, true, id))
        assertFalse(canHideCatalogEntry(games = true, id = id))
        setHidden(context, true, id, true)
        assertFalse(visibilityPrefs(context).getBoolean("game:$id", true))
        assertTrue(canHideCatalogEntry(games = true, id = "four"))
    }

    @Test fun omittedModesRemainSinglePlayerButMalformedSignedModesAreRejected() {
        val dir=Files.createTempDirectory("games-play-modes").toFile()
        try {
            val store=GamePackages(File(dir,"library"),engine)
            val original=fixture(dir)
            val manifest=store.inspect(original).manifest
            manifest.remove("play_modes")
            fun signedManifest()=fixture(dir,extra=mapOf("manifest.json" to manifest.toString().toByteArray()))
            assertFalse(store.inspect(signedManifest()).manifest.has("play_modes"))
            for(invalid in listOf("single",JSONObject.NULL,JSONObject())){
                manifest.put("play_modes",invalid)
                rejected{store.inspect(signedManifest())}
            }
        } finally { dir.deleteRecursively() }
    }
    @Test fun oldSignedPackageKeepsItsBundleAndSaveOwnerAfterPrimaryChanges() {
        val root = Files.createTempDirectory("retained-game-engine").toFile()
        try {
            val library = File(root, "library")
            val original = GamePackages(library, engine).install(fixture(root))
            val saves = GameSaveStore(root, original.origin)
            saves.commit(saves.begin(), "resume", JSONObject().put("level", 4), 1, true)
            val next = JSONObject().put("version", "4.8").put("files_sha256",
                JSONObject().put("index.js", "3".repeat(64)).put("index.wasm", "4".repeat(64)))
            val oldBundle = GameEngineBundle("godot-web-compat/original", engine)
            val registry = GameEngineRegistry(GameEngineBundle("godot-web", next), listOf(oldBundle))
            val upgraded = GamePackages(library, registry)
            val reopened = upgraded.open(original.id)
            reopened.second.use { assertNotNull(it.getEntry("web/index.pck")) }
            assertEquals(original.origin, reopened.first.origin)
            assertEquals(original.hash, reopened.first.hash)
            assertEquals("godot-web-compat/original/index.wasm", upgraded.engineFor(reopened.first).asset("index.wasm"))
            assertNull(upgraded.engineFor(reopened.first).asset("index.pck"))
            val nextManifest = JSONObject().put("engine", "4.8").put("engine_assets", next.getJSONObject("files_sha256"))
            assertEquals("godot-web/index.wasm", registry.select(nextManifest).asset("index.wasm"))
            val restored = GameSaveStore(root, reopened.first.origin).load("resume")
            assertEquals(4, restored.getJSONObject("record").getJSONObject("state").getInt("level"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun installsRealPythonSignedGodotExportWithTheProductionEngineContract(){
        val context=androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        val dir=Files.createTempDirectory("games-python").toFile()
        try {
            val e=context.assets.open("godot-web/engine.json").use{JSONObject(it.readBytes().toString(Charsets.UTF_8))}
            val store=GamePackages(dir,e)
            val fixture=File(System.getProperty("onklav.project.root"),"docs/fixtures/games/tap.onklav-g")
            val game=store.install(fixture)
            assertEquals("app.example.tap",game.id)
            assertTrue(game.origin.endsWith(".onklav.invalid"))
            store.open(game.id).second.use{assertNotNull(it.getEntry("web/index.pck"));assertNull(it.getEntry("web/index.wasm"))}
        }finally{dir.deleteRecursively()}
    }
    private fun rejected(block:()->Unit){try{block();fail("Expected rejection")}catch(e:IllegalArgumentException){} }
    @Test fun userCanInstallWithoutOwnerApprovalAndRemoveWithoutErasingPublisherIdentity(){
        val dir=Files.createTempDirectory("games-test").toFile();try{
            val store=GamePackages(File(dir,"library"),engine);val k=key();val p=fixture(dir,key=k)
            val game=store.install(p);assertEquals(1,store.installed().size);assertTrue(game.origin.startsWith("https://g-"))
            store.open(game.id).second.use{assertNotNull(it.getEntry("web/index.pck"))}
            store.remove(game.id);assertTrue(store.installed().isEmpty());rejected{store.install(fixture(dir,key=key()))}
            store.install(p);assertEquals(1,store.installed().size)
        }finally{dir.deleteRecursively()}
    }
    @Test fun updatesRejectWrongPublisherRollbackAndSameVersionChangedContent(){
        val dir=Files.createTempDirectory("games-update").toFile();try{
            val store=GamePackages(File(dir,"library"),engine);val k=key();store.install(fixture(dir,key=k))
            store.install(fixture(dir,code=2,key=k));rejected{store.install(fixture(dir,code=1,key=k))};rejected{store.install(fixture(dir,code=3,key=key()))}
            rejected{store.install(fixture(dir,code=2,key=k,extra=mapOf("web/new.txt" to byteArrayOf(1))))}
            assertEquals(2,store.installed().single().versionCode)
        }finally{dir.deleteRecursively()}
    }
    @Test fun rejectsTraversalAndSharedEngineReplacementEvenWhenPublisherSignsThem(){
        val dir=Files.createTempDirectory("games-path").toFile();try{
            val store=GamePackages(File(dir,"library"),engine)
            rejected{store.inspect(fixture(dir,extra=mapOf("web/../secret" to byteArrayOf(1))))}
            rejected{store.inspect(fixture(dir,extra=mapOf("web/index.wasm" to byteArrayOf(1))))}
            assertTrue(store.installed().isEmpty())
        }finally{dir.deleteRecursively()}
    }
    @Test fun signedSmsModesRequireTurnBasedProfile() {
        val dir=Files.createTempDirectory("games-modes").toFile();try{
            val store=GamePackages(File(dir,"library"),engine)
            assertEquals("turn_sms",store.inspect(fixture(dir,modes=listOf("single","turn_sms"),profile="turn_based")).manifest.getJSONArray("play_modes").getString(1))
            rejected{store.inspect(fixture(dir,modes=listOf("single","turn_sms")))}
            rejected{store.inspect(fixture(dir,modes=listOf("turn_sms"),profile="turn_based"))}
        }finally{dir.deleteRecursively()}
    }
    @Test fun changedStoredPackageFailsLaunchAndCannotExposeFilesystemPaths(){
        val dir=Files.createTempDirectory("games-tamper").toFile();try{
            val store=GamePackages(File(dir,"library"),engine);val game=store.install(fixture(dir));store.packageFile(game.id).appendText("tamper")
            rejected{store.open(game.id)}
            for(path in listOf("../secret","web/../secret","/web/a","web/%2e%2e/a","web/a\\b","web/a//b"))assertFalse(GamePackages.safe(path))
            assertTrue(GamePackages.safe("web/index.pck"))
        }finally{dir.deleteRecursively()}
    }
}
