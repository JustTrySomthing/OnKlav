# Per-game saves update

Game SDK0.6 and host0.100.56/code165 provide independent per-game/publisher cartridge saves, acknowledged return, permanent data slots and safe New/Resume. See ../developer/games/SAVES.md. Latest delivered APK0.100.55/code164 predates this source. Older notes below are historical.

> Notes has been replaced by Write in the host. The legacy Notes package/source is retained only for older SDK examples and backup compatibility; it is not an included desktop app.

# OnKlav App Development Kit 0.4.1 — Developer Preview

The current external-app developer kit starts at
[developer/README.md](../developer/README.md).

It includes project creation, locked Wasm builds, author key generation, signed
package inspection, a production-broker headless harness, scenario tests,
a private-document template and current API/compatibility/troubleshooting guides.
Use `python3 developer/onklav.py doctor` from the extracted starter root.

Only OnKlav is an Android APK. Authors ship signed `.onklav-a` applications;
Rust/Wasm controllers use host-rendered themed UI and explicit capability grants.
The runtime identifier remains `rust-wasm-ui.v1` and helper crate0.1.0. The toolkit targets Android host0.100.42/code151, with guest protocol/min_host level96. APK update codes and guest compatibility levels are separate. The shared
`onklav-ui`0.4.0 library provides forms/dialogs, file selection/media and included
app controllers. See [components](../developer/docs/COMPONENTS.md).

- [API reference](../developer/docs/API.md)
- [Compatibility](../developer/docs/COMPATIBILITY.md)
- [Testing and troubleshooting](../developer/docs/TESTING.md)
- [Readiness and evidence](../developer/docs/READINESS.md)
- [Machine-readable contract](../developer/contract.json)

First-party selective builds in the complete platform checkout use scripts/build_sdk_apps.py; see
[that separate workflow](../docs/SDK-BUILD-WORKFLOW.md). It is not the external
app project generator. Historical chronology is retained in docs/SDK-HISTORY.md
and is superseded by the current developer guides.

Included app entry points are now thin wrappers around the public controller
crate. Use `developer/onklav.py new` and `verify` for external app development.
New projects carry portable dependencies, signed icons/minimum host metadata,
and scenarios. See [cookbook](../developer/docs/COOKBOOK.md).

## Default file-selection UI

All SDK file-selection effects open the built-in OnKlav Files browser. Use the existing `blobs.import` / `blobs.export`, `library.import` / `library.export` and file-picker wrappers; keep their existing capabilities and user-gesture/token rules. Do not launch Android document pickers from app UI. Native host modules use `app.onklav.shared.OnKlavFileContracts`. The current host offers OnKlav/Android storage tabs and an Internal files link, with Android shared-storage access reviewed during host setup. Legacy document-tree connections remain compatible. Browsing and Save As stay in OnKlav. Guests receive scoped IDs/results, never host paths or URI strings. Save As creates a distinct copy if a name exists. See `docs/FILES-AND-READING-POLISH.md` in the host source for behavior, failure recovery and device acceptance.

## Built-in MP3 support

Android hosts include `app.onklav.shared.Mp3Codec` in the shared SDK: `decode(input, wave, check, progress)` converts MPEG-1/2/2.5 Layer III to PCM16 WAV, and `encode(wave, output, kbps, check, progress)` creates MP3. Call from a worker thread with cancellation and the calling app's authorization check. Source and output must differ. Conversion is limited to two hours and available storage; malformed/protected inputs fail explicitly. LGPL source/license and relinking instructions are bundled under `third_party/jump3r` in the platform source.

Wasm applications continue using authorized `media.play` and opaque file handles. The host tries the normal streaming decoder first and can recover authorized local MP3 files through the bundled software decoder. It does not download radio streams for conversion. Revocation and Stop cancel recovery and remove its temporary WAV. No Wasm MP3 encoding effect is introduced. Audio Editor exposes MP3 creation after pitch/tempo and other edits; Video Editor accepts MP3 soundtracks. File selection normalizes `.mp3` and common MIME aliases to `audio/mpeg`.

## Radio playback transport

