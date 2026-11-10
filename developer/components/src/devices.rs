//! Foreground, revocable device helpers. No addresses, provider IDs or system paths.
use crate::*;
pub fn connectivity(then:&str)->Value{effect("connectivity.read",json!({}),then)}
pub fn settings(target:&str,then:&str)->Value{effect("device.settings",json!({"target":target}),then)}
pub fn location(then:&str)->Value{effect("location.current",json!({}),then)}
pub fn manual_location(latitude:f64,longitude:f64,then:&str)->Value{effect("location.manual",json!({"latitude":latitude,"longitude":longitude}),then)}
pub fn torch(enabled:bool,seconds:u32,then:&str)->Value{effect("torch.set",json!({"enabled":enabled,"seconds":seconds}),then)}
pub fn ble_select(service:&str,then:&str)->Value{effect("ble.select",json!({"service":service}),then)}
pub fn ble_connect(then:&str)->Value{effect("ble.connect",json!({}),then)}
pub fn ble_read(characteristic:&str,then:&str)->Value{effect("ble.read",json!({"characteristic":characteristic}),then)}
pub fn ble_write(characteristic:&str,hex:&str,then:&str)->Value{effect("ble.write",json!({"characteristic":characteristic,"hex":hex}),then)}
/// Wait for one selected-characteristic notification, at most15 seconds.
pub fn ble_notify(characteristic:&str,then:&str)->Value{effect("ble.notify",json!({"characteristic":characteristic}),then)}
pub fn ble_disconnect(then:&str)->Value{effect("ble.disconnect",json!({}),then)}
/// Android reviews the requested SSID; OnKlav never saves the passphrase.
pub fn wifi_join(ssid:&str,passphrase:&str,then:&str)->Value{effect("wifi.join",json!({"ssid":ssid,"passphrase":passphrase}),then)}
pub fn wifi_disconnect(then:&str)->Value{effect("wifi.disconnect",json!({}),then)}
pub fn lan_select(service_type:&str,then:&str)->Value{effect("lan.select",json!({"type":service_type}),then)}
/// A single bounded TCP request/response to the host-selected local endpoint.
pub fn lan_exchange(hex:&str,then:&str)->Value{effect("lan.exchange",json!({"hex":hex}),then)}
pub fn lan_disconnect(then:&str)->Value{effect("lan.disconnect",json!({}),then)}
