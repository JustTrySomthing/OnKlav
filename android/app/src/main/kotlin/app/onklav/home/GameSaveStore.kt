package app.onklav.home

import android.util.AtomicFile
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import org.json.JSONObject

/** Host-owned cartridge storage. A renderer supplies data, never an owner or file path. */
internal class GameSaveStore(private val root:File, private val owner:String, private val beforeWrite:(File)->Unit={}) {
    data class Lease(val token:String)
    private val directory=File(root,"game-saves/"+digest(owner))
    private fun <T> locked(block:()->T):T=synchronized(monitor) {
        if(!directory.isDirectory && !directory.mkdirs())throw IOException("Game save storage unavailable")
        FileOutputStream(File(directory,"lock"),true).channel.use{c->c.lock().use{block()}}
    }
    private fun file(slot:String):AtomicFile {require(slot.matches(Regex("[a-z][a-z0-9_.-]{0,95}")));return AtomicFile(File(directory,"$slot.json"))}
    private fun write(file:AtomicFile,text:String) {val out=file.startWrite();try{beforeWrite(file.baseFile);out.write(text.toByteArray(Charsets.UTF_8));file.finishWrite(out)}catch(e:Exception){file.failWrite(out);throw e}}
    private fun bytes(file:AtomicFile):String? = try { file.openRead().use{input->val b=bounded(input);check(b.size<=LIMIT+8192){"Saved data is too large"};b.toString(Charsets.UTF_8)} }catch(e:java.io.FileNotFoundException){if(file.baseFile.exists())throw e else null}
    private fun bounded(input:java.io.InputStream):ByteArray {val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);while(true){val n=input.read(buffer);if(n<0)break;check(out.size()+n<=LIMIT+8192){"Saved data is too large"};out.write(buffer,0,n)};return out.toByteArray()}
    private fun decode(text:String,slot:String):JSONObject {val row=JSONObject(text);check(row.getString("schema")=="onklav.game.save.v2" && row.getString("owner")==owner && row.getString("slot")==slot){"Saved data belongs to another game or uses an unsupported format"};check(row.getLong("revision")>0 && row.getInt("payload_version")>0);check(digest(row.getJSONObject("state").toString())==row.getString("checksum")){"Saved data failed its integrity check"};return row}
    private fun current(slot:String):JSONObject? {val text=bytes(file(slot))?:return null;return decode(text,slot)}
    fun begin():Lease=locked{val lease=Lease(UUID.randomUUID().toString());write(AtomicFile(File(directory,"writer")),lease.token);lease}
    private fun requireLease(lease:Lease){check(bytes(AtomicFile(File(directory,"writer")))==lease.token){"This game session was replaced. Reopen the game before saving."}}
    fun load(slot:String):JSONObject=locked {
        try {val row=current(slot);JSONObject().put("ok",true).put("known",row!=null).put("exists",row!=null && !row.getJSONObject("state").optBoolean("_onklav_discarded",false)).put("record",row)}
        catch(e:Exception){
            // Keep originals. Expose recovery explicitly rather than making corruption look like no save.
            val backup=runCatching{bytes(AtomicFile(File(directory,"$slot.previous")))?.let{decode(it,slot)}}.getOrNull()
            JSONObject().put("ok",false).put("error","Saved progress could not be read. It has been retained.").put("backup",backup)
        }
    }
    fun commit(lease:Lease,slot:String,state:JSONObject,version:Int,resumable:Boolean,replace:Boolean=false):JSONObject=locked {
        requireLease(lease);require(version>0);check(state.toString().toByteArray().size<=LIMIT){"Game save exceeds the 2 MiB slot limit"}
        val target=file(slot);val old=runCatching{current(slot)}.getOrElse{if(!replace)throw it else null}
        if(old==null && !target.baseFile.exists())check((directory.listFiles()?.count{it.name.endsWith(".json")}?:0)<32){"Game save slot limit reached"}
        val revision=(old?.optLong("revision")?:0)+1
        val row=JSONObject().put("schema","onklav.game.save.v2").put("owner",owner).put("slot",slot).put("revision",revision).put("payload_version",version).put("resumable",resumable).put("saved_at",System.currentTimeMillis()).put("state",state).put("checksum",digest(state.toString()))
        // A valid previous checkpoint survives a failed write and the next successful replacement.
        if(old!=null)write(AtomicFile(File(directory,"$slot.previous")),old.toString())
        else if(replace){bytes(target)?.let{write(AtomicFile(File(directory,"$slot.preserved")),it)}}
        write(target,row.toString());JSONObject().put("ok",true).put("revision",revision)
    }
    fun clear(lease:Lease,slot:String):JSONObject = if(slot.startsWith("data.")) locked{requireLease(lease);file(slot).delete();AtomicFile(File(directory,"$slot.previous")).delete();JSONObject().put("ok",true)} else commit(lease,slot,JSONObject().put("_onklav_discarded",true),1,false,true)
    private fun forgetFriend(slot:String)=locked{require(slot.startsWith("friend."));file(slot).delete();AtomicFile(File(directory,"$slot.previous")).delete();AtomicFile(File(directory,"$slot.preserved")).delete()}

    companion object {
        fun removeFriend(context:android.content.Context,game:String,match:String){
            val owner=if(game in WebGameActivity.BUILTINS)"onklav.builtin.$game" else GamePackages.open(context).installed().firstOrNull{it.id==game}?.origin?:return
            GameSaveStore(context.noBackupFilesDir,owner).forgetFriend("friend.$match")
        }
        const val LIMIT=2*1024*1024
        private val monitor=Any()
        private fun digest(text:String)=MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
    }
}
