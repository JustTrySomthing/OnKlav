//! Display resources resolved by the host's locale list. Inputs and IDs stay stable.
use crate::{Node,serde_json::json};
/// Attach translated display attributes to a node or frame. Keep its original
/// strings as the fallback. Exact locale, then parent language, then fallback.
/// This is an unreleased renderer addition; older hosts ignore these resources.
pub fn translated(mut node:Node,locale:&str,strings:&[(&str,&str)])->Result<Node,String>{
 if !node.is_object()||locale.is_empty()||locale.len()>63||locale.split('-').any(|p|p.is_empty()||!p.bytes().all(|c|c.is_ascii_alphanumeric())){return Err("Use an object and a bounded language tag such as es or ar-EG.".into())}
 let allowed=["title","text","hint","label","detail","long_label","drawer_title","menu_title","accept_label"];
 if strings.len()>allowed.len(){return Err("Too many translated attributes.".into())}
 let mut values=json!({});
 for (key,text) in strings{
  if !allowed.contains(key)||values.get(*key).is_some()||text.chars().any(|c|c.is_control()&&c!='\n'&&c!='\t')||text.encode_utf16().count()>if key.ends_with("title"){80}else{100000}{return Err("Translate display strings only; IDs, input values and actions must remain unchanged.".into())}
  values.as_object_mut().unwrap().insert((*key).into(),json!(text));
 }
 if node.get("i18n").is_none(){node["i18n"]=json!({});}
 let translations=node["i18n"].as_object_mut().ok_or("Translation resources must be an object.")?;
 if translations.len()>=32&&!translations.contains_key(locale){return Err("Use at most32 locale resources per node.".into())}
 translations.insert(locale.into(),values);
 Ok(node)
}

#[cfg(test)]mod tests{
 use super::*;
 #[test]fn translations_preserve_ids_values_and_fallback(){let n=translated(json!({"id":"name","kind":"field","hint":"Name","value":"draft"}),"ar",&[("hint","الاسم")]).unwrap();assert_eq!(n["id"],"name");assert_eq!(n["value"],"draft");assert_eq!(n["hint"],"Name");assert_eq!(n["i18n"]["ar"]["hint"],"الاسم");}
 #[test]fn translations_reject_program_data_and_oversized_resources(){for key in ["id","value","action","url"]{assert!(translated(json!({}),"es",&[(key,"bad")]).is_err());}assert!(translated(json!({}),"../bad",&[]).is_err());assert!(translated(json!({}),"es",&[("title",&"x".repeat(81))]).is_err());}
}
