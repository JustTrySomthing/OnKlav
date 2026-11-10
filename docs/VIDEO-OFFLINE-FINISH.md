# Video Editor: offline finishing pass

Latest RC preparation: [VIDEO-RC-READINESS.md](VIDEO-RC-READINESS.md). This page records the earlier implementation stage; its remaining-work list is superseded by that document.


The editor uses local media, conventional signal processing and the existing Android Media3 pipeline. No neural inference, accounts, subscription services, remote assets, cloud storage or telemetry were added. This is a substantial usable offline feature set, not a claim that every CapCut workflow or phone acceptance gate is complete.

## Added workflows

- **Keyframe motion:** Linear, Smooth, Ease in, Ease out and Hold, chosen for the segment after a point. Push in, Pull out, Pan left and Pan right create real editable two-point animations. Easing stores a normalized curve interval so splitting a clip preserves the original trajectory on both halves.
- **Text animation:** Independent None, Fade, Slide or Pop entrances/exits for titles, stickers and captions. Each animation lasts up to400 ms (bounded to half the original title duration). Split title segments retain the original animation phase. Glyph images are cached while active; all active titles are still drawn into one bounded compositor texture.
- **Audio finishing:** Explicit Narration role, automatic timeline-based music ducking under narration, stereo balance and Original / Speech clarity / Warm / Telephone DSP tones. Ducking is a75% amplitude reduction (approximately12dB), with200ms attack and400ms release. Overlapping narration regions use the strongest single envelope. This is scheduled narration ducking, not speech/silence detection. Stereo balance attenuates the opposite stereo channel; mono or multichannel audio is left spatially unchanged. Tone processing uses low-cost sample-rate-aware filters, with bounded output. Original audio remains available.
- **Portable projects:** Projects > Back up project with media writes a ZIP containing the project and every referenced private video/photo/audio copy, with shared sources included once. Projects > Import portable project restores to a new identity, preserving the original project. All effects/timing/keyframes/captions remain editable. No external service is involved. This is full original-media packaging, not a compact proxy.
- **Control polish:** Adjustable sliders have original-value marks, neutral/changed track colors, snapping and individual Reset buttons. Slider values come directly from the edited model so undo/reset updates the control position. Time/trim controls intentionally have no invented original value. Color corrections can be applied to all main clips without replacing their other edits.

## Import/export handling

Portable ZIP imports require the exported project manifest as the first entry. Only the exact validated, referenced media names are extracted; arbitrary paths, unexpected files, missing/empty sources and repeats are rejected. All writes happen in a private staging directory; an incomplete or cancelled import removes that staged project. Publication uses a new random project ID and does not replace an existing project. Byte limits apply to actual uncompressed reads, not claimed ZIP metadata:8MB manifest,4GB per media file,16GB total.64MB free-space reserve is retained. ZIP CRC errors are surfaced. Import/export stream in chunks and check cancellation and the existing storage permission. Failed/cancelled provider backups are deleted where the provider permits it.

The optional new schema1 fields default to the previous behavior. Signed app identity/version4, permissions, publisher and all core/JNI code remain unchanged. APK creation is separate and was not requested in this turn.

## Remaining scope

No neural/cloud features are planned. Automatic transcription/translation, learned subject removal, generative tools, voice isolation and optical-flow models are excluded from this pass. Traditional stabilization/tracking and optical-flow algorithms are possible without neural AI but require a separate processing engine and hardware qualification; they are not represented as completed controls. True overlapping clip transitions, LUT imports, advanced blend modes, arbitrary track counts, reverse media rendering, beat analysis, proxy rendering, templates/asset stores and advanced pitch/spectral audio workflows are also not implemented. Existing through-black fades remain clearly labeled.

Actual handset encoder/GPU/recording, long-file/provider operations, A/V synchronization and landscape/accessibility acceptance remain separate gates. Host tests verify data, timing, PCM processing, archive handling, composition configuration and native-rendered UI; they do not qualify a phone's codec/GPU output.
