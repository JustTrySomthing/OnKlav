# Audio Editor preview and UI refresh

The old Play path rendered the entire edited track through timing, effects and output passes before starting audio. A single pitch change therefore required a full-track render. WSOLA similarity searches also read many overlapping input neighborhoods through a one-block disk cache, and the mapped reader repeatedly summed region lengths per sample.

The default audition now renders a maximum of 12 output seconds, starting at the playhead inside the selection. Tap another part of the waveform to audition it; Save still processes every edit over the full track. This is bounded offline auditioning, not a real-time full-track DSP engine. Preview normalization and overload protection use the excerpt; final mastering levels use the full track.

Pitch/tempo interpolation preloads a reusable grain buffer; the PCM reader reuses four nearby blocks and one read buffer. Region length is cached. Reverse audition coordinates and waveform order agree, fades retain their full-track position, and audition-local decoder seeks map back to the full timeline. Structural edits also map reversed selections correctly, and every time label follows the edited tempo. Cancelling while the decoder prepares cancels its continuation and drops listeners/focus; stale focus callbacks cannot stop a later preview. Import authorization remains checked with a bounded throttle instead of re-reading the app registry for every copied block.

The UI uses compact Open/Save actions, a larger waveform, a clearly labeled quick-preview transport, tool icons with text labels, tonal selection actions, and original-position marks/reset buttons with neutral colors at defaults. Export formats and the original source are preserved.

Fourteen unique focused DSP, model and UI checks passed. A 12-second 48 kHz stereo audition with +7 semitones and 140% tempo rendered in 0.4662 seconds on the host; the fixture used a two-hour edit timeline built from 360 repeated regions. Final runtime classes match compilation. See audio-editor-refresh-verification.json. Host timing is separate from actual phone timing/decoder/audio-route acceptance. No APK built.
