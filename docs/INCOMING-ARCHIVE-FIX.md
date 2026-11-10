> Historical checkpoint. The earlier Trash exclusion is superseded by the owner's new receiving rule in INCOMING-INBOX-FIX.md.

# Incoming messages resurface archived conversations

The incoming SMS/MMS notification path previously left the durable conversation folder unchanged. It now returns Archive to Inbox before notification channel, mute or display processing. Uses the existing Rust-backed store and preserves pin/mute/read/draft metadata; blocked senders, Trash and invalid thread IDs are excluded. Serialized store requests keep the folder check/update together relative to ordinary chat edits. Existing duplicate-SMS handling still avoids treating a replay as a new arrival.

Four focused real-store/Rust/Robolectric checks passed: archive-to-inbox with persisted flags; group thread; blocked/Trash exclusions; Inbox/invalid idempotence. The harness compiles production ChatStore and extracts the unchanged NativeCore JNI declaration and phone normalization. It directly invokes incomingThread rather than compiling the full notification/transport UI. Source inspection confirms both receive paths reach it before notification handling.

The added full-app IncomingArchiveTest remains pending: the restored offline build lacks current Camera/Mail/Media3 dependencies. The failed build did not reach app compilation; no test assertion failure or APK is claimed. No physical carrier acceptance. See incoming-archive-verification.json and evidence/incoming-archive.
