package app.onklav.home

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class SdkLocalizationTest {
    @Test fun languageFallbackDoesNotChangeDataOrActions(){
        val source=JSONObject("""{"title":"App","body":[{"kind":"field","id":"name","hint":"Name","value":"private draft","i18n":{"ar":{"hint":"الاسم","value":"wrong","id":"wrong"}}}],"i18n":{"ar":{"title":"تطبيق"}},"effect":{"service":"records.save","args":{"text":"original"}}}""")
        val result=SdkLocalization.frame(source,listOf("ar-EG"))
        assertEquals("تطبيق",result.getString("title"))
        val field=result.getJSONArray("body").getJSONObject(0)
        assertEquals("الاسم",field.getString("hint"));assertEquals("name",field.getString("id"));assertEquals("private draft",field.getString("value"))
        assertEquals(source.getJSONObject("effect").toString(),result.getJSONObject("effect").toString())
        assertEquals("Name",source.getJSONArray("body").getJSONObject(0).getString("hint"))
        assertEquals("App",SdkLocalization.frame(source,listOf("fr")).getString("title"))
    }
    @Test fun exactLocaleWinsAndNestedLabelsTranslate(){
        val source=JSONObject("""{"title":"App","menu":[{"kind":"select","id":"choice","options":[{"value":"a","text":"First","i18n":{"es":{"text":"Primero"}}}]}],"i18n":{"es":{"title":"General","menu_title":"Opciones"},"es-MX":{"title":"México"}}}""")
        val result=SdkLocalization.frame(source,listOf("es-MX","en"))
        assertEquals("México",result.getString("title"));assertEquals("Opciones",result.getString("menu_title"))
        val option=result.getJSONArray("menu").getJSONObject(0).getJSONArray("options").getJSONObject(0)
        assertEquals("Primero",option.getString("text"));assertEquals("a",option.getString("value"))
    }
    @Test fun oversizedResourcesFallBackAndRtlFollowsLocale(){
        val source=JSONObject().put("title","Fallback").put("i18n",JSONObject().put("es",JSONObject().put("title","x".repeat(81))))
        assertEquals("Fallback",SdkLocalization.frame(source,listOf("es")).getString("title"))
        assertTrue(SdkLocalization.rtl(listOf("ar-EG")));assertTrue(SdkLocalization.rtl(listOf("he")))
        assertFalse(SdkLocalization.rtl(listOf("en-US")))
    }
}
