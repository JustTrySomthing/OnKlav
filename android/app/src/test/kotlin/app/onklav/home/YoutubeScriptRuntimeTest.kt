package app.onklav.home

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class YoutubeScriptRuntimeTest {
    @Test fun extractorRunsPlayerJavascriptWithoutTheUnusedJsr223Adapter() {
        val script="function transform(a){return a.split('').reverse().slice(1).join('');}"
        assertEquals("edcba",org.schabi.newpipe.extractor.utils.JavaScript.run(script,"transform","abcdef"))
    }
}
