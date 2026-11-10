//! Unreleased app-private structured storage. Requires storage.private.
//! Transactions compare revisions; callers must handle conflicts without blind retry.
use crate::*;
pub fn info(name:&str,then:&str)->Value{effect("collections.info",json!({"name":name}),then)}
pub fn usage(then:&str)->Value{effect("collections.usage",json!({}),then)}
pub fn get(name:&str,id:&str,then:&str)->Value{effect("collections.get",json!({"name":name,"id":id}),then)}
pub fn page(name:&str,after:&str,revision:Option<u64>,then:&str)->Value{
 let mut args=json!({"name":name,"after":after,"limit":40});if let Some(rev)=revision{args["expected_revision"]=json!(rev);}effect("collections.query",args,then)
}
pub fn query(name:&str,index:&str,value:Value,after:&str,revision:Option<u64>,then:&str)->Value{
 let mut request=page(name,after,revision,then);request["args"]["index"]=json!(index);request["args"]["value"]=value;request
}
pub fn put(id:&str,data:Value)->Value{json!({"operation":"put","id":id,"data":data})}
pub fn delete(id:&str)->Value{json!({"operation":"delete","id":id})}
/// One atomic collection change, at most64 operations. Schema/index migration
/// is explicit and part of that same commit. Preserve drafts if it fails.
pub fn transact(name:&str,revision:u64,schema:u32,indexes:Option<&[&str]>,operations:Vec<Value>,then:&str)->Value{
 let mut args=json!({"name":name,"expected_revision":revision,"schema":schema,"operations":operations});
 if let Some(fields)=indexes{args["indexes"]=json!(fields);}effect("collections.transact",args,then)
}
/// Clear rows while retaining the collection revision/schema/index definitions.
pub fn clear(name:&str,revision:u64,then:&str)->Value{effect("collections.clear",json!({"name":name,"expected_revision":revision}),then)}

/// Create a private backup file; export it through blobs::export for user-selected destinations.
pub fn backup(name:&str,revision:u64,then:&str)->Value{effect("collections.backup",json!({"name":name,"expected_revision":revision}),then)}
/// Explicit atomic replacement from a private imported backup handle.
pub fn restore(name:&str,revision:u64,schema:u32,blob:&str,then:&str)->Value{effect("collections.restore",json!({"name":name,"expected_revision":revision,"schema":schema,"blob":blob}),then)}
