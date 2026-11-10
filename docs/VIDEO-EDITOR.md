The current upgrade is documented in [VIDEO-MOBILE-EDITOR.md](VIDEO-MOBILE-EDITOR.md). Earlier implementation and release notes follow.

# Video Editor

Original native OnKlav timeline workspace using Android Media3 Transformer1.9.3 and the public experimental CompositionPlayer API. The signed Rust/Wasm shell uses app.onklav.videoeditor and existing host95; native rendering is dispatched through SdkAppScreen. Uses existing top-left exit navigation, theme colors and desktop vectors. No network capability or remote asset service.

Features: private named project library; auto-save and session undo/redo; video/photo imports; thumbnail clip timeline; scrub, split, duplicate, delete and reorder; trim handles/exact times;0.25–4x video speed with audio sync; photo duration; rotate/flip/zoom/pan; eased start/end zoom animation; timed styled text/emoji per clip; volume and up to4mixed sound tracks with offsets/trim/fades; brightness, contrast, saturation, hue, warmth/presets; audio/video fades through black; aspect ratios, fit/crop,480/720/1080short-edge output and24/30/60fps limits; H.264/AAC MP4 export to new document.

Input copies are private and durable, max4GB per file,100clips,4sound tracks,12hours per project; no20MB download limit. Saves are atomic, validated source paths, originals untouched. Deleting a project removes its private media copies. Import and export check space and grants, support cancellation and feedback; incomplete new exports are removed. Work stops when backgrounded. Preview uses same composition/effects as export (720p short-edge max preview). A paused composition preview refreshes after350ms without starting audio, and resumes preparation only while foregrounded. An inactive thumbnail is labeled Source so users do not mistake it for rendered edits.

This is a local editor, not a claim of CapCut parity. No cloud templates, auto-caption/transcription, AI cutout, stabilization, chroma key, arbitrary video overlay tracks, recording or overlapping crossfade transitions. Fades pass through black. Original interface and assets; no CapCut branding. Device decoder/GPU/encoder limits apply. Handset codec/export/touch/performance verification remains separate from host checks.

Seven focused checks and full lint passed (zero errors). ARM64 APK assembled/verified with original signer. See video-editor-verification.json.

## Build and acceptance

Host release0.96.0/code104; original publisher and Android identities retained. Native core/JNI unchanged. SDK shell alone does not provide the native workspace on an older APK; include this source in the next OnKlav APK. Media3 uses the declared Google Maven/Maven Central dependencies. This environment needed official artifacts seeded into a private Maven cache and a Gradle init hook because the Java resolver could not use its network route; no upstream artifact or POM was modified. Restore those derived dependencies or use normal online resolution if rebuilding after a workspace reset.

Portrait resolution uses the short edge:1080p vertical9:16 is1080×1920. Frame rate is an upper limit for source video; images are generated at the selected frame rate. Captions wrap into bounded textures. Import feedback shows copied bytes and percentage when the document provider supplies a size.

Handset acceptance still requires: import phone H.264/HEVC/HDR clips and photos; audition synchronized speed/mixed audio; render text/fades/color/rotation; export all aspect ratios/resolutions to a real Android document provider; cancel/background export and verify no partial output; reopen projects after a process restart; check touch, keyboard, audio focus and long-file performance. Host layout and PCM checks cannot qualify the phone GPU or codecs.

The test annotation dependency aligns with the Media3/Guava runtime (error_prone_annotations2.28.0). Lottie transitively brings AppCompat; Write deliberately retains its platform EditText in ComponentActivity and its own theme, with a scoped AppCompatCustomView lint exemption documenting that design. No existing Write editor behavior was changed.
