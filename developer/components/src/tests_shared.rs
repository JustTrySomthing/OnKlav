use crate::*;
use serde_json::{json,Value};
fn call(f:Value,action:&str,value:&str)->Value{forms::form("Form",json!({"state":f["state"],"event":{"action":action,"value":value}}),&[forms::FormField::text("name","Name").required(),forms::FormField::toggle("enabled","Enabled",true)])}
#[test]fn form_validation_preserves_drafts_and_cancel_does_not_save(){
 let f=call(json!({"state":null}),"start","");let f=call(f,"form.save","");assert_eq!(f["state"]["status"],"Name is required.");assert!(f["effect"].is_null());
 let f=call(f,"form.field.name","Draft");let f=call(f,"form.back","");assert_eq!(f["confirm"]["accept_label"],"Discard");let f=call(f,"form.cancel","");assert_eq!(f["state"]["values"]["name"],"Draft");assert_eq!(f["state"]["dirty"],true);assert!(f["effect"].is_null());assert_eq!(f["scroll_body"],true);
}
#[test]fn unreadable_saved_form_cannot_be_silently_overwritten(){
 let f=forms::form("Form",json!({"state":null,"event":{"action":"form.loaded","reply":{"ok":true,"value":{"id":"a".repeat(32),"title":"Saved","text":"not valid JSON","updated":10}}}}),&[forms::FormField::text("name","Name").required(),forms::FormField::toggle("enabled","Enabled",true)]);
 assert_eq!(f["state"]["invalid_data"],true);let f=call(f,"form.save","");assert!(f["effect"].is_null());assert_eq!(f["state"]["doc"]["text"],"not valid JSON");let f=call(f,"form.reset","");assert_eq!(f["confirm"]["accept_label"],"Reset");let f=call(f,"form.accept","");assert_eq!(f["state"]["dirty"],true);assert_eq!(f["state"]["invalid_data"],false);assert!(f["effect"].is_null());
}
#[test]fn navigation_restores_and_bounds_route_history(){
 let mut n=navigation::Navigation::restore(&Value::Null,"home");assert!(!n.back());for i in 0..15{assert!(n.go(&format!("page{i}")));}assert!(n.go("page14"));assert!(!n.go("overflow"));let saved=n.state();let mut n=navigation::Navigation::restore(&saved,"home");assert_eq!(n.current(),"page14");assert!(n.back());assert_eq!(n.current(),"page13");
}
#[test]fn invalid_form_configuration_is_a_controlled_screen(){
 let f=forms::form("Form",json!({"state":null,"event":{"action":"start"}}),&[forms::FormField::text("duplicate","First"),forms::FormField::text("duplicate","Second")]);assert!(f["effect"].is_null());assert!(f["body"][0]["text"].as_str().unwrap().contains("Invalid form configuration"));
}
#[test]fn declarative_forms_use_existing_controller_and_defaults(){
 let source=r#"{"schema":"onklav.form.v1","fields":[{"id":"task","label":"Task","required":true},{"id":"done","label":"Done","type":"toggle","default":true},{"id":"priority","label":"Priority","type":"choice","options":["normal",{"value":"high","text":"High"}]}]}"#;
 let definition=form_schema::FormDefinition::from_json(source).unwrap();assert_eq!(definition.fields.len(),3);
 let f=form_schema::configured_form("Tasks",json!({"state":null,"event":{"action":"start"}}),source);assert_eq!(f["title"],"Tasks");assert_eq!(f["effect"]["service"],"records.current");assert_eq!(f["state"]["values"]["done"],true);assert_eq!(f["state"]["values"]["priority"],"normal");
}
#[test]fn declarative_mistakes_report_the_exact_property(){
 for (source,expected) in [
  (r#"{"fields":[{"id":"name","label":"Name","reqired":true}]}"#,"form.fields[0].reqired"),
  (r#"{"fields":[{"id":"name","label":"Name","type":"unknown"}]}"#,"form.fields[0].type"),
  (r#"{"fields":[{"id":"name","label":"Name","limit":0}]}"#,"form.fields[0].limit"),
  (r#"{"fields":[{"id":"name","label":"Name","default":false}]}"#,"form.fields[0].default"),
  (r#"{"fields":[{"id":"name","label":"Name","required":"yes"}]}"#,"form.fields[0].required"),
  (r#"{"fields":[{"id":"name","label":"Name","type":"choice","options":["same","same"]}]}"#,"form.fields[0].options[1].value"),
 ]{let error=form_schema::FormDefinition::from_json(source).err().unwrap();assert!(error.contains(expected),"{error}");}
}
#[test]fn builder_and_declarative_choice_validation_do_not_drift(){
 let fields=[forms::FormField::choice("p","Priority",&[("same","One"),("same","Two")])];
 let builder=forms::configuration_error(&fields).unwrap();let schema=form_schema::FormDefinition::from_json(r#"{"fields":[{"id":"p","label":"Priority","type":"choice","options":[{"value":"same","text":"One"},{"value":"same","text":"Two"}]}]}"#).err().unwrap();assert_eq!(builder,schema);
}
#[test]fn all_missing_fields_get_inline_errors_and_edits_clear_only_their_own(){
 let fields=[forms::FormField::text("first","First").required(),forms::FormField::text("second","Second").required()];
 let f=forms::form("Form",json!({"state":null,"event":{"action":"form.save"}}),&fields);assert_eq!(f["state"]["errors"]["first"],"First is required.");assert_eq!(f["state"]["errors"]["second"],"Second is required.");assert!(f["effect"].is_null());assert!(f["body"].as_array().unwrap().iter().any(|n|n["id"]=="form.error.first"));assert_eq!(f["body"].as_array().unwrap().iter().find(|n|n["id"]=="form.field.first").unwrap()["error"],"First is required.");assert_eq!(f["body"].as_array().unwrap().iter().find(|n|n["id"]=="form.error.first").unwrap()["heading"],true);
 let f=forms::form("Form",json!({"state":f["state"],"event":{"action":"form.field.first","value":"Draft"}}),&fields);assert!(f["state"]["errors"].get("first").is_none());assert_eq!(f["state"]["errors"]["second"],"Second is required.");assert_eq!(f["state"]["values"]["first"],"Draft");
}
#[test]fn invalid_choice_or_toggle_events_do_not_mutate_or_save(){
 let fields=[forms::FormField::toggle("enabled","Enabled",true),forms::FormField::choice("p","Priority",&[("normal","Normal")])];
 let f=forms::form("Form",json!({"state":null,"event":{"action":"form.field.enabled","value":"not a boolean"}}),&fields);assert_eq!(f["state"]["values"]["enabled"],true);assert_eq!(f["state"]["dirty"],false);assert!(f["effect"].is_null());
 let f=forms::form("Form",json!({"state":f["state"],"event":{"action":"form.field.p","value":"unknown"}}),&fields);assert_eq!(f["state"]["values"]["p"],"normal");assert_eq!(f["state"]["dirty"],false);assert!(f["effect"].is_null());
}
#[test]fn common_input_types_validate_and_supply_typed_data_without_losing_drafts(){
 use forms::{FormField,InputKind};
 let source=r#"{"fields":[{"id":"count","label":"Count","type":"integer","default":3},{"id":"cost","label":"Cost","type":"decimal"},{"id":"day","label":"Day","type":"date"},{"id":"at","label":"At","type":"time"}]}"#;
 let d=form_schema::FormDefinition::from_json(source).unwrap();assert_eq!(d.fields[0].default,"3");
 let data=forms::typed_data(&d.fields,&json!({"count":"42","cost":"1.25","day":"2024-02-29","at":"23:59"})).unwrap();assert_eq!(data["count"],42);assert_eq!(data["cost"],1.25);assert_eq!(data["day"],"2024-02-29");
 for (kind,value) in [(InputKind::Integer,"2.5"),(InputKind::Decimal,"NaN"),(InputKind::Decimal,"inf"),(InputKind::Date,"2023-02-29"),(InputKind::Date,"2024-04-31"),(InputKind::Time,"24:00"),(InputKind::Email,"missing-address"),(InputKind::Phone,"words")]{assert!(kind.check(value).is_err(),"{kind:?}: {value}");}
 let f=forms::form("Numbers",json!({"state":null,"event":{"action":"form.field.count","value":"2.5"}}),&[FormField::integer("count","Count")]);let f=forms::form("Numbers",json!({"state":f["state"],"event":{"action":"form.save"}}),&[FormField::integer("count","Count")]);assert_eq!(f["state"]["values"]["count"],"2.5");assert!(f["effect"].is_null());assert!(f["state"]["data"].is_null());assert!(f["state"]["errors"]["count"].is_string());assert_eq!(f["body"][1]["input_kind"],"integer");
}
#[test]fn date_and_time_boundaries_include_leap_years_and_reject_unicode_without_panics(){
 for good in ["2000-02-29","2024-02-29","0001-01-01","9999-12-31"]{assert!(form_values::valid_date(good));}
 for bad in ["1900-02-29","0000-01-01","2024-13-01","2024-00-01","2024-01-00","éaa-01-01","2024-🐈-1","","2024-1-1"]{assert!(!form_values::valid_date(bad));}
 for good in ["00:00","23:59","09:03"]{assert!(form_values::valid_time(good));}
 for bad in ["24:00","12:60","1:30","é:00","12:é",""]{assert!(!form_values::valid_time(bad));}
}

#[test]fn text_editor_normalizes_and_preserves_uniform_original_line_endings(){
 for (style,raw) in [("crlf","one\r\ntwo\r\n"),("cr","one\rtwo\r"),("lf","one\ntwo\n")]{let file=json!({"id":"opaque","revision":"read-revision","text":raw,"line_ending":style,"utf8_bom":true});let draft=files::text_for_editor(&file);assert_eq!(draft,"one\ntwo\n");let effect=files::write_text(&file,&draft,"saved");assert_eq!(effect["args"]["text"],raw);assert_eq!(effect["args"]["revision"],"read-revision");assert_eq!(effect["args"]["utf8_bom"],true);}
}

