# Voice Recorder and Screen Recorder

Two included OnKlav host apps, inside the existing single APK. No additional
APK, signer, network service, model, media library or Android storage grant.
Shared recording state, file library, native media bindings and themed controls.

## Everyday workflow

**Voice Recorder** records mono 48 kHz audio as compact AAC/M4A (128 kbps) or
lossless 16-bit PCM/WAV. The waveform comes from measured input peaks, not an
invented animation. The timer excludes pauses. Start, Pause/Resume and Stop &
save remain at the bottom of the Record page while content scrolls. Recording
continues in a foreground service when leaving OnKlav or turning the screen off.

**Screen Recorder** offers Silent, Device audio, Microphone, or Device +
microphone. Start immediately or after 3/5/10 seconds. Standard and High are
30 fps H.264/MP4 presets, bounded by 1280/1920 pixels on the long edge without
upscaling; actual dimensions follow the screen aspect ratio and encoder alignment.
Device sound is stereo AAC (192 kbps); Both mixes mono narration into the stereo
soundtrack with bounded arithmetic. Microphone-only capture uses Android's
native synchronized media recorder. Use headphones for Both to avoid picking
up the same device sound through its speakers and microphone.

Android's screen-sharing approval appears for every session. No token is
persisted or reused. Its picker can offer one app or the whole screen, depending
on the Android version. While counting down, switch to the desired app if needed.
Once started, use the ongoing notification to Pause/Resume or Stop & save; tapping
it returns to the correct OnKlav recorder. Back offers Keep recording & leave
or Stop & save. Microphone setup grants are reused; revoked permissions are
requested on an explicit start. Notification denial is respected, with a reminder
to return to the app for recording controls.

The Recordings tab supports search, playback, rename, share, save a copy and
Move to Trash. Voice playback has seek, ten-second skips and 1–2× speed.
Video preview uses the existing Media3 player with seeking and playback controls.
Leaving/backgrounding pauses/closes preview. Playback is blocked during capture
to avoid accidental feedback. Deleting an unfiled draft is permanent and confirmed;
normal recordings use the recoverable Files Trash.

## Data and resource lifecycle

A single foreground-service recording lease prevents simultaneous recorder
sessions. User-visible control state, monotonic clock and metering survive UI
navigation. All backend transitions run in one worker queue. Stale notification
tokens and repeated Stop actions cannot affect another session. Preparation/
countdown cancellation releases capture and removes only that session's draft;
it does not publish an unwanted short recording. New Start stays disabled until
the previous service is destroyed, preventing an immediate restart from being
sent to a stopping service.

Audio focus is requested for Voice Recorder, with pause on focus loss/phone
calls. Screen capture does not take playback audio focus, so the soundtrack being
captured keeps playing. Microphone silencing/muting is reported rather than
shown as a working signal. Audio reads continue to drain during pause; paused
samples are discarded. Device/mixed audio is encoded separately and muxed into
the video without re-encoding, using the measured initial offset and rebased,
monotonic timestamps. Encoder shutdown has bounded waits.

Finalization closes the media recorder, virtual display, AudioRecord/codec,
projection and audio focus before publishing. Final media is fsynced before its
ready receipt. Stop/callback errors retain an interrupted draft rather than
claim a successful save or silently delete data. If captured audio cannot be
finalized but video is playable, the silent-video fallback is explicitly labelled.

Completed media is automatically imported through the SAME signed OnKlav Files
controller into Audio / Voice Recorder or Video / Screen Recorder. Retry checks
both the stable recording filename and content hash before reusing an earlier
import receipt. It never drops an unfiled original when permission or filing
fails. The read-only recording provider checks live Files access and folder
membership for every open; it exposes neither arbitrary paths nor unfinished
drafts. Single-file sharing uses explicit read URI grants.

Private `recording-drafts` files are durable app data, not disposable cache.
Ready-but-unfiled recordings show a Save to Files action. Interrupted WAV files
can repair their header, retaining every complete sample. Other interrupted
media is recovered only if its tracks are playable; a playable video can be
recovered without an unusable interrupted soundtrack, with an explicit warning.
An MP4/M4A process death before its container is finalized can be unrecoverable;
no host test or UI promises otherwise. Unplayable drafts remain available for
explicit deletion. Do not remove compatibility/migration code or silently erase
private recordings as cleanup.

## Android and practical limits

- Protected windows/video, calls, work-profile content and apps opting out of
  playback capture cannot be bypassed. Captured device audio may be silent.
- Screen lock, system revocation or Android's capture indicator can end projection;
  the callback stops and attempts to save. There is no hidden auto-resume.
- Rotation keeps the starting output canvas. Android scales the captured content;
  borders can appear. The app does not promise adaptive-resolution video files.
- Each session stops at six hours, its file-size budget or low-storage threshold.
  The per-file budget is at most 1.5 GiB, reduced for free space to leave room for
  muxing and filing. There is no untested seamless multi-file segmentation.
- Normal recordings participate in existing Files/App Documents backups. Unfiled
  and interrupted private drafts must be recovered/filed first. Existing archives
  remain limited to 512 MiB overall; larger recordings need a separate copy,
  such as Save a copy to a connected phone folder.
- Fixed a pre-existing backup mismatch: audio/video imports allowed 2 GiB but
  backup validation rejected individual media above 256 MiB even when the archive
  fit 512 MiB. Media now uses its existing import limit, while archive size/hash/
  ZIP restrictions and non-media limits remain enforced. A real 257 MiB archive
  round-trip regression covers this change.

## Verification

See recorder-verification.json and recorder-checks. Focused host checks exercise
real signed Files import/rename/read-only sharing/trash, retry/revocation safety,
WAV data recovery, clock/mix/dimension rules, recording-service transitions with
an injected backend, permission intent, and dark/light/narrow/large-text layouts.
The existing encrypted Vault backup path is checked after the backup change.
The native backup checks include a real media file above the former limit;
matching host and ARM64 JNI are rebuilt with honest hashes/alignment evidence.

Host service tests use a fake capture backend. Real microphone input, Android
projection consent/revocation, app audio policies, AAC/H.264 encoders, A/V sync,
Bluetooth routing, phone lock, thermal behavior and long recordings still need
physical phone acceptance. No new APK is requested or built in this task.
