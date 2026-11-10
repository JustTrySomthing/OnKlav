package app.onklav.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONObject
import java.io.File

/** Private, fsynced callback receipts. Removal follows the durable core commit. */
internal class CarrierResults(context:Context,private val folder:String="carrier-results") {
    private val root=File(context.filesDir,folder).apply{check(mkdirs()||isDirectory)}
    @Synchronized fun save(id:String,value:JSONObject) {
        require(id.matches(Regex("[a-f0-9]{32}")))
        val bytes=value.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size<=8192)
        val file=File(root,"$id.json")
        check(file.exists()||root.listFiles().orEmpty().count{it.extension=="json"}<256){"Too many unprocessed carrier callbacks"}
        val temp=File(root,"$id.tmp")
        temp.outputStream().use{it.write(bytes);it.fd.sync()}
        check(temp.renameTo(file))
    }
    @Synchronized fun read(id:String):JSONObject? {
        require(id.matches(Regex("[a-f0-9]{32}")))
        val f=File(root,"$id.json");if(!f.exists())return null
        require(f.length()<=8192){"Invalid carrier receipt"}
        return JSONObject(f.readText())
    }
    @Synchronized fun remove(id:String){require(id.matches(Regex("[a-f0-9]{32}")));val f=File(root,"$id.json");check(!f.exists()||f.delete())}
    fun ids():List<String> = root.listFiles().orEmpty().filter{it.name.matches(Regex("[a-f0-9]{32}\\.json"))}.map{it.nameWithoutExtension}.sorted()
    companion object {
        val kinds=setOf("sms_sent","sms_delivery","mms_sent","mms_download","mms_ack")
        fun valid(intent:Intent):Boolean {
            val id=intent.getStringExtra("token")?:return false
            val kind=intent.getStringExtra("kind")?:return false
            val part=intent.getIntExtra("part",0)
            return id.matches(Regex("[a-f0-9]{32}"))&&kind in kinds&&part in 0..254&&
                (kind.startsWith("sms_")||part==0)&&intent.action=="app.onklav.$kind"&&intent.data==Uri.parse("onklav-result://$kind/$id/$part")
        }
    }
}
