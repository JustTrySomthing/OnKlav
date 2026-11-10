//! Explicit, bounded JSON record upgrades. Reading never writes the user's document.
use crate::serde_json::{self,json,Value};

pub struct Migration { pub from:u32,pub apply:fn(Value)->Result<Value,String> }
pub struct RecordSchema<'a> { id:&'a str,version:u32,migrations:&'a [Migration] }
pub struct Record { pub data:Value,pub version:u32,pub migrated:bool }
const MAX_UNITS:usize=80000;
fn bounded(value:&Value)->Result<(),String>{
 if !value.is_object(){return Err("Record data must be a JSON object.".into())}
 if value.to_string().encode_utf16().count()>MAX_UNITS{return Err("Record exceeds the private document limit.".into())}Ok(())
}
fn parse(text:&str)->Result<Value,String>{
 if text.len()>MAX_UNITS*4||text.encode_utf16().count()>MAX_UNITS{return Err("Record exceeds the private document limit.".into())}
 serde_json::from_str(text).map_err(|e|format!("Could not read record: {e}"))
}
impl<'a> RecordSchema<'a> {
 pub fn new(id:&'a str,version:u32,migrations:&'a [Migration])->Result<Self,String>{
  if id.is_empty()||id.len()>128||!id.chars().all(|c|c.is_ascii_alphanumeric()||"._-".contains(c)){return Err("Use a stable schema ID of1–128 ASCII letters, digits, dots, underscores or hyphens.".into())}
  if version==0||migrations.len()>32{return Err("Use a positive schema version and at most32 migrations.".into())}
  let mut seen=std::collections::BTreeSet::new();
  for m in migrations{if m.from>=version||!seen.insert(m.from){return Err("Migration source versions must be unique and below the current version.".into())}}
  Ok(Self{id,version,migrations})
 }
 /// Read the named versioned format; future/foreign versions fail closed.
 pub fn read(&self,text:&str)->Result<Record,String>{
  let value=parse(text)?;
  if value["format"]!="onklav.record.v1"||value["schema"]!=self.id{return Err("Record format or schema does not match this app.".into())}
  let version=value["version"].as_u64().filter(|v|*v>0&&*v<=u32::MAX as u64).ok_or("Record has an invalid schema version.")? as u32;
  self.upgrade(value["data"].clone(),version)
 }
 /// Opt into a known unwrapped legacy format. Never guess its schema/version.
 pub fn read_legacy(&self,text:&str,version:u32)->Result<Record,String>{
  let value=parse(text)?;
  if value.get("format").is_some(){return Err("This is a wrapped record; use read instead of legacy import.".into())}
  self.upgrade(value,version)
 }
 fn upgrade(&self,mut data:Value,mut version:u32)->Result<Record,String>{
  bounded(&data)?;
  if version>self.version{return Err("This record needs a newer app. Its saved data is unchanged.".into())}
  let original=version;
  if self.version-version>32{return Err("No bounded migration path exists for this record.".into())}
  while version<self.version{
   let migration=self.migrations.iter().find(|m|m.from==version).ok_or_else(||format!("Missing migration from schema version{version}. Saved data is unchanged."))?;
   data=(migration.apply)(data).map_err(|e|format!("Migration from version{version} failed: {e}"))?;
   bounded(&data)?;version+=1;
  }
  Ok(Record{data,version,migrated:version!=original})
 }
 /// Serialize current data for an explicit Save action; performs no host call.
 pub fn encode(&self,data:&Value)->Result<String,String>{
  bounded(data)?;
  let text=json!({"format":"onklav.record.v1","schema":self.id,"version":self.version,"data":data}).to_string();
  if text.encode_utf16().count()>MAX_UNITS{return Err("Record and schema metadata exceed the private document limit.".into())}Ok(text)
 }
}

#[cfg(test)]mod tests{
 use super::*;
 fn rename(mut data:Value)->Result<Value,String>{if let Some(name)=data.as_object_mut().unwrap().remove("old_name"){data["name"]=name;}Ok(data)}
 fn add(mut data:Value)->Result<Value,String>{data["enabled"]=json!(true);Ok(data)}
 #[test]fn legacy_upgrades_in_order_preserve_unknown_values_and_need_explicit_save(){
  let steps=[Migration{from:0,apply:rename},Migration{from:1,apply:add}];let schema=RecordSchema::new("org.example.record",2,&steps).unwrap();let raw=r#"{"old_name":"Keep me","extra":17}"#;let record=schema.read_legacy(raw,0).unwrap();assert!(record.migrated);assert_eq!(record.data,json!({"name":"Keep me","extra":17,"enabled":true}));assert!(raw.contains("old_name"));let encoded=schema.encode(&record.data).unwrap();let reopened=schema.read(&encoded).unwrap();assert!(!reopened.migrated);assert_eq!(reopened.data,record.data);
 }
 #[test]fn foreign_future_missing_and_failing_migrations_are_errors(){
  let schema=RecordSchema::new("org.example.record",2,&[]).unwrap();
  for raw in [r#"{"format":"onklav.record.v1","schema":"org.other","version":2,"data":{}}"#,r#"{"format":"onklav.record.v1","schema":"org.example.record","version":3,"data":{}}"#,r#"{"format":"onklav.record.v1","schema":"org.example.record","version":1,"data":{}}"#]{assert!(schema.read(raw).is_err());}
  let steps=[Migration{from:1,apply:|_|Err("unsupported old data".into())}];let schema=RecordSchema::new("org.example.record",2,&steps).unwrap();assert!(schema.read_legacy("{}",1).err().unwrap().contains("unsupported old data"));
 }
 #[test]fn record_bounds_and_migration_configuration_are_checked(){
  let schema=RecordSchema::new("org.example.record",1,&[]).unwrap();assert!(schema.encode(&json!({"large":"x".repeat(80000)})).is_err());assert!(schema.read_legacy("[]",1).is_err());assert!(schema.read_legacy("not json",1).is_err());assert!(schema.read_legacy(r#"{"format":"other"}"#,1).is_err());assert!(RecordSchema::new("",1,&[]).is_err());assert!(RecordSchema::new("valid",0,&[]).is_err());let steps=[Migration{from:0,apply:rename},Migration{from:0,apply:add}];assert!(RecordSchema::new("valid",1,&steps).is_err());
 }
}
