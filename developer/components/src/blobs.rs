//! Unreleased private files/cache. Handles are scoped to the calling app.
//! Chunks are staging acknowledgements; finish publishes an immutable durable file.
use crate::*;
pub fn begin(name:&str,mime:&str,cache:bool,then:&str)->Value{effect("blobs.begin",json!({"name":name,"mime":mime,"cache":cache}),then)}
pub fn append(id:&str,offset:u64,hex:&str,then:&str)->Value{effect("blobs.append",json!({"id":id,"offset":offset,"hex":hex}),then)}
pub fn finish(id:&str,sha256:Option<&str>,then:&str)->Value{let mut args=json!({"id":id});if let Some(hash)=sha256{args["sha256"]=json!(hash);}effect("blobs.finish",args,then)}
pub fn info(id:&str,then:&str)->Value{effect("blobs.info",json!({"id":id}),then)}
pub fn read(id:&str,offset:u64,length:u32,then:&str)->Value{effect("blobs.read",json!({"id":id,"offset":offset,"length":length}),then)}
pub fn list(after:&str,then:&str)->Value{effect("blobs.list",json!({"after":after,"limit":40}),then)}
pub fn delete(id:&str,then:&str)->Value{effect("blobs.delete",json!({"id":id}),then)}
pub fn usage(then:&str)->Value{effect("blobs.usage",json!({}),then)}
pub fn clear_cache(then:&str)->Value{effect("blobs.clear_cache",json!({}),then)}
/// User-selected Android import; also requires files.external and a direct tap.
pub fn import(cache:bool,then:&str)->Value{effect("blobs.import",json!({"cache":cache}),then)}
/// User-selected Android export; also requires files.export and a direct tap.
pub fn export(id:&str,then:&str)->Value{effect("blobs.export",json!({"id":id}),then)}

/// Convenience for generated data; native Android import/export needs no chunk loop.
pub fn append_bytes(id:&str,offset:u64,bytes:&[u8],then:&str)->Result<Value,String>{
 if bytes.is_empty()||bytes.len()>65536{return Err("Append1–65536 bytes per chunk.".into())}
 const DIGITS:&[u8]=b"0123456789abcdef";let mut hex=String::with_capacity(bytes.len()*2);
 for b in bytes{hex.push(DIGITS[(b>>4)as usize]as char);hex.push(DIGITS[(b&15)as usize]as char);}
 Ok(append(id,offset,&hex,then))
}
/// Decode a read reply; an empty EOF chunk is valid. Invalid/oversized data fails.
pub fn read_bytes(value:&Value)->Result<Vec<u8>,String>{
 let text=value["hex"].as_str().ok_or("Missing file chunk.")?;
 if text.len()>131072||text.len()%2!=0||!text.bytes().all(|b|b.is_ascii_hexdigit()){return Err("Invalid file chunk.".into())}
 text.as_bytes().chunks_exact(2).map(|part|u8::from_str_radix(std::str::from_utf8(part).unwrap(),16).map_err(|e|e.to_string())).collect()
}
#[cfg(test)]mod tests{
 use super::*;
 #[test]fn typed_chunks_preserve_binary_values_and_reject_invalid_provider_data(){let bytes=[0,255,128,10];let effect=append_bytes("handle",0,&bytes,"done").unwrap();assert_eq!(read_bytes(&json!({"hex":effect["args"]["hex"]})).unwrap(),bytes);assert!(append_bytes("handle",0,&[],"done").is_err());for text in ["0","zz"]{assert!(read_bytes(&json!({"hex":text})).is_err());}assert_eq!(read_bytes(&json!({"hex":""})).unwrap(),Vec::<u8>::new());}
}