#[test]fn form_migration_preserves_document_and_waits_for_explicit_save(){
 fn migrate(mut value:Value)->Result<Value,String>{let name=value.as_object_mut().ok_or("Expected fields")?.remove("old_name").ok_or("Missing old name")?;value["name"]=name;Ok(value)}
 let fields=[forms::FormField::text("name","Name").required()];let doc=json!({"id":"existing","title":"Original","text":r#"{"old_name":"Keep name","extra":7}"#});
 let frame=forms::form_with_migration("Form",json!({"state":null,"event":{"action":"form.loaded","reply":{"ok":true,"value":doc}}}),&fields,migrate);
 assert_eq!(frame["state"]["values"]["name"],"Keep name");assert_eq!(frame["state"]["values"]["extra"],7);assert_eq!(frame["state"]["doc"],doc);assert_eq!(frame["state"]["dirty"],true);assert!(frame["effect"].is_null());
 let back=forms::form_with_migration("Form",json!({"state":frame["state"],"event":{"action":"form.back"}}),&fields,migrate);assert_eq!(back["confirm"]["accept_label"],"Discard");
 let save=forms::form_with_migration("Form",json!({"state":frame["state"],"event":{"action":"form.save"}}),&fields,migrate);assert_eq!(save["effect"]["service"],"records.save");let saved:Value=serde_json::from_str(save["effect"]["args"]["note"]["text"].as_str().unwrap()).unwrap();assert_eq!(saved["name"],"Keep name");assert_eq!(saved["extra"],7);
}
#[test]fn failed_or_invalid_form_migration_blocks_saving_without_modifying_saved_data(){
 let fields=[forms::FormField::toggle("enabled","Enabled",false)];let doc=json!({"id":"existing","text":"{\"legacy\":true}"});
 for migrate in [(|_|Err("Unknown legacy format".into())) as forms::FormMigration,|_|Ok(json!({"enabled":"not boolean"})),|_|Ok(json!({"extra":"x".repeat(80000)}))]{
  let frame=forms::form_with_migration("Form",json!({"state":null,"event":{"action":"form.loaded","reply":{"ok":true,"value":doc}}}),&fields,migrate);assert_eq!(frame["state"]["invalid_data"],true);assert_eq!(frame["state"]["doc"],doc);
  let save=forms::form_with_migration("Form",json!({"state":frame["state"],"event":{"action":"form.save"}}),&fields,migrate);assert!(save["effect"].is_null());
 }
}
