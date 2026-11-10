Current navigation/layout refinement: [Phone/Camera root navigation](PHONE-CAMERA-ROOT-NAVIGATION.md). This supersedes historical workspace-layout descriptions below; native calling and permission protections are retained.

# Phone — OnKlav native dialer

Phone is an included native app in the single OnKlav APK. Keypad, Recents (call
events/history) and Contacts use the current theme and font scale. An outline
handset icon matches the other app icons. No calling backend, account service,
new binary dependency or SDK guest calling permission is introduced.

## Everyday workflow

- Large keypad, paste, backspace/hold-to-clear, hold 0 for +, hold 1 for voicemail,
  pause/wait digits, save contact and message shortcuts. Explicit Call action,
  calling-account selection when multiple enabled Telecom accounts exist.
- Searchable, date-grouped call history with All/Missed filters, incoming/outgoing/
  missed/declined/blocked labels, per-call details/duration and automatic paging.
  Keyset queries use bound search arguments; 100 rows per page, 5,000 retained,
  server-side search for older history. Private/unknown caller IDs cannot be dialed.
- Shared OnKlav contact loading/avatar rendering, name/number search, favorites,
  contact details, calls and messages. Native local contact creation and guarded
  editing of a writable linked contact, up to ten numbers; other contact data and
  unchanged phone-row labels/IDs are retained. Provider version assertion rejects
  concurrent edits. Read-only or unusually large contacts are not silently replaced.
- Confirmed single history-entry removal and number blocking via Android's blocked
  number provider. Android blocked-number management and calling-account/voicemail/
  carrier settings remain reachable. Keypad sounds and haptics have saved toggles.

## Everyday-use review

- Number entry now uses Android's country-aware as-you-type formatter without
  changing the dialed digits. Service codes and pause/wait sequences stay intact.
- Visible Speed dial and Voicemail shortcuts sit above the keypad. Eight saved
  speed-dial slots (2–9) support direct tapping or holding the corresponding key,
  searchable contact/number assignment, change and removal. Stored destinations
  are explicit numbers; editing a contact never silently changes a destination.
- Voicemail checks setup, offers an account choice for multiple enabled lines,
  then calls the account's configured voicemail number. Missing configuration
  points to Calling accounts & voicemail. Hold 1 remains available.
- Contact details now include confirmed deletion using a stable lookup URI and
  live write-permission checks. Confirmation explains linked/synced entries;
  messages and call history are retained. Matching speed-dial entries are removed.
  Add, edit, search and favorites remain available. Save reports Saving/Contact saved
  and prevents duplicate saves. Read-only/provider limitations are reported.
- Settings expose Android Ringtone & sound alongside carrier/account/voicemail
  options, keypad sounds/haptics, blocked numbers and setup.
- Surfaces, type and outline icons use the shared OnKlav theme; controls retain
  the narrow-screen/large-font layout. New focused tests cover partial number
  formatting, persistent speed-dial assignment/removal, stable contact deletion
  with revoked-access protection, and visible shortcut/UI behavior.

## Android calling integration

An exported InCallService protected by BIND_INCALL_SERVICE supplies incoming and
ongoing call screens. Callbacks track all Telecom-delivered calls by internal IDs;
call waiting, hold/resume, add, merge/swap, conference participants and separation
are exposed according to actual capabilities. Audio endpoints use Android 14+
CallEndpoint APIs with an older CallAudioState fallback. In-call keypad sends DTMF
with bounded tone lifetime. Mute, proximity handling and a connection-based timer
are present; minimizing does not disconnect the call.

Telecom VideoCall surfaces and explicit session-change consent support provider
video when available, including an audio-only answer option. Camera access is
required for transmission. The carrier/ConnectionService owns media; OnKlav does
not invent a new video/voice transport or auto-enable a camera from an upgrade request.

Private CallStyle incoming/ongoing notifications expose answer/decline/end actions.
Answer opens the private call activity directly instead of a notification activity
trampoline; other actions are immutable private receiver PendingIntents, with
current-call ID and role checks. Incoming notification answer chooses audio for
video calls; the visible screen offers explicit video answer. Missed calls have a
separate notification. Post-dial waits target the original call. Callbacks,
lookups, surfaces and tones are released on removal/service destruction.

Ringing stays with Android (IN_CALL_SERVICE_RINGING=false); custom notification
sound does not duplicate the system ringtone. Emergency placement uses
TelecomManager.placeCall and the system's emergency/default-dialer rules. External
DIAL/VIEW tel intents only prefill a keypad; CALL intents do not trigger calls here.

## Setup and permission behavior

Setup version 5 adds a Default Phone role step before general call-log/runtime
approval requests. Call, call-log, phone-state, contact read/write, notification,
Bluetooth, microphone/camera and full-screen access use the existing setup and
live checks. Already granted approvals are not requested again. Android role and
permission consent cannot be silently granted or overridden. Permission denial or
revocation shows setup/recovery routes; the optional Finish later exit is retained.

## Qualification and scope

17 focused host/UI tests passed, including a narrow-screen/large-font keypad, incoming controls, in-call keypad, intent/role protections, history paging and legacy/modern Telecom audio mappings. Production compilation and full production/test lint passed with no errors; existing warnings remain. Evidence and source hashes are in docs/phone-verification.json and docs/evidence/phone.
No new APK or physical phone/carrier certification is claimed. Before replacing
a daily-use dialer, test incoming/outgoing/call waiting, dual SIM, Bluetooth,
lock-screen/head-up notifications, OEM background behavior, proximity and real
contact-provider edits on the owner's phone. Do not place emergency calls as tests.
Actual emergency routing must be qualified through appropriate platform/carrier
procedures. Carriers determine voicemail, waiting, forwarding and conference/video
availability. This does not include a spam-identification service, proprietary
Google assistant features, visual-voicemail download or call recording.

Official integration references:
- https://developer.android.com/develop/connectivity/telecom/dialer-app
- https://developer.android.com/reference/android/telecom/InCallService
- https://developer.android.com/reference/android/telecom/TelecomManager
- https://developer.android.com/reference/android/telecom/CallEndpoint

Current incoming-presentation ownership, quiet/no-badge live-call channels and terminal-state/service-unbind cleanup are documented in PHONE-CALL-LIFECYCLE.md. The 41 passing host checks do not replace a Samsung/carrier call test.
