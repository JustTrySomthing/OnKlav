//! Foreground native requests. Owners and session leases are supplied by the broker.
use serde_json::Value;
use crate::Result;
pub fn cap(s:&str)->Option<&'static str>{Some(match s{
 "calendar.resolve"|"calendar.localize"=>"clock.read","calendar.insert"=>"calendar.handoff","connectivity.read"=>"device.connectivity","device.settings"=>"device.settings","location.current"|"location.manual"=>"location.foreground","torch.set"=>"device.torch","ble.select"|"ble.connect"|"ble.read"|"ble.write"|"ble.notify"|"ble.disconnect"=>"nearby.bluetooth","wifi.join"|"wifi.disconnect"=>"nearby.wifi","lan.select"|"lan.exchange"|"lan.disconnect"=>"network.lan",_=>return None})}
fn text<'a>(a:&'a Value,k:&str,max:usize)->Result<&'a str>{a[k].as_str().filter(|s|!s.is_empty()&&s.len()<=max&&!s.chars().any(char::is_control)).ok_or_else(||format!("Invalid {k}"))}
pub fn request(s:&str,a:&Value)->Result<()>{
 let keys:&[&str]=match s{
 "calendar.localize"=>&["start","end","timezone"],"calendar.resolve"=>&["start","end","timezone","gap","overlap"],"calendar.insert"=>&["start_ms","end_ms","title","description","timezone","all_day"],"connectivity.read"|"location.current"|"ble.connect"|"ble.disconnect"|"wifi.disconnect"|"lan.disconnect"=>&[],"device.settings"=>&["target"],"location.manual"=>&["latitude","longitude"],"torch.set"=>&["enabled","seconds"],"ble.select"=>&["service"],"ble.read"|"ble.notify"=>&["characteristic"],"ble.write"=>&["characteristic","hex"],"wifi.join"=>&["ssid","passphrase"],"lan.select"=>&["type"],"lan.exchange"=>&["hex"],_=>return Err("Unsupported foreground service".into())};
 let o=a.as_object().ok_or("Expected device arguments")?;if o.keys().any(|k|!keys.contains(&k.as_str())){return Err("Unsupported device argument".into())}
 match s{
 "calendar.localize"=>{for k in ["start","end"]{let v=text(a,k,16)?;if v.len()!=16||!v.is_ascii(){return Err("Invalid UTC calendar timestamp".into())}}text(a,"timezone",128)?;},
 "calendar.resolve"=>{for k in ["start","end"]{let v=text(a,k,19)?;if v.len()!=19||!v.is_ascii(){return Err("Use a local ISO date and time".into())}}text(a,"timezone",128)?;if a.get("gap").is_some()&&!matches!(a["gap"].as_str(),Some("reject"|"forward")){return Err("Invalid clock gap policy".into())}if a.get("overlap").is_some()&&!matches!(a["overlap"].as_str(),Some("earlier"|"later")){return Err("Invalid clock overlap policy".into())}},
 "calendar.insert"=>{let start=a["start_ms"].as_u64().ok_or("Invalid calendar start")?;let end=a["end_ms"].as_u64().ok_or("Invalid calendar end")?;if end<=start||end>253402300799999{return Err("Invalid calendar range".into())}text(a,"title",1024)?;text(a,"timezone",128)?;if a.get("description").is_some()&&a["description"].as_str().is_none_or(|v|v.len()>8192){return Err("Invalid calendar details".into())}if a.get("all_day").is_some()&&!a["all_day"].is_boolean(){return Err("Invalid all-day flag".into())}},
 "device.settings"=>if !matches!(a["target"].as_str(),Some("internet"|"wifi"|"bluetooth"|"location")){return Err("Unsupported settings target".into())},
 "location.manual"=>for(k,min,max)in [("latitude",-90.,90.),("longitude",-180.,180.)]{if a[k].as_f64().is_none_or(|v|!v.is_finite()||v<min||v>max){return Err("Invalid coordinates".into())}},
 "torch.set"=>{if !a["enabled"].is_boolean()||a["enabled"]==true&&a["seconds"].as_u64().is_none_or(|n|!(1..=120).contains(&n)){return Err("Choose a flashlight duration up to120 seconds".into())}},
 "ble.select"=>{uuid::Uuid::parse_str(text(a,"service",36)?).map_err(|_|"Invalid service UUID")?;},
 "ble.read"|"ble.write"|"ble.notify"=>{uuid::Uuid::parse_str(text(a,"characteristic",36)?).map_err(|_|"Invalid characteristic UUID")?;},
 "wifi.join"=>{text(a,"ssid",32)?;if a.get("passphrase").is_some()&&a["passphrase"].as_str().is_none_or(|v|!v.is_empty()&&(!(8..=63).contains(&v.len())||!v.is_ascii())){return Err("Invalid Wi-Fi passphrase".into())}},
 "lan.select"=>{let t=text(a,"type",40)?;let Some(name)=t.strip_prefix('_').and_then(|t|t.strip_suffix("._tcp."))else{return Err("Choose a DNS-SD TCP service type".into())};if name.is_empty()||name.len()>30||!name.bytes().all(|b|b.is_ascii_alphanumeric()||b==b'-'){return Err("Invalid local service type".into())}},_=>{}
 }
 if matches!(s,"ble.write"|"lan.exchange"){let max=if s=="ble.write"{1024}else{8192};if a["hex"].as_str().is_none_or(|v|v.len()>max||v.len()%2!=0||!v.bytes().all(|b|b.is_ascii_digit()||(b'a'..=b'f').contains(&b))){return Err("Invalid bounded binary payload".into())}}
 Ok(())
}
#[cfg(test)]mod tests{use super::*;use serde_json::json;
 #[test]fn scope_and_bounds(){assert_ne!(cap("wifi.join"),cap("lan.exchange"));assert!(cap("ble.read").is_some());assert!(request("torch.set",&json!({"enabled":true,"seconds":121})).is_err());assert!(request("location.manual",&json!({"latitude":91,"longitude":0})).is_err());assert!(request("lan.exchange",&json!({"hex":"00","host":"evil"})).is_err());assert!(request("ble.select",&json!({"service":"180d"})).is_err());assert!(request("connectivity.read",&json!({"ssid":true})).is_err());}
 #[test]fn valid_workflows(){for(s,a)in [("torch.set",json!({"enabled":true,"seconds":30})),("location.manual",json!({"latitude":41.2,"longitude":-87.1})),("ble.select",json!({"service":"0000180d-0000-1000-8000-00805f9b34fb"})),("lan.select",json!({"type":"_onklav._tcp."})),("wifi.join",json!({"ssid":"My device"})),("calendar.insert",json!({"start_ms":1000,"end_ms":2000,"title":"Meeting","timezone":"UTC"}))]{assert!(request(s,&a).is_ok(),"{s}")}}
}
