package app.onklav.home

import org.json.JSONObject
import java.util.Locale

/** Display-only translations. IDs, input values, routes and effects never change. */
internal object SdkLocalization {
    private val fields=setOf("title","text","hint","label","detail","long_label","drawer_title","menu_title","accept_label")
    fun frame(source:JSONObject,locales:List<String>):JSONObject {
        var units=0
        fun visit(original:JSONObject):JSONObject {
            val out=JSONObject(original.toString())
            val translations=original.optJSONObject("i18n")?.takeIf{it.length()<=32}
            val candidates=locales.flatMap{tag->
                val parts=tag.replace('_','-').split('-')
                (parts.size downTo 1).map{parts.take(it).joinToString("-")}
            }.distinct()
            val dictionaries=candidates.mapNotNull{tag->translations?.keys()?.asSequence()?.firstOrNull{it.equals(tag,true)}?.let{translations.optJSONObject(it)}}
            for(field in fields){
                val value=dictionaries.firstNotNullOfOrNull{dictionary->
                    (dictionary.opt(field) as? String)?.takeIf{it.length<=(if(field.endsWith("title"))80 else 100000)&&it.none{c->c.isISOControl()&&c!='\n'&&c!='\t'}}
                }
                if(value!=null)out.put(field,value)
            }
            out.remove("i18n")
            for(key in listOf("body","actions","drawer","menu","options")) {
                out.optJSONArray(key)?.let{array->for(index in 0 until array.length())array.optJSONObject(index)?.let{array.put(index,visit(it))}}
            }
            for(key in listOf("confirm","trailing"))out.optJSONObject(key)?.let{out.put(key,visit(it))}
            for(key in fields+"value")if(out.opt(key)is String)units+=out.getString(key).length
            return out
        }
        val result=visit(source)
        return if(units<=150000)result else source
    }
    fun tags(context:android.content.Context):List<String> = context.resources.configuration.locales.let{list->List(list.size()){list[it].toLanguageTag()}}
    fun rtl(tags:List<String>):Boolean = tags.firstOrNull()?.let{android.text.TextUtils.getLayoutDirectionFromLocale(Locale.forLanguageTag(it))==android.view.View.LAYOUT_DIRECTION_RTL}?:false
}
