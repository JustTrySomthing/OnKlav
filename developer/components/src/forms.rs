//! Declarative persisted forms: fields, validation, defaults, Save and draft recovery.
use crate::*;
pub use crate::form_values::InputKind;
use serde_json::{json,Value};
#[derive(Clone)]pub enum FieldKind{Text,Multiline,Toggle,Choice}
#[derive(Clone)]pub struct FormField{pub id:String,pub label:String,pub kind:FieldKind,pub required:bool,pub default:Value,pub limit:usize,pub options:Vec<(String,String)>,pub input:InputKind}
impl FormField{
 pub fn text(id:&str,label:&str)->Self{Self{id:id.into(),label:label.into(),kind:FieldKind::Text,required:false,default:json!(""),limit:80,options:vec![],input:InputKind::Text}}
 pub fn multiline(id:&str,label:&str)->Self{Self{kind:FieldKind::Multiline,limit:1000,..Self::text(id,label)}}
 pub fn toggle(id:&str,label:&str,value:bool)->Self{Self{kind:FieldKind::Toggle,default:json!(value),..Self::text(id,label)}}
 pub fn choice(id:&str,label:&str,options:&[(&str,&str)])->Self{Self{kind:FieldKind::Choice,default:json!(options.first().map(|v|v.0).unwrap_or("")),options:options.iter().map(|(v,t)|(v.to_string(),t.to_string())).collect(),..Self::text(id,label)}}
 pub fn input(mut self,input:InputKind)->Self{self.input=input;self}
 pub fn integer(id:&str,label:&str)->Self{Self::text(id,label).input(InputKind::Integer)}
 pub fn decimal(id:&str,label:&str)->Self{Self::text(id,label).input(InputKind::Decimal)}
 pub fn email(id:&str,label:&str)->Self{Self::text(id,label).input(InputKind::Email)}
 pub fn phone(id:&str,label:&str)->Self{Self::text(id,label).input(InputKind::Phone)}
 pub fn date(id:&str,label:&str)->Self{Self::text(id,label).input(InputKind::Date)}
 pub fn time(id:&str,label:&str)->Self{Self::text(id,label).input(InputKind::Time)}
 pub fn required(mut self)->Self{self.required=true;self}
 pub fn limit(mut self,limit:usize)->Self{self.limit=limit.clamp(1,1000);self}
 pub fn default(mut self,value:Value)->Self{self.default=if matches!(self.input,InputKind::Integer|InputKind::Decimal)&&value.is_number(){json!(value.to_string())}else{value};self}
}
/// The same constraints apply to Rust builders, declarative files and CLI preflight.
pub fn configuration_error(fields:&[FormField])->Option<String>{
 if fields.is_empty()||fields.len()>20{return Some("form.fields: use1–20 fields".into())}
 let mut ids=std::collections::BTreeSet::new();
 for (i,f) in fields.iter().enumerate(){
  let path=format!("form.fields[{i}]");
  if f.id.is_empty()||f.id.len()>60||!f.id.chars().all(|c|c.is_ascii_alphanumeric()||c=='_'||c=='-'){return Some(format!("{path}.id: use1–60 ASCII letters/digits, underscore or hyphen"))}
  if !ids.insert(&f.id){return Some(format!("{path}.id: duplicate field {:?}",f.id))}
  if f.label.trim().is_empty()||f.label.encode_utf16().count()>80||f.label.chars().any(char::is_control){return Some(format!("{path}.label: use1–80 visible UTF-16 units"))}
  if !matches!(f.kind,FieldKind::Text)&&f.input!=InputKind::Text{return Some(format!("{path}.type: typed inputs require a single-line text field"))}
  if f.input==InputKind::Date&&f.limit<10||f.input==InputKind::Time&&f.limit<5{return Some(format!("{path}.limit: too short for the selected date/time format"))}
  match f.kind{
   FieldKind::Toggle=>if !f.default.is_boolean(){return Some(format!("{path}.default: toggle default must be true or false"))},
   FieldKind::Choice=>{
    if f.options.is_empty()||f.options.len()>20{return Some(format!("{path}.options: use1–20 choices"))}
    let mut values=std::collections::BTreeSet::new();
    for (n,(value,text)) in f.options.iter().enumerate(){
     if value.is_empty()||value.encode_utf16().count()>1000||text.trim().is_empty()||text.encode_utf16().count()>80{return Some(format!("{path}.options[{n}]: choice value/text is empty or too long"))}
     if !values.insert(value){return Some(format!("{path}.options[{n}].value: duplicate choice {value:?}"))}
    }
    if !f.options.iter().any(|(value,_)|f.default==value.as_str()){return Some(format!("{path}.default: must match one choice value"))}
   },
   _=>{
    if !(1..=1000).contains(&f.limit){return Some(format!("{path}.limit: use1–1000 UTF-16 units"))}
    if f.default.as_str().is_none_or(|v|v.encode_utf16().count()>f.limit||plain(v,f.limit)!=v){return Some(format!("{path}.default: use text within this field's limit"))}
    if let Err(error)=f.input.check(f.default.as_str().unwrap()){return Some(format!("{path}.default: {error}"))}
   }
  }
 }
 None
}
/// Returns typed numbers/booleans and checked text, without writing data.
/// `values` remains the editable draft; callers must still inspect `dirty`/Save.
pub fn typed_data(fields:&[FormField],values:&Value)->Result<Value,std::collections::BTreeMap<String,String>>{
 let mut data=values.clone();if !data.is_object(){data=json!({});}let mut errors=std::collections::BTreeMap::new();
 for f in fields{
  let value=&values[&f.id];let text=value.as_str().unwrap_or("");
  let missing=f.required&&if matches!(f.kind,FieldKind::Toggle){value!=true}else{text.trim().is_empty()};
  if missing{errors.insert(f.id.clone(),format!("{} is required.",f.label));continue}
  let result=match f.kind{FieldKind::Toggle=>if value.is_boolean(){Ok(value.clone())}else{Err("Choose true or false.")},FieldKind::Choice=>if f.options.iter().any(|(v,_)|value==v.as_str()){Ok(value.clone())}else{Err("Choose a supported value.")},_=>f.input.check(text)};
  match result{Ok(value)=>data[&f.id]=value,Err(error)=>{errors.insert(f.id.clone(),error.into());}}
 }
 if errors.is_empty(){Ok(data)}else{Err(errors)}
}
fn defaults(fields:&[FormField])->Value{let mut v=json!({});for f in fields{v[&f.id]=f.default.clone();}v}
fn normalized(fields:&[FormField],value:&Value)->Option<Value>{
 if !value.is_object(){return None}let mut values=value.clone();
 for f in fields{if values.get(&f.id).is_none(){values[&f.id]=f.default.clone();}let v=&values[&f.id];match f.kind{FieldKind::Toggle=>if !v.is_boolean(){return None},FieldKind::Choice=>if !f.options.iter().any(|(k,_)|v==k.as_str()){return None},_=>{let s=v.as_str()?;if s.encode_utf16().count()>f.limit{return None}values[&f.id]=json!(plain(s,f.limit));}}}Some(values)
}
pub type FormMigration=fn(Value)->Result<Value,String>;
/// Existing forms keep their saved format and require no migration callback.
pub fn form(title:&str,input:Value,fields:&[FormField])->Value{form_inner(title,input,fields,None)}
/// Upgrade a known saved field map on load. A failed migration blocks Save;
/// a successful change remains a draft until the user explicitly saves it.
pub fn form_with_migration(title:&str,input:Value,fields:&[FormField],migrate:FormMigration)->Value{form_inner(title,input,fields,Some(migrate))}
fn form_inner(title:&str,input:Value,fields:&[FormField],migrate:Option<FormMigration>)->Value{
 if let Some(error)=configuration_error(fields){return Screen::new(title,json!({})).body(vec![label("form.error",&format!("Invalid form configuration: {error}"))]).finish()}
 let mut s=input["state"].clone();if !s.is_object(){s=json!({"doc":{},"values":defaults(fields),"dirty":false,"invalid_data":false,"status":"Opening…","confirm":null,"errors":{}});}
 if !s["errors"].is_object(){s["errors"]=json!({});}
 let e=&input["event"];let a=e["action"].as_str().unwrap_or("");let mut req=Value::Null;let mut close=false;
 match a{
 "start"=>req=services::current("form.loaded"),
 "form.loaded" if e["reply"]["ok"]==true=>{
  s["doc"]=e["reply"]["value"].clone();let text=s["doc"]["text"].as_str().unwrap_or("");let mut changed=false;
  let parsed=if text.is_empty(){Ok(defaults(fields))}else{serde_json::from_str::<Value>(text).map_err(|_|"Could not read saved form data.".to_string()).and_then(|original|{
   let updated=if let Some(migrate)=migrate{migrate(original.clone())?}else{original.clone()};
   if updated.to_string().encode_utf16().count()>80000{return Err("Migrated form data exceeds the private document limit.".into())}
   let values=normalized(fields,&updated).ok_or("Saved data does not match this form's fields.")?;changed=values!=original;Ok(values)
  })};
  match parsed{Ok(values)=>{s["values"]=values;s["status"]=json!(if changed{"Data updated for this app. Save to keep changes."}else{"Ready"});s["invalid_data"]=json!(false);},Err(error)=>{s["invalid_data"]=json!(true);s["status"]=json!(format!("{error} Saved data is unchanged. Reset explicitly to start over."));}}
  s["dirty"]=json!(changed&&s["invalid_data"]!=true);s["errors"]=json!({});
 },
 _ if a.starts_with("form.field.")=>{if let Some(f)=fields.iter().find(|f|f.id==a[11..]){
  let value=e["value"].as_str().unwrap_or("");
  let accepted=match f.kind{FieldKind::Toggle=>matches!(value,"true"|"false"),FieldKind::Choice=>f.options.iter().any(|(v,_)|v==value),_=>true};
  if accepted{
   s["values"][&f.id]=if matches!(f.kind,FieldKind::Toggle){json!(value=="true")}else{json!(plain(value,f.limit))};
   s["errors"].as_object_mut().unwrap().remove(&f.id);s["dirty"]=json!(true);s["status"]=json!("Unsaved changes");
  }else{s["errors"][&f.id]=json!(format!("Choose a supported value for {}.",f.label));s["status"]=s["errors"][&f.id].clone();}
 }},
 "form.save"=>{
  s["errors"]=json!({});
  if let Err(errors)=typed_data(fields,&s["values"]){for (id,error) in errors{s["errors"][&id]=json!(error);}}
  if s["invalid_data"]==true{s["status"]=json!("Reset unreadable data explicitly before saving.");}
  else if let Some(error)=fields.iter().find_map(|f|s["errors"][&f.id].as_str()){s["status"]=json!(error);}
  else{
   let text=s["values"].to_string();
   if text.encode_utf16().count()>80000{s["status"]=json!("This form is too large to save. Shorten the values first.");}
   else{let mut doc=s["doc"].clone();let name=fields.iter().filter(|f|matches!(f.kind,FieldKind::Text)).find_map(|f|s["values"][&f.id].as_str().filter(|v|!v.trim().is_empty())).unwrap_or(title);doc["title"]=json!(plain(name,80));doc["text"]=json!(text);req=services::save(&doc,"form.saved");}
  }
 },
 "form.saved" if e["reply"]["ok"]==true=>{s["doc"]=e["reply"]["value"].clone();s["dirty"]=json!(false);s["status"]=json!("Saved");s["errors"]=json!({});},
 "form.reset"=>s["confirm"]=json!("reset"),
 "form.back"=>if s["dirty"]==true{s["confirm"]=json!("back")}else{close=true},
 "form.cancel"=>s["confirm"]=Value::Null,
 "form.accept"=>{if s["confirm"]=="reset"{s["values"]=defaults(fields);s["errors"]=json!({});s["dirty"]=json!(true);s["invalid_data"]=json!(false);s["status"]=json!("Unsaved changes");}else{close=true;}s["confirm"]=Value::Null;},_=>{}
 }
 if e["reply"]["ok"]==false{s["status"]=e["reply"]["error"].clone();}
 s["data"]=typed_data(fields,&s["values"]).unwrap_or(Value::Null);
 let mut nodes=vec![];for f in fields{let id=format!("form.field.{}",f.id);let value=&s["values"][&f.id];if matches!(f.kind,FieldKind::Text|FieldKind::Multiline){nodes.push(label(&format!("form.label.{}",f.id),&format!("{}{}",f.label,if f.required{" (required)"}else{""})));}let mut n=match f.kind{FieldKind::Text=>field(&id,&f.label,value.as_str().unwrap_or("")),FieldKind::Multiline=>editor(&id,&f.label,value.as_str().unwrap_or("")),FieldKind::Toggle=>toggle(&id,&f.label,value==true),FieldKind::Choice=>{let opts=f.options.iter().map(|(v,t)|(v.as_str(),t.as_str())).collect::<Vec<_>>();choices("select",&id,&f.label,value.as_str().unwrap_or(""),&opts)}};if matches!(f.kind,FieldKind::Text|FieldKind::Multiline){n["limit"]=json!(f.limit);n["input_kind"]=json!(f.input.name());}if let Some(error)=s["errors"][&f.id].as_str(){n["error"]=json!(error);}if s["invalid_data"]==true{n=disabled(n);}nodes.push(n);if let Some(error)=s["errors"][&f.id].as_str(){nodes.push(heading(&format!("form.error.{}",f.id),error));}}
 nodes.push(label("form.status",s["status"].as_str().unwrap_or("")));nodes.push(primary_button("form.save","Save").enabled(s["invalid_data"]!=true));
 let mut f=Screen::new(title,s.clone()).body(nodes).scrollable().actions(vec![icon("form.reset","Reset form","refresh")]).effect(req).back("form.back");if s["confirm"].is_string(){let reset=s["confirm"]=="reset";f=f.confirm_labeled(if reset{"Reset form?"}else{"Discard changes?"},if reset{"Load defaults? Saved data changes only after Save."}else{"Your saved form will be kept."},"form.accept","form.cancel",if reset{"Reset"}else{"Discard"});}if close{f=f.close()}f.finish()
}
