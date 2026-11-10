//! Signing happens on the developer's machine. No private publisher key is shipped.
use ed25519_dalek::{Signer,SigningKey};
use std::{fs,io::Write,path::Path};
fn main(){if let Err(e)=run(){eprintln!("{e}");std::process::exit(1)}}
fn run()->Result<(),Box<dyn std::error::Error>>{
 let args:Vec<String>=std::env::args().collect();
 if args.len()==3&&args[1]=="keygen"{let mut key=[0;32];getrandom::fill(&mut key).map_err(|e|format!("Could not generate publisher key: {e}"))?;let mut options=fs::OpenOptions::new();options.write(true).create_new(true);#[cfg(unix)]{use std::os::unix::fs::OpenOptionsExt;options.mode(0o600);}options.open(&args[2])?.write_all(&key)?;println!("Publisher key created. Keep it private and backed up.");return Ok(())}
 if args.len()!=6||args[1]!="pack"{return Err("Usage: onklav-pack keygen PRIVATE_KEY | pack MANIFEST_JSON MAIN_WASM PRIVATE_KEY OUTPUT.onklav-a".into())}
 let mut manifest:serde_json::Value=serde_json::from_slice(&fs::read(&args[2])?)?;let raw=fs::read(&args[4])?;let secret:[u8;32]=raw.try_into().map_err(|_|"Expected a 32-byte private publisher key")?;let key=SigningKey::from_bytes(&secret);manifest["publisher"]=serde_json::json!(key.verifying_key().as_bytes().iter().map(|b|format!("{b:02x}")).collect::<String>());
 let manifest=serde_json::to_vec(&manifest)?;let wasm=fs::read(&args[3])?;let sig=key.sign(&onklav_core::sdk::signature_message(&manifest,&wasm));let path=Path::new(&args[5]);if path.extension().and_then(|s|s.to_str())!=Some("onklav-a"){return Err("Use .onklav-a for OnKlav apps".into())}
 let mut zip=zip::ZipWriter::new(fs::File::create(path)?);let options=zip::write::SimpleFileOptions::default().compression_method(zip::CompressionMethod::Deflated).unix_permissions(0o644);
 for (name,bytes) in [("manifest.json",manifest.as_slice()),("main.wasm",wasm.as_slice()),("signature.ed25519",sig.to_bytes().as_slice())]{zip.start_file(name,options)?;zip.write_all(bytes)?;}zip.finish()?;println!("Created {}",path.display());Ok(())
}
