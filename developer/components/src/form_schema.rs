//! A small declarative form schema, shared by the CLI and the Wasm app itself.
use crate::{forms::{self,FormField},serde_json::{self,Value},Screen,label};

pub struct FormDefinition { pub title:Option<String>,pub fields:Vec<FormField> }
fn keys(value:&Value,allowed:&[&str],path:&str)->Result<(),String>{
    let object=value.as_object().ok_or_else(||format!("{path}: expected an object"))?;
    if let Some(key)=object.keys().find(|key|!allowed.contains(&key.as_str())){return Err(format!("{path}.{key}: unknown property; allowed: {}",allowed.join(", ")))}
    Ok(())
}
fn string<'a>(value:&'a Value,key:&str,path:&str)->Result<&'a str,String>{value[key].as_str().ok_or_else(||format!("{path}.{key}: expected text"))}
impl FormDefinition {
    pub fn from_json(source:&str)->Result<Self,String>{
        if source.len()>65536{return Err("form schema: exceeds64KB".into())}
        let value:Value=serde_json::from_str(source).map_err(|e|format!("form schema: {e}"))?;
        Self::from_value(&value)
    }
    pub fn from_value(value:&Value)->Result<Self,String>{
        if value.to_string().len()>65536{return Err("form schema: exceeds64KB".into())}
        keys(value,&["schema","title","fields"],"form")?;
        if let Some(schema)=value.get("schema"){if schema!="onklav.form.v1"{return Err("form.schema: use onklav.form.v1".into())}}
        let title=match value.get("title"){
            None=>None,Some(v)=>{let title=v.as_str().filter(|s|!s.trim().is_empty()&&s.encode_utf16().count()<=80&&!s.chars().any(char::is_control)).ok_or("form.title: use1–80 visible UTF-16 units")?;Some(title.into())}
        };
        let source=value["fields"].as_array().ok_or("form.fields: expected an array of1–20 fields")?;
        if source.is_empty()||source.len()>20{return Err("form.fields: use1–20 fields".into())}
        let mut fields=Vec::new();
        for (index,value) in source.iter().enumerate(){
            let path=format!("form.fields[{index}]");
            keys(value,&["id","label","type","required","default","limit","options"],&path)?;
            let id=string(value,"id",&path)?;let label=string(value,"label",&path)?;
            let kind=match value.get("type"){None=>"text",Some(v)=>v.as_str().ok_or_else(||format!("{path}.type: expected text"))?};
            let mut field=match kind{
                "integer"=>FormField::integer(id,label),"decimal"=>FormField::decimal(id,label),"email"=>FormField::email(id,label),"phone"=>FormField::phone(id,label),"date"=>FormField::date(id,label),"time"=>FormField::time(id,label),
                "text"=>FormField::text(id,label),"multiline"=>FormField::multiline(id,label),"toggle"=>FormField::toggle(id,label,false),
                "choice"=>{
                    let options=value["options"].as_array().ok_or_else(||format!("{path}.options: expected1–20 choices"))?;
                    if options.is_empty()||options.len()>20{return Err(format!("{path}.options: use1–20 choices"))}
                    let mut choices=Vec::new();
                    for (n,option) in options.iter().enumerate(){
                        if let Some(text)=option.as_str(){choices.push((text,text));}
                        else {let op=format!("{path}.options[{n}]");keys(option,&["value","text"],&op)?;choices.push((string(option,"value",&op)?,string(option,"text",&op)?));}
                    }
                    FormField::choice(id,label,&choices)
                },_=>return Err(format!("{path}.type: unknown {kind:?}; use text, multiline, toggle, choice, integer, decimal, email, phone, date or time"))
            };
            if kind!="choice"&&value.get("options").is_some(){return Err(format!("{path}.options: only choice fields have options"))}
            if let Some(required)=value.get("required"){field.required=required.as_bool().ok_or_else(||format!("{path}.required: expected true or false"))?;}
            if let Some(limit)=value.get("limit"){
                if matches!(kind,"toggle"|"choice"){return Err(format!("{path}.limit: only text or multiline fields have a limit"))}
                let limit=limit.as_u64().filter(|n|(1..=1000).contains(n)).ok_or_else(||format!("{path}.limit: use an integer1–1000"))?;field.limit=limit as usize;
            }
            if let Some(default)=value.get("default"){field=field.default(default.clone());}
            fields.push(field);
        }
        if let Some(error)=forms::configuration_error(&fields){return Err(error)}
        Ok(Self{title,fields})
    }
}
/// Typical apps only edit the JSON fields. Existing Rust builder API remains available.
pub fn configured_form(title:&str,input:Value,source:&str)->Value{
    match FormDefinition::from_json(source){
        Ok(definition)=>forms::form(definition.title.as_deref().unwrap_or(title),input,&definition.fields),
        Err(error)=>Screen::new(title,input["state"].clone()).body(vec![label("form.error",&format!("Invalid form configuration: {error}"))]).finish()
    }
}
