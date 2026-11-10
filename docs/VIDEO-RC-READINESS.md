# Offline Video Editor RC preparation

This pass implements practical local tools using the installed Media3 1.9.3 engine and conventional DSP. No neural model, cloud service or runtime dependency is added. The requested ARM64 RC APK0.100.8-rc1/code117 has now been built and package/signature/assets/alignment verified. See video-rc-apk-verification.json. Handset acceptance remains pending.

## Implemented scope

| Request | Implementation | Limits |
|---|---|---|
| Overlapping transitions | Crossfade and four directional wipes, two alternating composited main tracks, matching audio fades, accurate overlap timing and timeline shading | Incoming transition is stored on each clip.0–2seconds, clamped to half of each adjacent clip, preventing three-way overlap. Project duration is reduced by overlaps. Main clips without transitions keep the original single-sequence path. |
| Custom easing | Two draggable Bézier handles, four accessible numeric controls, fixed endpoints, invert time coordinate before evaluating progress | Handles stay inside0–1; no overshoot. Splits retain the original curve interval and handles. A split must leave enough clip duration to preserve neighboring transitions. |
| Imported LUT looks | Normalized SDR Rec.7093D .cube parser, adjustable strength, original file retained with the project |2–33samples/channel,4MB input, complete finite0–1data and normalized domains. Red-fastest .cube order is mapped to Media3's RGB cube order; encoded SDR lookup is resampled into the engine's linear BT.709 cube.8-bit LUT texture/interpolation; not a Log/HDR grading pipeline. LUT files travel in portable backups. |
| Pitch | Sonic pitch processing on mixed audio tracks, from−12to+12semitones | Independent of track speed; source sound can be extracted to an audio track first. Codec/A-V synchronization acceptance requires a phone. |
| Noise cleanup | Adjustable adaptive three-band expansion and optional80Hz rumble filter | Conventional DSP, not voice isolation or spectral source separation. It can soften quiet material; use a low amount and compare. Channel state persists across buffers and resets per stream. |
| Beat markers | On-device audio decode and bounded energy-onset detection; visible timeline dots, previous/next marker navigation | First10minutes of the chosen audio source span,120-second analysis budget, maximum5,000markers. Speed curves and track offset map source onsets to project times. Approximate onsets, not a guaranteed tempo grid; no automatic splitting. New analysis replaces old markers with Undo available. Later edits leave markers at their project times; shortening removes out-of-range markers. |
| Editing proxies | Explicit360p/30fpsH.264/AAC source proxy, lower360/540/720pcomposited previews, automatic original fallback | Source up to30minutes,1GB project cache, free-space reserve. Keyed by original private filename/length/mtime/source duration; only completed timing-checked proxies are selected. Exports and portable backups always use original media. Cache can be cleared without changing project edits. |

Transition/caption/split/seek/trim/export code uses the same overlap-adjusted clip starts. Imported subtitles partition ownership at the next clip's start, so the same cue is not duplicated during a transition. User-authored overlapping titles remain intentional layers. Overlay and alternating main sequences are padded through the project end, allowing the compositor's primary input to supply the complete timeline.

## Deferred deliberately

**Reverse rendering** and **advanced overlay blend modes** remain unavailable. Reverse playback needs a bounded reverse-decoding/rendering pipeline, a decision about reversed audio, cache invalidation and phone codec tests. Arbitrary blend modes need access to the destination frame and a different compositor, rather than another single-input color effect. Adding unqualified versions now would compromise preview/export consistency. There are no nonfunctional buttons for either feature. Existing overlay alpha, chroma key and masks remain usable.

Traditional stabilization/tracking, arbitrary track counts, optical flow, asset marketplaces, proxies beyond the stated source/cache bounds and professional HDR output are also outside this RC scope. Neural/cloud functionality remains excluded by the user.

## RC qualification

See `video-rc-verification.json` and `docs/evidence/video-rc/` for actual executed results. Host tests cover timing, backwards-compatible JSON, composition configuration, real PCM processing, approximate onset detection, LUT ordering/strength, source-vs-proxy routing, portable source copies and native-rendered UI. EGL/GLES software rendering checks the transition expressions against the installed compositor's blend convention; it is not an Android driver test.

Before declaring the APK final: verify Samsung preview and crossfade/wipe export, two-decoder overlap load, actual mixed-audio timing/pitch/noise, voice recording, HDR-input tone mapping, proxy generation/cancel/cache fallback, provider output/cancel, large/long/portrait projects and accessibility/landscape on the device. Current native libraries,25signed apps, game pack, original signer and packaging-cache refresh rules must be retained for assembly. APK verification must check the original package/signature, new version,16KB alignment and all packaged assets. Full CapCut parity and professional handset qualification are not claimed.

The installed compositor was inspected rather than assuming an alpha convention. It uses straight RGB with `SRC_ALPHA/ONE_MINUS_SRC_ALPHA` RGB blending and separate alpha blending. Both transition and chroma/mask effects now preserve RGB while modifying alpha, correcting the earlier dark-edge risk. Final pixel tests include the installed blend factors and a25%red-over-blue composite; preliminary premultiplied-alpha evidence is retained but not counted as final validation.

## Suggested handset RC smoke sequence

1. Use a new project with two short real videos and a photo. Add a0.5second crossfade, then try each wipe. Confirm the project duration subtracts the overlap, seeking/trim/split stay aligned, and the exported transition matches playback.
2. Import a normalized17or33point SDR .cube, vary strength, remove it, undo/redo, and reopen the project. Back up and restore the project to confirm the imported look and original media travel with it.
3. Draw a custom easing curve on a moving/zooming clip. Split outside the transition guard and compare motion at the join in preview and export.
4. Extract audio or add a real music/narration source. Compare pitch changes, low noise cleanup and rumble removal. Check A/V sync at the beginning and end. Detect approximate beats and use previous/next navigation.
5. Create a proxy from a supported high-resolution source. Compare360/540/720preview, background/cancel a proxy build, clear cache and confirm original fallback. Export again at the original requested output quality.
6. Export720p/30fps first, then the phone's supported higher settings. Check cancellation, a real document provider, HDR-input tone mapping, rotation/aspect changes and long-project memory/decoder behavior.
7. Check voiceover approval/save/discard, screen backgrounding, landscape and TalkBack actions. These steps remain device acceptance work, not already-passed evidence.
