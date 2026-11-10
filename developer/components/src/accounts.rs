//! Working host-managed accounts; credentials never enter guest state.
use crate::*;
pub fn connect(provider:&str,then:&str)->Value{effect("accounts.connect",json!({"provider":provider}),then)}
pub fn list(then:&str)->Value{effect("accounts.list",json!({}),then)}
pub fn refresh(account:&str,then:&str)->Value{effect("accounts.refresh",json!({"account":account}),then)}
pub fn disconnect(account:&str,then:&str)->Value{effect("accounts.disconnect",json!({"account":account}),then)}
/// Adds an opaque account to an existing http.request; the host attaches credentials.
pub fn authenticated(mut request:Value,account:&str)->Value{request["args"]["account"]=json!(account);request}
