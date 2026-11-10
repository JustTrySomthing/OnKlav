# Audio Editor deep review

The review found real correctness and reliability issues beneath the recently improved interface. The fixes below are implemented and saved in source. The editor remains a single-track, file-backed editor; this review does not certify it as a complete professional studio or physically tested release.

**Scope:** AudioEditor UI/model, preview planning, DSP/rendering, PCM/WAV import, MP3/AAC export, draft/history handling, file-provider output and playback ownership. Latest delivered APK is still0.100.25-rc1/code134; it does not contain this source update. No APK was requested or built.

## Confirmed findings and implemented fixes

| Priority | Finding and consequence | Fix and evidence |
| --- | --- | --- |
| High | Import cancellation could be delivered after saving the new draft JSON, then the error path could restore the old in-memory source and delete the new PCM. That left a draft pointing at a missing file. | A small non-cancellable metadata/adoption boundary keeps source, JSON and cleanup together. Decode remains cancellable; abandoned decoded files are cleaned. Cancellation-before-adoption regression checks preserve the old draft and remove temporary files. The exact sub-millisecond post-write race was addressed by code structure, not exhaustive fault injection. |
| High | Failed export used unconditional URI deletion, and an ordinary output-stream close could tell the host provider that a partial copy completed successfully. | The export owns a file descriptor, sends closeWithError on interruption, closes the descriptor and never deletes an arbitrary destination URI. Authorization/cancellation are checked again before successful close. Tests verify an unopened destination remains intact and an interrupted descriptor closes. Reliable provider recovery behavior still needs handset/provider acceptance. |
| High | Reversing audio changed rendering/selection mapping while the waveform could retain its old direction. Selecting a region from that stale picture could target the wrong sound. | Mapping changes regenerate the waveform with cancellation of obsolete work. An asymmetric waveform regression verifies the visible direction. |
| Medium | Undo/Redo moved history stacks and changed the edit before saving; a save failure could leave the stacks and restored draft inconsistent. | History transitions happen only after a successful save; failures restore the current edit and leave both stacks intact. Permission-denial and recovery checks exercise this. |
| Medium | Fades could remain longer than the edited track after trimming or a tempo change. The slider clamped its display while rendering still used the old duration. | Model, restoration and slider limits agree with the current output duration. Trim, tempo, disk-state and Undo checks verify the same values. |
| High for fidelity | WAV import clipped valid32-bit float samples outside[-1,1] before users could reduce gain. This flattened peaks permanently inside the editable copy. | The float cache retains finite headroom; invalid nonfinite samples are sanitized. A regression imports values beyond full scale and then recovers them correctly with lower gain. Original user files remain untouched. |
| Medium for responsiveness | WAV conversion read each sample individually, creating excessive tiny disk reads. MP3 compatibility conversion also went through that WAV path. | Frame-aligned64KB buffered reads replace per-sample I/O. PCM8/16/24/32-bit, float32, stereo/MP3 round-trip and malformed-frame behavior were checked. This removes the inefficient access pattern; actual Samsung import speed was not measured. |
| Medium | Audio Editor did not handle headphone-disconnect broadcasts, so preview could continue through the phone speaker. | A preview-scoped receiver stops playback and releases resources. It unregisters on teardown and ignores stale events from a former playback token. Tests verify stop, restart and late-event isolation. |
| Medium | Original comparison inherited the default peak limiter, so it was not entirely free of sound processing. | Original comparison bypasses that limiter while retaining the structural edit/reverse order. A near-full-scale sample regression verifies the dry level. |
| Medium | Oversized/slowed exports could fail only after lengthy work. RIFF's size check also omitted the36bytes counted in its header field. | MP3's two-hour output limit is reported before rendering. WAV payload plus header is validated before reading/processing or allocating scratch output. Exact boundary tests use headers only, without giant files. |
| Medium for sound quality | The DC-removal filter used one fixed coefficient at every sample rate, unintentionally removing more low bass at higher rates. | A consistent3Hz coefficient replaces it. The dedicated80Hz rumble control stays separate. Checks verify settled DC removal and retention of20Hz content at48/96kHz; this is not an all-music perceptual certification. |
| Low | Some permission errors still named the retired Audio app; generic Continue actions were vague; MP3's192kbps caption was inaccurate at lower sample rates. | Names now say Audio Editor, confirmation buttons say Reset edits/Open audio, and the encoder caption accurately says up to192kbps. |

