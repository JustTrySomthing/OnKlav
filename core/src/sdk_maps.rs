//! Bounded raster-map widget schema. Network/cache policy is a separate broker.
use serde_json::Value;
use std::collections::BTreeSet;
type Result<T> = std::result::Result<T,String>;
fn text<'a>(v:&'a Value,k:&str,max:usize)->Result<&'a str>{let s=v[k].as_str().ok_or("Missing map text")?;if s.trim().is_empty()||s.len()>max||s.chars().any(char::is_control){return Err("Invalid map text".into())}Ok(s)}
fn https(s:&str)->bool{
 if s.len()>512||!s.starts_with("https://")||s.chars().any(|c|c.is_whitespace()||c.is_control()||matches!(c,'\\'|'?'|'#'|'@')){return false}
 let host=s[8..].split('/').next().unwrap_or("");!host.is_empty()&&host.len()<=253&&host.split('.').count()>1&&host.split('.').all(|p|!p.is_empty()&&p.len()<=63&&!p.starts_with('-')&&!p.ends_with('-')&&p.bytes().all(|b|b.is_ascii_alphanumeric()||b==b'-'))
}
pub(crate) fn validate(n:&Value)->Result<()>{
 if let Some(callback)=n.get("on_viewport"){if callback.as_str().is_none_or(|s|s.is_empty()||s.len()>96){return Err("Invalid map viewport callback".into())}}
 let p=&n["provider"];text(p,"id",64)?;text(p,"name",80)?;text(p,"attribution",300)?;
 if !["osm","other"].contains(&text(p,"data",8)?){return Err("Unknown map data source".into())}
 if !https(text(p,"copyright_url",512)?){return Err("Use an HTTPS map copyright page without credentials/query".into())}
 let template=text(p,"template",512)?;if !https(template)||["{z}","{x}","{y}"].iter().any(|s|template.matches(s).count()!=1)||template.replace("{z}","0").replace("{x}","0").replace("{y}","0").contains(['{','}']){return Err("Use a keyless HTTPS XYZ template".into())}
 if !p["offline_allowed"].is_boolean(){return Err("Declare provider offline policy".into())}
 if template[8..].split('/').next().unwrap_or("").to_ascii_lowercase().ends_with(".openstreetmap.org")&&(p["data"]!="osm"||p["offline_allowed"]!=false){return Err("OSM public tiles require OSM credits and forbid offline-area downloads".into())}
 let z=n["zoom"].as_u64().filter(|z|*z<=19).ok_or("Map zoom must be0–19")?;let world=1u64<<z;
 for (key,max) in [("latitude",85.05112878),("longitude",180.0)]{if n[key].as_f64().is_none_or(|v|!v.is_finite()||v.abs()>max){return Err("Invalid map center".into())}}
 let tiles=n["tiles"].as_array().ok_or("Missing map tiles")?;if tiles.len()>25{return Err("At most25 map tiles".into())}
 let mut seen=BTreeSet::new();for t in tiles{
  let x=t["x"].as_u64().filter(|v|*v<world).ok_or("Invalid map tile column")?;let y=t["y"].as_u64().filter(|v|*v<world).ok_or("Invalid map tile row")?;
  if !seen.insert((x,y)){return Err("Duplicate map tile".into())}
  if t["file"].as_str().is_none_or(|s|s.len()!=32||!s.bytes().all(|b|b.is_ascii_digit()||(b'a'..=b'f').contains(&b))){return Err("Use app-private map tile handles".into())}
 }
 if let Some(route)=n.get("route"){text(route,"attribution",300)?;let points=route["points"].as_array().filter(|p|(2..=2048).contains(&p.len())).ok_or("Route exceeds2048 points")?;for p in points{for(key,max)in[("latitude",85.05112878),("longitude",180.)]{if p[key].as_f64().is_none_or(|v|!v.is_finite()||v.abs()>max){return Err("Invalid map route point".into())}}}}
 let markers=n["markers"].as_array().ok_or("Missing map markers")?;if markers.len()>32{return Err("At most32 map markers".into())}for m in markers{text(m,"label",80)?;for(key,max)in[("latitude",85.05112878),("longitude",180.)]{if m[key].as_f64().is_none_or(|v|!v.is_finite()||v.abs()>max){return Err("Invalid map marker".into())}}}
 Ok(())
}
#[cfg(test)]mod tests{
 use super::*;use serde_json::json;
 fn node()->Value{json!({"provider":{"id":"demo","name":"Demo","data":"osm","attribution":"Sample overlay","copyright_url":"https://www.openstreetmap.org/copyright","template":"https://example.org/{z}/{x}/{y}.png","offline_allowed":false},"zoom":1,"latitude":0.,"longitude":180.,"tiles":[{"x":0,"y":1,"file":"a".repeat(32)}],"markers":[]})}
 #[test]fn maps_route_geometry_requires_bounded_points_and_visible_credits(){let mut n=node();n["route"]=json!({"attribution":"Demo route","points":[{"latitude":0.,"longitude":179.},{"latitude":1.,"longitude":-179.}]});assert!(validate(&n).is_ok());n["route"]["points"][0]["latitude"]=json!(90);assert!(validate(&n).is_err());n["route"]["points"][0]["latitude"]=json!(0);n["route"]["attribution"]=json!("");assert!(validate(&n).is_err());}
 #[test]fn maps_schema_bounds_coordinates_private_tiles_and_provider_metadata(){let n=node();assert!(validate(&n).is_ok());for(k,v)in[("zoom",json!(20)),("latitude",json!(90)),("longitude",json!(-181)),("tiles",json!([{"x":2,"y":0,"file":"a".repeat(32)}])),("markers",json!([{"latitude":0,"longitude":0,"label":""}]))]{let mut bad=n.clone();bad[k]=v;assert!(validate(&bad).is_err(),"{k}");}let mut duplicate=n.clone();duplicate["tiles"]=json!([n["tiles"][0],n["tiles"][0]]);assert!(validate(&duplicate).is_err());}
 #[test]fn maps_provider_rejects_hidden_credits_credentials_and_ambiguous_templates(){for s in ["http://example.org/{z}/{x}/{y}.png","https://user@example.org/{z}/{x}/{y}.png","https://example.org/{z}/{x}/{y}.png?key=secret","https://example.org/{z}/{x}/{y}/{token}.png"]{let mut n=node();n["provider"]["template"]=json!(s);assert!(validate(&n).is_err());}let mut n=node();n["provider"]["attribution"]=json!(" ");assert!(validate(&n).is_err());}
}
