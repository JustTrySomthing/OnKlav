# Audio Editor

Audio Editor is a signed included OnKlav app (`app.onklav.audioeditor`). The Kotlin native workspace is dispatched by the shared SDK host for this first-party ID, with shared theme, top bar and operating-system Back navigation. It is a single-track editor, not a multitrack Audacity clone. The Rust/Wasm shell uses the normal signed package, install, grant and removal paths. No new Android APK application or JNI changes are introduced.

## Editing

- Open a user-selected audio file or extract the track from an MP4. WAV PCM8/16/24/32 and float32 have a direct bounded parser. Other formats use Android MediaExtractor/MediaCodec and depend on installed system decoders.
- Source waveform with selection handles, exact start/end seconds, zoom and waveform navigation. Waveform is before sound processing; cuts precede Reverse and other effects.
- Keep/trim, remove selected region, silence, duplicate, reverse, fades; parameter/region undo and redo (40 changes). All edits leave the selected external original unchanged.
- Volume −24…+24 dB; parametric tone controls at120Hz/1kHz/8kHz (±12dB); pitch ±12semitones and independent tempo50…200%. Stereo uses linked alignment.
- Noise-only sample learning and STFT spectral subtraction with smoothed gain floor; background gate,80Hz rumble filter and DC blocker. Noise reduction cannot perfectly separate voice from overlapping noises; gentle settings preserve more detail.
- Optional3:1 compression above−18dB,−1dB peak normalization and default peak overload protection. Overload protection is a constant peak attenuation pass, not a lookahead dynamic mastering limiter.
- Preview selected region, compare without sound effects, scrub, pause/stop; preview files reused when unchanged. Audio focus and shared media/speech ownership prevent overlapping internal playback. Stop/cancel on background/disposal.
- Save whole edited timeline to a new user-selected file as16-bit PCM WAV or AAC-LC M4A128kbps mono/192kbps stereo. M4A codec support depends on the phone; WAV remains available.
- Private atomic draft restores the current decoded source and edit/noise settings. New import is published only after successful decoding and draft save. Access revocation hides the workspace; native operations verify installation/grants. Delete app data removes `filesDir/audio-editor`.

## Implementation

`AudioEditEngine.kt` contains file-backed float PCM, region mapping, linked WSOLA time stretching with pitch resampling, FFT denoising, biquad EQ, level/fade/cleanup processing and WAV encoding. `AudioEditMedia.kt` owns bounded WAV/system decoding and system AAC encoding. `AudioEditorModel.kt` owns private draft/IO, cancel/progress, preview and export. `AudioEditor.kt` renders themed controls, waveform, precision/help/export dialogs. New icon vectors follow existing outlined theme tint.

Two-hour timeline limit, one/two channels,8–192kHz,1GB encoded input; processing files use bounded heap. Disk availability is checked during import and before render. Extreme pitch shifts/resampling and large tempo changes can produce artifacts; no claim of studio mastering quality. Preview renders offline with measured progress before playback. No recording, multitrack mixing, plugins, lossless compressed export, background render service or fully general audio-project interchange.

## Verification

See `audio-editor-verification.json` and `docs/evidence/v0.94.0`. Phone codec/output-provider/listening/latency acceptance is separate from host DSP and UI tests. No APK requested for this source update; last delivered0.93APK predates Audio.