## UI and usability assessment

The bounded tool area, compact/focused waveform modes, visible tool choices, exact-value entry, long-press explanations and expandable status details are retained. The normal phone workflow passed again after the model/storage changes. Earlier large-text, narrow-phone, landscape and focused-waveform checks remain documented in AUDIO-EDITOR-REFINEMENTS.md; they were not all repeated for this engine audit.

Useful next refinements are a time ruler, selection handles directly on the zoomed waveform, clearer end-of-selection preview wording, visible stereo channels and live level/clipping feedback. These are not silently added in this review. Exact values currently support decimal input rather than sample-number editing.

## Important remaining technical follow-up

**Noise reduction together with pitch shifting needs a focused quality pass.** The learned noise spectrum is measured from mapped source audio, but render applies timing/pitch before denoising. With colored or tonal noise, shifted noise frequencies can stop lining up with the original profile. The processing order is confirmed from source; the magnitude of the audible problem has not been measured in this pass. A next DSP change should test denoising before pitch or a validated profile conversion using representative audio. It should not be marketed as solved by these fixes.

Pitch/tempo uses a lightweight overlap/add algorithm and interpolation. Frequency/duration/stereo checks pass, but vocals, percussion, transients and extreme settings can still need quality work. There is no formant-preservation option. Listening comparisons on speech/music/noise remain necessary before strong sound-quality claims.

High-rate AAC/M4A encoding depends on the handset codec. Broader import formats, downmixing, sample-rate choices and codec fallback/resampling deserve separate compatibility work. WAV/MP3 paths have focused host coverage; that does not qualify every Android codec/provider combination.

## Missing features worth prioritizing

| Priority | Addition | Current limitation |
| --- | --- | --- |
| 1 | Named editable projects with Open/Save and portable backup | One private autosaved draft; opening another file replaces it. Export is flattened audio. Undo/Redo is session history, not a portable project. |
| 2 | Full-track and loop-selection playback alongside fast audition | Playback currently auditions up to12seconds. A longer mode should stream/buffer work without reintroducing a full-song wait for every small change. |
| 3 | Background export with durable progress/cancel | Leaving/stopping the editor cancels its interactive work. Long exports should eventually have an explicit background task rather than depend on an open screen. |
| 4 | Peak/RMS/clipping meters, stereo lanes and a time ruler | The waveform is a mapped-source overview, not a live processed level meter or separate channel display. |
| 5 | Short crossfades/zero-crossing assistance at edits | Arbitrary cuts can click at waveform discontinuities. Whole-track fade-in/out does not smooth every internal cut. |
| 6 | Export controls for bitrate, sample rate and24-bit/float WAV | WAV output is16-bit PCM and compressed exports use that rendered intermediate. Current choices are intentionally simpler than studio export controls. |

Multitrack mixing, recording directly into the editor and automation would be larger product expansions. They should not crowd the current single-track interface unless that scope is explicitly chosen.

## Validation and practical limits

**28 unique focused checks passed:**6 editor/DSP checks,6 WAV/import/export checks,9 model/lifecycle/data-safety checks,6 preview/processing checks and1 full phone UI workflow. The initial26-case run passed. The affected WAV boundary class was rerun after its correction, and the DC/editor class after the filter change. Unchanged passes were retained rather than broad suites repeated. Final runtime classes match compiled Kotlin byte-for-byte.

Tests cover independent pitch/tempo/stereo, denoising, gain/EQ/fades/normalization, source-preserving edits, signed access, dry comparison, bounded audition, cached block reading, route interruption, draft cancellation, failed history save, destination preservation, descriptor cleanup, import depths/headroom, output limits and UI reachability. Source/native/SDK/speech/game inputs remain compatible with hash-verified reusable outputs. Only Kotlin audio paths/tests and supporting documentation changed.

This is a source/host review, not a physical Galaxy S21+ test. Actual speaker/headset/Bluetooth/call routing, device codec behavior, IME/rotation, provider publication/recovery, power-loss behavior and long-file performance remain handset acceptance. The small metadata commit is cancellation-safe by design; exhaustive crash/power-loss injection was not performed. Host waveform/sine tests cannot prove subjective music quality. No new APK or signing identity was created.