`radio.stations` authorizes public station lookup and playback through the host Radio broker. The host now handles public HTTP/HTTPS progressive MP3/AAC/OGG/Vorbis/Opus/FLAC, ICY metadata, HLS and bounded PLS/M3U resolution. Device decoder support still applies. Media3 reads and derived playlist/segment requests retain the original station capability, public-address validation and live revocation checks; no credentials/cookies or file/private-network URLs are accepted. This transport allowance does not change the HTTPS-only `network.http` or account brokers. Live Pause releases the socket; Play reconnects at the live edge, and Stop/sleep/revocation cancel pending recovery. Cached station browsing is immediate; Settings > Refresh stations explicitly refreshes the directory.

### Encrypted user-folder policy

The built-in Files picker mediates unlocks for host and SDK apps. Guests receive
opaque entry identifiers and the usual selected-file grants; passwords and
container keys stay in the host. All library operations and ancestors are checked
by the native folder policy. A lock invalidates later provider opens and Write
reads/saves. Apps must handle a locked/revoked selection by offering file selection
again. Do not retain automatic plaintext caches indefinitely. A user-authorized
import is a separate app-owned copy; it is not recalled by locking its source.

Deferred save destinations inside protected folders are intentionally rejected:
save outside, then move the file into the unlocked folder in Files. See
`docs/FOLDER-ENCRYPTION.md` for transaction/recovery boundaries and limits.

### Shared audio handoff

A new shared `media.play` replaces the previous player and media session. Host
preview/speech/voice clients use the common audio owner and release focus when
interrupted. Old service starts, controls and delayed interruption callbacks
cannot act on a newer playback generation. Repeated current service starts are
idempotent. Decoder release must occur outside the carrier/registry monitor so
a closing loader can finish its authorization without blocking the next app.
See `docs/AUDIO-HANDOFF.md` and its focused verification report. No new SDK
capability or additional per-app permission is required.

### Shared offline speech

Host speech automatically applies shared reading cleanup: common honorifics, number labels, multi-initial names, emoji/decorative markers and invisible book characters. Pass original text; do not pre-expand or duplicate cleanup in apps. Spoken progress is mapped back to original UTF-16 positions. See docs/SPEECH-TEXT-CLEANUP.md for exact conservative rules and limits.


`speech` and `speech_settings` use OnKlav's included English neural voices by default, including in Messages and the built-in readers. The shared host engine is official Rust Sherpa-ONNX1.13.8 with the original native Piper/eSpeak pronunciation path and Kitten Nano0.8 FP32. No Android TTS configuration, cloud service, app-specific model download or generated-audio file is needed. The visible choices are Male (Nano speaker 2, default) and Female (Nano speaker 0), using the labels requested by the owner. Both selected upstream speakers are male; the Female label is a user-selected name, not a claim about the source voice. No Kokoro bank or separate app engine is included.

Keep the existing `speech.output` capability and UTF-16 value/position contract. The host owns model/audio resources, authorization, focus, cancellation and handoff. Short requests remain intact; long text uses bounded natural chunks with synthesis ahead of playback and a10-second native PCM queue. Playback uses native AAudio. Position highlighting is estimated from played PCM/text weights; it is not forced-alignment word timing. Built-in Read Aloud/eBooks may continue a user-started session with screen off through a foreground media service; other SDK apps remain foreground-scoped. Permission revocation, widget/session loss and closing an app still stop its speech. Never cache a speech session or bypass these checks to gain background access.

The host voice pack contains only the two selected rows and English data. Original IDs0/2 map internally to compact rows0/1; app-facing controls/IDs are unchanged. UI_HIDDEN preserves the warm shared model; actual memory pressure can unload it. SDK apps must continue to use host speech capabilities rather than bundling models.

### Shared document viewing

Supported document `files.open` operations now open the included Docu View screen: PDF, ODF/ODT, TXT, DOC, DOCX, RTF, ODS, XLSX, XLS, CSV and TSV. Keep using the existing authorized Files broker and scoped selections. Guests receive no host path/URI and gain no additional file permission. PDF pages preserve layout; other documents use a mobile reading view and spreadsheets display cached values. PDF text tools require Android15+. No Office macros/formulas or linked content execute. Native host modules can call `OnKlavDocumentContracts.view` with a user-selected content URI. See `docs/DOCU-VIEW.md` for limits and fidelity.

## Game music

Create or source your own tracks and bundle them with your game. The SDK handles playback, looping and the Music bus. See [Game music](../developer/games/GAME-MUSIC.md).
