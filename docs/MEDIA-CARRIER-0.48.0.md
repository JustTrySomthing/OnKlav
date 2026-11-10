# OnKlav 0.48 — media, carrier testing and cloud backups

## Changes

- Gallery is a lazy three-column grid of square photo/GIF/video/audio tiles. Its separate MMS media query reads the conversation's history, independently of the feed's latest-100-message window. Provider work runs off the UI thread with cancellation and loading/error states.
- Attachment taps open an internal full-screen pager with previous/next controls, image pinch zoom/pan, GIF playback, settings, save, share and Open With. Ordinary horizontal swipes browse when the image is not zoomed.
- Audio/video use a lifecycle-owned Android MediaPlayer, explicit Play, pause, seek/time, 10-second skips, mute, speed controls and audio focus. Video fits its aspect ratio. Playback stops when changing pages, leaving the viewer or backgrounding the app.
- Starred message icons are yellow in the feed, viewer and gallery. GIF thumbnails use Android's animated drawable decoder, with an autoplay preference and lifecycle stop.
- Save copies a selected image/video/audio attachment into Files → Images/Video/Audio → Messages, using bounded staging and the serialized Rust content store. Android message originals remain in the provider.

## SMS/MMS and live Walkie phone testing

The existing transport includes real SmsManager sending/downloading, protected incoming receivers, Rust MMS compose/parse/ack, Android provider persistence, durable callbacks/recovery, and Walkie consent/floor/audio/receipt paths. Previously the fixed release gate made those paths inaccessible. Setup now offers an explicit, default-off carrier trial.

1. Open Setup → Enable SMS / MMS and Walkie phone testing and read the test notice.
2. Grant message permissions, choose OnKlav as the default messenger through Android, select an active SIM and save your own phone number.
3. Test incoming and outgoing plain SMS, multipart SMS and MMS in both directions. Test attachments and mobile-data/APN failures. No automatic resend is introduced.
4. For Walkie, use two test phones, grant its SDK microphone/voice/contact/communications capabilities as needed, choose the other number and complete invitation/acceptance. Test talk, listen, leave-message, Send/Discard and interruption recovery.

Release `MMS_READY` remains false. The host supplies a separate `carrier_test_enabled` fact; it does not mislabel the trial as carrier verification. Live role, permissions, SIM and recipient policy are still checked at handoff. No default-role change or real external message was performed in this development environment. Carrier charges can apply on the user's phone. Full carrier/two-phone acceptance remains open.

## Drive and backup

Google Drive & Backups now offers working Android document-provider actions instead of a disabled connection button. Choose Drive in Android's file picker; its installed provider handles Google sign-in and syncing. Upload streams files up to 256 MiB, supports cancellation and reports incomplete copies. Saving does not make the file public. The last successfully saved document can be opened again.

App metadata backup/restore and SMS/MMS/media ZIP backup/check/compare/review/restore are reachable from the Drive screen. Select Drive or another provider as the file location. Phone history writes require carrier trial, current default role, READ_SMS, checked archive identity, current phone comparison, local-number review and explicit confirmation. Existing deduplication, journals and interrupted-restore review remain active. App metadata excludes SDK documents, credentials, permissions and pending sends. Backups are not encrypted.

Automatic Drive public-link creation and direct OAuth account integration remain unfinished. No OAuth client configuration or Google-account upload acceptance was available here. Android's provider-based backup path is functional integration, not a claim that those separate services are finished.

## Development and verification boundary

For this feedback build, use a debug test APK to avoid a release shrinking cycle. Its size/startup are not a release performance comparison. SDK packages and Godot are reused; Rust host/mobile JNI were rebuilt only for the new shared-media import and separate carrier-trial fact. Tests are a bounded selection, not another full Compose/emulator loop. Do not hold delivery for the prior Files → Notes → return host-test stall.

Physical-phone media decoding, GIF timing, gestures, provider variations, account syncing, carrier sending/receiving and two-phone audio remain acceptance work. Exact old-app visual/behavior parity is not claimed. Games remain unchanged and deferred.
