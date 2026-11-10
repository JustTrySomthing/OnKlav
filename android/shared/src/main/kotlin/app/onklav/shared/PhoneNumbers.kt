package app.onklav.shared

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.*

/** Presentation only: keep dialable identifiers separate from punctuation. Short codes stay intact. */
fun phoneInput(value:String):String = buildString {
    value.forEach { c -> if(c in '0'..'9' && count {it.isDigit()}<15) append(c) else if(c=='+' && isEmpty()) append(c) }
}
fun displayPhone(value:String,partial:Boolean=false):String {
    val raw=phoneInput(value); val digits=raw.filter {it.isDigit()}
    val country=digits.length==11 && digits.startsWith("1") || raw.startsWith("+1") && (partial || digits.length>5)
    val national=if(country)digits.drop(1)else digits
    if((raw.startsWith('+')&&!raw.startsWith("+1")) || national.length>10 || national.isEmpty() || (!partial && national.length<6)) return raw
    return (if(country)"+1 " else "")+"("+national.take(3)+(if(national.length>=3)")"else "")+(if(national.length>3)" "+national.drop(3).take(3)else "")+(if(national.length>6)"-"+national.drop(6)else "")
}
object PhoneVisualTransformation:VisualTransformation {
    override fun filter(text:AnnotatedString):TransformedText {
        val formatted=displayPhone(text.text,partial=true)
        val mapping=object:OffsetMapping {
            fun dialable(c:Char)=c.isDigit()||(c=='+'&&text.text.startsWith('+'))
            override fun originalToTransformed(offset:Int):Int {
                val count=text.text.take(offset).count(::dialable)
                if(count==0)return 0
                var seen=0
                for(i in formatted.indices)if(dialable(formatted[i])&&++seen==count)return i+1
                return formatted.length
            }
            override fun transformedToOriginal(offset:Int):Int {
                val count=formatted.take(offset).count(::dialable)
                if(count==0)return 0
                var seen=0
                for(i in text.indices)if(dialable(text[i])&&++seen==count)return i+1
                return text.length
            }
        }
        return TransformedText(AnnotatedString(formatted),mapping)
    }
}
