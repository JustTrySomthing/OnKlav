# Walkie-Talkie v3 — SDK development checkpoint

Requires an OnKlav host built from 0.19.0 source. Rust/Wasm app logic uses shared
Compose controls and host-owned foreground voice recording/playback. Install
WalkieTalkie.onklav-a inside OnKlav, never as a separate APK.

Settings, saved recipients, bounded clip history/deletion and local voice review/
queue/discard are implemented. Enable audio.record/audio.playback in Settings >
Apps; Android microphone access is requested separately after a Record press.
No sample clips ship. Raw audio stays in the host, outside guest Wasm state.

Queued clips are local only. Live sessions, carrier sending and receipts are
not implemented; Connect stays unavailable and the dial remains OFFLINE.
Phone microphone/speaker/encoder/focus acceptance is pending; host fixture
checks are not proof of real speech capture or audible playback.
See docs/WALKIE-PORT.md and TODO.md for the remaining work.
