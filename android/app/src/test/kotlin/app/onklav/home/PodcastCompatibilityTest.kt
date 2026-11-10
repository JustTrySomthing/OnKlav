package app.onklav.home

import java.io.File
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class PodcastCompatibilityTest {
 @Test fun realPublisherFeedsListPlayableEpisodes(){
  val directory=System.getenv("ONKLAV_PODCAST_FIXTURES")?.let(::File)?:return
  val files=directory.listFiles()?.filter{it.extension=="xml"}.orEmpty();assertTrue(files.isNotEmpty())
  for(file in files){try{val result=SdkPodcasts.parse(file.readBytes());assertTrue(result.getJSONArray("items").length()>0);println("Parsed ${file.name}: ${result.getString("title")} / ${result.getJSONArray("items").length()} episodes")}catch(e:Exception){throw AssertionError("Publisher fixture ${file.name}: ${e.message}",e)}}
 }
}
