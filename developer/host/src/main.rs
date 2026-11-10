//! Desktop-only JSON-line harness around the same Rust broker used by Android.
//! It deliberately does not simulate Android services or permission dialogs.
use std::{fs,io::{self,BufRead,Read,Write},path::PathBuf};
use serde_json::{json,Value};
fn main() {
    if let Err(e)=run(){eprintln!("{e}");std::process::exit(1)}
}
fn run()->Result<(),Box<dyn std::error::Error>> {
    let args:Vec<_>=std::env::args_os().skip(1).collect();
    if args.len()!=1{return Err("Usage: onklav-sdk-host TEST_DATA_DIRECTORY (JSON lines on stdin)".into())}
    let root=PathBuf::from(&args[0]);fs::create_dir_all(&root)?;let root=root.canonicalize()?;
    let stdin=io::stdin();let mut input=stdin.lock();let mut out=io::stdout().lock();
    loop {
        let mut line=String::new();let n=Read::by_ref(&mut input).take(1_100_002).read_line(&mut line)?;
        if n==0 {break} if n>1_100_000{return Err("Request exceeds limits".into())}
        let response=(||->Result<Value,String>{
            let j:Value=serde_json::from_str(&line).map_err(|e|e.to_string())?;
            if j["action"]=="host_info" {Ok(json!({"build":onklav_core::sdk::HOST_BUILD,"source":option_env!("ONKLAV_RUNTIME_SOURCE").unwrap_or("")}))}
            else if j["action"]=="validate_form_schema" {
                let definition=onklav_ui::form_schema::FormDefinition::from_value(&j["schema"])?;
                Ok(json!({"fields":definition.fields.len(),"title":definition.title}))
            }
            else if j["action"]=="stage" {
                let path=PathBuf::from(j["path"].as_str().ok_or("Missing package path")?);
                let meta=fs::symlink_metadata(&path).map_err(|e|e.to_string())?;
                if !meta.is_file()||meta.len()>700_000{return Err("Invalid or oversized package".into())}
                let mut bytes=Vec::new();fs::File::open(path).map_err(|e|e.to_string())?.take(700_001).read_to_end(&mut bytes).map_err(|e|e.to_string())?;
                if bytes.len()>700_000{return Err("Package exceeds limit".into())}
                fs::write(root.join("app-install.zip"),bytes).map_err(|e|e.to_string())?;
                onklav_core::sdk::dispatch(&root,&json!({"action":"inspect"}))
            } else {onklav_core::sdk::dispatch(&root,&j)}
        })();
        let result=match response {Ok(v)=>json!({"ok":true,"value":v}),Err(e)=>json!({"ok":false,"error":e})};
        writeln!(out,"{result}")?;out.flush()?;
    }
    Ok(())
}
