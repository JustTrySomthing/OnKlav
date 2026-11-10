# Audio Editor mobile layout — complete

Source refinement after 0.100.25-rc1/code134. No new APK requested or built.

## Problem and change

The old fixed waveform, selection, transport and tall icon tabs consumed almost all of the editor height. Its alternate small-screen layout scrolled the entire workspace, so choosing a tool could leave the settings below the screen.

The workspace now measures its actual host allocation. Portrait reserves separate bounded areas for waveform/selection and tools, with a persistent playback/history dock. Settings get priority when height is tight. At wide landscape sizes the waveform and dock occupy the left pane and settings the right pane. The waveform and tool content scroll independently. Category tabs, three visible tool choices and transport remain reachable. Choosing another tool starts its panel at the first control; individual tool choices survive category switches. Very short viewports can require scrolling within a pane; simultaneous visibility of every setting is not promised.

Trim exposes Edit (selection operations), Fades and Reverse. Sound exposes Volume, Tone and Pitch (including tempo). Clean exposes Noise, Gate and Filters. All previous editing operations remain. The toolbar contains Open, filename/format, Save, and a More menu for Reset and Help. Time selection remains precise; select-all, waveform zoom and view position are accessible. Large-text selection times use separate unbroken lines and an inset rectangular hit area. A new file resets only waveform view; configuration recreation retains it.

All icon buttons have explicit 48dp targets; play/cancel is52dp. Tool buttons use their full width so labels stay readable at larger text sizes. Slider resets and selection/pan sliders retain48dp targets. Whole switch rows are tappable and expose switch semantics. Original-position slider marks, original-position tint and reset synchronization remain. Export and precise-selection dialogs scroll inside short windows; format choices wrap. Playback preparation has a visible Cancel action. The DSP, preview/cache, file contracts, shared audio ownership and export formats are unchanged.

## Validation

Eight unique focused checks passed: five existing AudioEditorHostTest checks and three final AudioEditorUiTest scenarios. The final UI run passes strict unclipped slider-in-viewport assertions, transport bounds/48dp targets, fresh tool scroll, reset values, switch changes, selection dialog, zoom/pan, save formats, Help and permission revocation. Test layouts reserve56dp each for host header/navigation:360×640 phone,640×360 landscape,320×640 with160% Compose workspace text. Dialog scaling on a real handset remains device acceptance.

The five retained engine/host checks cover independent pitch/tempo and stereo, learned noise reduction, nondestructive edit/round-trip, gain/tone/fades/normalization/clipping/DC and signed-app revocable access. The implementation of these operations was unchanged after that run. Their passing entries are retained in initial-run.log. No broad suite repeated. Final runtime bundle was compared byte-for-byte with compiled Kotlin. Native, signed SDK apps, speech and games remain hash-current and reusable.

Native-rendered screenshots reviewed: phone selection, pitch/trim, landscape pitch, large-text initial pitch and scrolled tempo, and export dialog. Resource entry-name assertions catch wrong icon mappings. Initial diagnostics are retained: one unsupported stock icon fixed with a vector; landscape qualifier ordering corrected; animated test scrolling needed an advancing test clock; the Compare target enlarged; a switch assertion made independent of its starting state; and restored merged resources/test tables regenerated after stale IDs were caught. These were corrected, not counted as passing checks. Only generated resource outputs were cleared; SDK/dependency caches preserved.

No new APK, physical phone touch/IME/rotation/listening, long-file performance, carrier or device acceptance is claimed. Keep the latest0.100.25 optimized packaging, selected shared voices, original signer, identities, migrations, licenses and all other completed app work.
