//! Public offline GeoNames city metadata. No location or network permission.
use serde_json::{json,Value};
use crate::Result;
use crate::weather_cities::CITIES;
mod postal_data{include!("weather_us_postal.rs");}
fn postal(query:&str)->Option<Vec<Value>>{let q=query.strip_prefix("us ").unwrap_or(query);let base=q.split_once('-').map(|(zip,plus)|if plus.len()==4&&plus.bytes().all(|b|b.is_ascii_digit()){zip}else{""}).unwrap_or(q);if base.len()!=5||!base.bytes().all(|b|b.is_ascii_digit()){return None}
 let found=postal_data::POSTAL.lines().find(|r|r.starts_with(&format!("{base}\t")));let Some(row)=found else{return Some(vec![])};let a=row.split('\t').collect::<Vec<_>>();let lat=a[3].parse::<f64>().unwrap();let lon=a[4].parse::<f64>().unwrap();
 let mut nearest=(f64::MAX,"UTC");for line in CITIES.lines(){let c=line.split('\t').collect::<Vec<_>>();if c.len()!=10||c[3]!="US"||c[4]!=a[2]{continue}if let(Ok(y),Ok(x))=(c[5].parse::<f64>(),c[6].parse::<f64>()){let distance=(y-lat).powi(2)+((x-lon)*lat.to_radians().cos()).powi(2);if distance<nearest.0{nearest=(distance,c[7]);}}}
 Some(vec![json!({"id":format!("us-zip-{base}"),"name":format!("{}, {} {}",a[1],a[2],base),"lat":lat,"lon":lon,"zone":nearest.1})])}
pub fn search(query:&str)->Vec<Value>{let q=query.trim().to_lowercase();if q.len()<2||q.len()>100{return vec![]}if let Some((a,b))=q.split_once(','){if let(Ok(lat),Ok(lon))=(a.trim().parse::<f64>(),b.trim().parse::<f64>()){if lat.is_finite()&&lon.is_finite()&&lat.abs()<=90.&&lon.abs()<=180.{return vec![json!({"id":"coordinates","name":format!("{lat:.4}, {lon:.4}"),"lat":lat,"lon":lon,"zone":"UTC"})]}}}
 if let Some(rows)=postal(&q){return rows}
 let tokens=q.split([' ',',']).filter(|t|!t.is_empty()).collect::<Vec<_>>();let mut results=vec![];
 for line in CITIES.lines(){let a=line.split('\t').collect::<Vec<_>>();if a.len()!=10{continue}let key=format!("{} {} {} {} {} {}",a[1],a[2],a[3],a[4],a[8],a[9]).to_lowercase();if tokens.iter().all(|q|key.contains(q)){if let(Ok(lat),Ok(lon))=(a[5].parse::<f64>(),a[6].parse::<f64>()){results.push(json!({"id":a[0],"name":format!("{}, {} · {}",a[1],a[9],a[3]),"lat":lat,"lon":lon,"zone":a[7]}));if results.len()>=15{break}}}}results}

pub fn service(args:&Value)->Result<Value>{let o=args.as_object().ok_or("Use city search arguments")?;if o.len()!=1{return Err("Use only a city query".into())}let q=args["query"].as_str().filter(|q|q.len()<=100&&!q.chars().any(char::is_control)).ok_or("Use a city name up to100characters")?;Ok(json!({"items":search(q)}))}
#[cfg(test)]mod tests{use super::*;#[test]fn offline_world_search_and_coordinates(){assert!(search("Chicago").iter().any(|p|p["zone"]=="America/Chicago"));assert!(search("London GB").iter().any(|p|p["zone"]=="Europe/London"));assert_eq!(search("40,-75")[0]["lat"],40.);assert!(search("91,200").is_empty());assert!(service(&json!({"query":"Chicago","url":"https://x"})).is_err());}}

#[cfg(test)]mod postal_tests{use super::*;
 #[test]fn us_zip_lookup_is_offline_preserves_zeroes_and_accepts_zip_plus_four(){for(q,zone,name)in [("60601","America/Chicago","Chicago"),("10001-1234","America/New_York","New York"),("94105","America/Los_Angeles","San Francisco"),("00501","America/New_York","Holtsville"),("96701","Pacific/Honolulu","Aiea"),("99501","America/Anchorage","Anchorage")]{let rows=search(q);assert_eq!(rows.len(),1,"{q}");assert_eq!(rows[0]["zone"],zone,"{q}");assert!(rows[0]["name"].as_str().unwrap().contains(name),"{q}");}assert!(search("00000").is_empty());assert!(search("60601-123").is_empty());assert!(search("60601junk").is_empty());}
}
