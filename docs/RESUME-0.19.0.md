# Resume 0.19.0 — foreground voice broker, source only

User said proceed with the next TODO item, continuing the no-APK-per-item workflow.
Item 2 implements host-owned recording/playback and local review/queue/discard.
It does not establish a live Walkie chat or transmit audio. Continue item 3 next.
Current source 0.19.0-dev/code26, Walkie signed package v3. The last APK remains
historical 0.14.0; current Linux JNI only, no mobile Rust rebuild.

Production: Rust/Wasm declares voice_capture/voice_clip; generic Kotlin
SdkVoiceComponent -> SdkVoiceController -> AndroidVoiceBackend MediaRecorder/
MediaPlayer -> current model/session SDK host_voice/voice_prepare/voice_commit/
voice_played -> Rust current widget/grant/recipient checks -> private clip archive.
Raw audio and backend objects never enter Wasm. Wasm effects cannot request these
trusted host actions. Android permission is requested only after a direct Record
press and a live app grant check; the grant callback does not start a recorder.
New audio.record/audio.playback grants default OFF during curated bootstrap;
users explicitly enable them in Settings > Apps. Signed updates use normal review.

AMR-WB is attempted first, NB fallback; private cache files, mono, 64 KiB cap,
12-second recorder/host timer stop. Full output is structurally checked before
trimming valid extra encoder frames to 600 frames (12s); <300ms fails. Native
sample quality/encoder behavior is not established by fixtures. Backend validates
live authorization immediately before recording/playback start as well as during
use and callbacks. No recording is a live floor grant: this is a local voice
message for a saved recipient; live floor/control authorization is item 3.

Drafts stay in host memory/cache until explicit Queue voice clip. That queues a
private outgoing clip with no send attempt; no carrier work or delivered status.
Queue/Discard review is always shown while transport is unavailable. Auto Send/
Auto Play remain connected-chat settings and are not falsely applied offline.
Preview requires playback permission as well as record permission. Outgoing own
clip playback never marks it remotely heard. Incoming clips are marked heard
locally only on successful player completion with a still-current session/widget.
Receipts are not sent. Frame close/dialog/recipient changes, permission revocation,
background/pause, focus loss, other voice owner, cancellation and dispose release
resources and discard unsent drafts; generation checks reject stale callbacks.
Periodic foreground authorization checks run at 250ms while active/draft held.

Kotlin controller tests inject fixture recorder/player/focus. A joint test uses
actual signed Wasm and JNI/private archive for local capture queue and incoming
played state; its device audio alone is a fixture. Rust tests cover widget/grant/
recipient binding, modal/closed widgets, trim/invalid tail, queue status, playback
of outgoing clips, and revoked sessions. Initial JNI integration used a distinct
Robolectric sandbox and hit the same-library/two-classloader restriction; align
its SDK/qualifiers/native graphics/paused looper with existing JNI tests.

Remaining: real microphone/speaker/focus/encoder/OS grant/lifecycle acceptance on
phone, original warm-up/beeps and live dial/contact verification flow, session/
transport auth, background archive scheduling, retry/notifications/receipts and
carrier checks. MMS_READY=false; no default role change. Do not call the entire
Walkie migration finished or claim measured device speed/audio quality.

Use restored-env/env.sh and cargo env, retained publisher key, build_sdk_apps.py,
build_native.py --host-only, check_android.py --tests-only. Save through existing
source/SDK/Walkie/TODO identities. See source-verification.json for final results.

Final evidence: 65 Rust +48 Kotlin host tests passed (113), including the current
full app/shared run. The joint playback test asserts both the Rust heard state
and successful UI completion response. Playback completion refreshes history
through a non-gesture guest callback. Playback preparation/completion has a 20s
host timeout. TODO item 2 implementation is complete, with phone audio acceptance
still pending; no APK/mobile rebuild occurred. The shared atomic archive's
large-library read/write latency still needs device profiling; no speed or audio
quality improvement is claimed. See source-verification.json and final XML logs.
