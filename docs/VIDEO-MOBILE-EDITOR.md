# Mobile Video Editor upgrade

Latest RC preparation: [VIDEO-RC-READINESS.md](VIDEO-RC-READINESS.md). This page records the earlier implementation stage; its remaining-work list is superseded by that document.


See [VIDEO-OFFLINE-FINISH.md](VIDEO-OFFLINE-FINISH.md) for the latest local finishing tools and portable project workflow. No neural AI or cloud service is required or planned.

This upgrade rebuilds the editing workspace and adds real composition capabilities. It is an advanced local editor with explicit bounds, not a claim of full CapCut parity or handset production qualification.

## Workspace

The preview stays fixed above the timeline. The timeline uses a central playhead and draws only the visible time window, so a long project never creates an enormous Compose layout. Actual clip durations determine thumbnail widths; subtitle, overlay and audio blocks show their actual project times. Drag to scrub, tap to select, drag the selected clip edges to trim, or use exact trim values. Accessible seek/previous/next actions remain available.

A labeled icon dock opens a bounded, scrollable inspector below the timeline. The preview remains interactive with tools open. Done closes the panel; Back first closes the tools. Projects, resolution and Export integrate with the real OnKlav host top bar. The standalone test workspace keeps its own equivalent header. Undo/Redo, Play, keyframes and fullscreen sit by the preview; Split, Duplicate and Delete act on the selected main clip or overlay. No paid-feature buttons or nonfunctional AI controls are shown.

## Implemented features

| Area | Implemented | Bounds / notes |
|---|---|---|
| Main editing | Import photos/videos, trim, precise trim, split, duplicate, delete, reorder earlier/later, freeze frame | 100 main clips, 12-hour project, 4 GB per import. Reordering uses explicit controls, not drag-reorder. |
| Preview / canvas | Edited composition preview, fullscreen, drag/pinch transforms, 5 aspect ratios, fit/crop | 720p short-edge preview; Source posters are labeled. GPU/decoder behavior needs phone acceptance. |
| Video/photo overlays | Four independently timed composited overlay tracks; position, scale, rotation, alpha, trim/split/duplicate/delete | Native Media3 compositor, reverse registration order for visual stacking. Overlay sound is muted; use Audio for sound tracks. |
| Cutouts / masks | Green- or blue-screen chroma key, tolerance/spill reduction, circle and letterbox masks, feather | Alpha shader shared by preview/export. Six EGL/GLES software-renderer pixel cases passed; handset GPU/HDR-input checks remain open. This is not AI subject segmentation. |
| Animation | Clip/overlay keyframes for zoom, X/Y, rotation and opacity; add/remove/jump to point; direct preview manipulation | Five easing modes plus four editable motion presets, up to100 points per clip; split boundaries preserve the original curve interval. Legacy eased start/end zoom remains readable. No arbitrary Bézier curve editor or motion tracking. |
| Speed | Normal speed, Speed Up/Slow Down/Hero Moment presets, draggable 5-point curve editor | Curves are rendered as32 source-timed speed segments;0.25–4x actual speed. Shared duration/inverse-time model keeps splits and extracted audio aligned. No optical-flow frame synthesis. |
| Text / stickers | Timed text and emoji, position, color, bold, background, size and wrapping | One active text/caption texture is composited above video/overlay tracks. Fade/slide/pop entrances and exits preserve phase across splits. No downloaded sticker/font marketplace. |
| Captions | Manual captions; SRT and WebVTT import; SRT export; cross-clip cue mapping and editing | 2 MB import, up to200 text/caption layers per clip and5,000 total. No speech-to-text engine included. |
| Audio | Four mixed audio tracks, trim, offsets, volume, fades, extract source audio with speed preserved | Real decoded media is used; timeline blocks are not simulated waveforms. Narration ducking, stereo balance and three conventional DSP tone presets. No beat detector, voice-isolation model or spectral noise reduction. |
| Voiceover | AAC microphone recording at the playhead, real level meter, Stop/Save and Discard, focus and lifecycle handling | Up to30 minutes or remaining project time. Included-app `audio.record` capability defaults on via the signed upgrade; Android microphone approval is requested in context when needed. Hardware recording needs phone verification. |
| Color | Brightness, contrast, saturation, warmth, hue and6 existing presets | No imported LUT/curves/HSL-secondary qualification. |
| Fades | Clip/audio fade-in/out and through-black presets | Fades are accurately labeled; no overlapping cross-dissolve or paid transition library. |
| Output | H.264/AAC MP4;480/720/1080/2160 short-edge requests;24/25/30/50/60fps limits; automatic or4/8/16/32Mbps requests | 4K and rate/bitrate availability depend on phone encoder/fallback. This is SDR output. Device export/provider/long-file testing remains necessary. |
| Projects | Private media copies, atomic project saves, undo/redo, project library, backward-compatible optional JSON fields | Portable project ZIP backup/restore includes every referenced source; imports receive a new identity. Source files and the original publisher are retained. |

## Remaining parity and qualification work

- Automatic speech captions/transcription, translation and caption styling/word animation require an actual ASR/language pipeline.
- AI subject/background removal, tracked masks, stabilization, motion tracking and optical-flow slow motion require additional processing engines/models.
- True overlapping crossfade/wipe transitions, arbitrary track-count editing, advanced blend modes, arbitrary Bézier easing, LUT imports, animated stickers/fonts and asset/template libraries are not implemented.
- Beat detection, spectral/noise/voice isolation, advanced audio pitch tools, proxy workflows, collaboration and codec/HDR export expansion remain separate work.
- No claim of professional-grade acceptance is made until Samsung phone GPU/codec/recording, A/V synchronization, real-provider export/cancellation, long-project performance, and landscape/keyboard/accessibility have been verified. The host tests cannot replace those checks.

## Implementation and compatibility

The app ID stays `app.onklav.videoeditor`; signed shell version4 adds the existing `audio.record` capability using the original publisher. New fields remain optional in schema1, so prior local project JSON still loads. Audio extraction retains original source ranges/rates; speed segments use absolute source boundaries so split clips keep their rates. Overlay lengths are clamped when the main timeline shrinks. New media capture/import staging is removed on failed publication; existing originals and exported files are preserved.

Captions use a single cached texture for only active cues, above all visual tracks, avoiding a shader sampler per subtitle. Chroma/mask GLSL was compiled and sampled through EGL/GLES using llvmpipe. That proves the shader expressions and alpha cases on the host, not Android driver or Media3 pipeline qualification.

No core/JNI changes or new runtime dependencies. No new APK requested for this task; the last delivered release remains0.100.7/code116. Build the updated host with signed shell version4 for handset acceptance.

## Reference basis

The interaction model is an original OnKlav implementation inspired by public mobile editing workflows, not a copy of CapCut branding/assets.

- [CapCut: mobile overlay workflow and export controls](https://www.capcut.com/resource/how-to-add-capcut-overlays)
- [CapCut: editing, keyframes and speed curves](https://www.capcut.com/resource/how-to-use-capcut)
- [Android: video compositor settings and timed layouts](https://developer.android.com/media/media3/transformer/videocompositorsettings)

The APIs were additionally checked against the locally installed Media3 1.9.3 classes before implementation.
