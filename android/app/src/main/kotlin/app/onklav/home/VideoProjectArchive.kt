package app.onklav.home

import org.json.JSONObject
import java.io.*
import java.util.zip.*

/** Portable, offline project with its private media. Never extract arbitrary ZIP paths. */
internal object VideoProjectArchive {
    private const val maxManifest=8_000_000
    private const val maxMedia=4_000_000_000L
    private const val maxTotal=16_000_000_000L
    fun files(p:VideoProject)=(p.clips.map{it.file}+p.layers.map{it.clip.file}+p.music.map{it.file}+p.clips.map{it.lut}+p.layers.map{it.clip.lut}).filter{it.isNotEmpty()}.distinct()
    fun write(p:VideoProject,folder:File,output:OutputStream,checkpoint:()->Unit={}){
        VideoProject.parse(p.json());val manifest=p.json().toString().toByteArray(Charsets.UTF_8);require(manifest.size<=maxManifest){"Project details are too large"}
        ZipOutputStream(output.buffered()).use{zip->
            zip.setLevel(0) // Media is already compressed; avoid wasting the phone's battery.
            zip.putNextEntry(ZipEntry("project.json"));zip.write(manifest);zip.closeEntry()
            var total=0L
            for(name in files(p)){checkpoint();val file=File(folder,name);require(file.isFile&&file.length() in 1..maxMedia){"Missing or oversized media: $name"};zip.putNextEntry(ZipEntry("media/$name"));file.inputStream().use{input->val buffer=ByteArray(131072);var size=0L;while(true){checkpoint();val n=input.read(buffer);if(n<0)break;size+=n;total+=n;require(size<=maxMedia&&total<=maxTotal){"Project archive exceeds 16 GB"};zip.write(buffer,0,n)}};zip.closeEntry()}
        }
    }
    /** Returns a new identity. Failure/cancel removes only this import's staged data. */
    fun read(input:InputStream,root:File,checkpoint:()->Unit={}):VideoProject{
        val id=videoId();val staging=File(root,".import-$id");val destination=File(root,id);check(staging.mkdirs()){"Unable to prepare project import"}
        try{val parsed=ZipInputStream(input.buffered()).use{zip->
            require(zip.nextEntry?.name=="project.json"){"Choose a Video Edit project ZIP exported by Video Edit"}
            val manifest=ByteArrayOutputStream();val buffer=ByteArray(131072)
            while(true){checkpoint();val n=zip.read(buffer);if(n<0)break;require(manifest.size()+n<=maxManifest){"Project details are too large"};manifest.write(buffer,0,n)}
            val project=VideoProject.parse(JSONObject(manifest.toString("UTF-8"))).copy(id=id)
            val expected=files(project).map{"media/$it"}.toSet();val seen=mutableSetOf<String>();var total=0L
            while(true){checkpoint();val entry=zip.nextEntry?:break;require(!entry.isDirectory&&entry.name in expected&&seen.add(entry.name)){"Unexpected or repeated file in project archive"}
                val file=File(staging,entry.name.removePrefix("media/"));file.outputStream().buffered().use{out->var size=0L;while(true){checkpoint();val n=zip.read(buffer);if(n<0)break;size+=n;total+=n;require(size<=maxMedia&&total<=maxTotal){"Project archive exceeds its size limits"};require(root.usableSpace>64_000_000){"Not enough storage to restore this project"};out.write(buffer,0,n)};require(size>0){"Project media is empty"}}
            };require(seen==expected){"Project archive is missing media"};project
        };checkpoint();check(!destination.exists()&&staging.renameTo(destination)){"Unable to publish imported media"};return parsed
        }catch(e:Exception){staging.deleteRecursively();throw e}
    }
}
