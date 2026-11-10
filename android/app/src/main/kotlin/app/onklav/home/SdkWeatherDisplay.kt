package app.onklav.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import app.onklav.shared.R
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class WeatherPoint(val at:ZonedDateTime,val temperature:Double,val wind:Double,val humidity:Double,val rain:Double,val symbol:String)
internal fun weatherPoints(node:JSONObject):List<WeatherPoint>{
 val zone=runCatching{ZoneId.of(node.optString("zone","UTC"))}.getOrDefault(ZoneId.of("UTC"));val rows=node.optJSONObject("forecast")?.optJSONArray("points")?:return emptyList()
 return (0 until minOf(rows.length(),120)).mapNotNull{i->runCatching{val r=rows.getJSONArray(i);val t=r.getDouble(1);require(t.isFinite()&&t in -100.0..70.0);WeatherPoint(Instant.parse(r.getString(0)).atZone(zone),t,r.optDouble(2,0.0),r.optDouble(3,0.0),r.optDouble(4,0.0),r.optString(5,"cloudy"))}.getOrNull()}
}
internal fun weatherCondition(symbol:String):String{val base=symbol.substringBefore('_');return when(base){"clearsky"->"Clear";"fair"->"Mostly clear";"partlycloudy"->"Partly cloudy";"cloudy"->"Cloudy";"fog"->"Fog";else->when{base.contains("thunder")->"Thunderstorms";base.contains("snow")->if(base.contains("light"))"Light snow"else"Snow";base.contains("sleet")->"Sleet";base.contains("rain")->if(base.contains("heavy"))"Heavy rain"else if(base.contains("light"))"Light rain"else"Rain";else->"Forecast"}}}
@Composable internal fun SdkWeatherDisplay(node:JSONObject,modifier:Modifier=Modifier){
 val points=weatherPoints(node);if(points.isEmpty()){Text("Forecast unavailable. Tap Refresh.",color=MaterialTheme.colorScheme.error);return}
 val now=points.first();val fahrenheit=node.optBoolean("fahrenheit",true);val unit=if(fahrenheit)"°F"else"°C"
 fun temp(t:Double)="${kotlin.math.round(if(fahrenheit)t*9/5+32 else t).toInt()}°"
 fun wind(v:Double)="${kotlin.math.round(v*if(fahrenheit)2.236936 else 3.6).toInt()} ${if(fahrenheit)"mph"else"km/h"}"
 val locale=Locale.getDefault();val hour=DateTimeFormatter.ofPattern("h a",locale);val date=DateTimeFormatter.ofPattern("EEE, MMM d",locale)
 Column(modifier.fillMaxWidth().testTag("sdk-weather-display"),verticalArrangement=Arrangement.spacedBy(18.dp)){
  Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.primaryContainer){Column(Modifier.fillMaxWidth().padding(22.dp)){
   Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Now",style=MaterialTheme.typography.labelLarge);Text(temp(now.temperature)+unit.takeLast(1),fontSize=54.sp,fontWeight=FontWeight.Light);Text(weatherCondition(now.symbol),fontSize=20.sp)};Icon(painterResource(R.drawable.ref_app_weather),null,Modifier.size(68.dp),tint=MaterialTheme.colorScheme.onPrimaryContainer)}
   HorizontalDivider(Modifier.padding(vertical=16.dp),color=MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha=.15f))
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Column{Text("Wind",style=MaterialTheme.typography.labelMedium);Text(wind(now.wind),fontSize=17.sp)};Column{Text("Humidity",style=MaterialTheme.typography.labelMedium);Text("${now.humidity.toInt()}%",fontSize=17.sp)};Column{Text("Rain · next hour",style=MaterialTheme.typography.labelMedium);Text(if(fahrenheit)"${"%.2f".format(locale,now.rain/25.4)} in"else"${"%.1f".format(locale,now.rain)} mm",fontSize=17.sp)}}
  }}
  Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
   Text("Next hours",style=MaterialTheme.typography.titleMedium)
   Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){
    points.take(12).forEachIndexed{i,p->Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surfaceVariant){Column(Modifier.width(88.dp).padding(vertical=14.dp,horizontal=8.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)){Text(if(i==0)"Now"else hour.format(p.at),style=MaterialTheme.typography.labelMedium);Text(temp(p.temperature),fontSize=22.sp,fontWeight=FontWeight.Medium);Text(weatherCondition(p.symbol),style=MaterialTheme.typography.labelSmall,maxLines=2)}}}
   }
  }
  Column(verticalArrangement=Arrangement.spacedBy(4.dp)){
   Text("7-day outlook",style=MaterialTheme.typography.titleMedium)
   points.groupBy{it.at.toLocalDate()}.entries.take(7).forEachIndexed{i,(_,day)->
    val p=day.first();val lo=day.minOf{it.temperature};val hi=day.maxOf{it.temperature};val condition=day.minByOrNull{kotlin.math.abs(it.at.hour-12)}?:p
    Row(Modifier.fillMaxWidth().padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(if(i==0)"Today"else date.format(p.at),fontSize=16.sp);Text(weatherCondition(condition.symbol),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)};Text("${temp(lo)}  /  ${temp(hi)}",fontSize=17.sp)}
    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.45f))
   }
  }
  val updated=runCatching{Instant.parse(node.getJSONObject("forecast").getString("updated")).atZone(now.at.zone)}.getOrNull()
  Text(if(updated!=null)"Updated ${date.format(updated)} · ${hour.format(updated)} ${updated.zone.id}"else"Forecast times: ${now.at.zone.id}",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
 }
}
