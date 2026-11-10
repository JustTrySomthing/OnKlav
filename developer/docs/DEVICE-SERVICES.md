# Scoped device services (working source)

Current target: [OnKlav 0.100.42/code151, guest protocol96](CURRENT-HOST.md). Phase-specific “working/unreleased” wording below denotes preview qualification, not absence from this APK.
These are implemented preview APIs in the current reference host, rather than the older baseline host. Physical device/provider qualification remains required. `devices` helpers create standard broker effects; app grants
are explicit and revocable. Each native authorization carries the signed app,
publisher and current host session. Guest-supplied owner/address/port fields fail.

- `device.connectivity`: `connectivity.read` returns transport, connection,
  internet/validation/captive-portal, metering and roaming flags. No SSID, BSSID,
  device address or telephone identity.
- `device.settings`: `device.settings` opens only internet, Wi-Fi, Bluetooth or
  location settings after a tap. It does not toggle a radio.
- `location.foreground`: `location.current` offers approximate Android location
  or manually entered coordinates. Missing permission/provider/15-second timeout
  returns explicit manual-fallback feedback. No background permission or cached
  location history. `location.manual` validates coordinates entered in app UI.
- `device.torch`: `torch.set` holds one session-owned lease for1–120 seconds.
  Closing/backgrounding, permission changes, removal and timeout release it.
- `nearby.bluetooth`: `ble.select` scans for a full128-bit service UUID for8
  seconds, bounds results at20 and asks the user to select a device. Selection
  lasts120 seconds. `ble.connect` tests connection/service discovery; read,
  write-with-response and one-notification transactions each reconnect, discover
  the selected service, and close GATT after completion/failure/15 seconds.
  Payloads are at most512 bytes. This is deliberately a bounded transaction API,
  not a persistent notification subscription. Android permission/hardware failure
  is reported; no background scan, pairing automation or address export.
- `nearby.wifi`: `wifi.join` asks Android to review a specific SSID (open or WPA2
  passphrase). An approved network lease lasts120 seconds; no process-wide
  network binding, saved credential or automatic reconnect. Disconnect, loss,
  close/background, revocation and removal unregister the request.
- `network.lan`: separate permission. `lan.select` discovers a requested DNS-SD
  TCP service type for5 seconds (20 choices), asks the user to select one, and
  resolves a private/link-local endpoint. Public, multicast, loopback and wildcard
  targets are refused. `lan.exchange` sends at most4096 bytes and reads one chunk
  of at most4096 bytes with5-second connect/read deadlines. The caller handles
  protocol framing and encryption. Guest code cannot supply a host/port. A chosen
  temporary Wi-Fi network is used when present; otherwise the active LAN is used.

Choices and sockets are memory-only and session-scoped. Leaving the foreground
cancels in-flight work and clears leases; process death releases OS resources.
Hardware/OEM and full-host integration acceptance remains separate from isolated
service tests. No Bluetooth/Wi-Fi/location success is inferred from static fixtures.

Calendar helpers: `calendar::insert_android` opens Android's event editor, where
the user chooses the calendar and saves or cancels. The response is only
`handed_off:true,saved:false`; it never claims provider persistence. No whole-calendar
read/write permission is requested. All-day events use UTC midnight/exclusive end.
`calendar_zoned_ui::event_editor` localizes existing UTC timestamps, preserves a
draft and emits UTC Save data after explicit timezone/gap/overlap resolution. Its
outcome includes the chosen timezone for parent-owned storage. Arbitrary ICS
TZID/RRULE/BYDAY/EXDATE import is still unsupported.

`notifications::schedule_with_policy` adds repeat count1–100, interval60–2592000
seconds, missed `latest` (default, one late posting) or `skip` (skip after a5-minute
inexact-job grace), and Snooze60–86400 seconds. Count includes the first occurrence.
Repeats retain their original absolute cadence and skip backlogs. Snooze replaces
the keyed reminder with a shifted cadence and its remaining count. Notification
actions are private one-use tokens and recheck the current signed owner/grant.
Notifications are inexact; registration/posting is never a delivery guarantee.
