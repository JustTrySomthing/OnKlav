# Audio Editor final usability refinements — complete

Source update after the mobile layout refinement; latest delivered APK remains0.100.25-rc1/code134. No new APK requested/built.

## Finished behavior

- Expand waveform in the file toolbar opens a dedicated selection/waveform area. Back to tools and Android Back return without losing the selection, zoom or edit. Short portrait viewports show an explicit Waveform & selection button rather than losing access to the timeline. Very short tool panels use a labeled chooser for all nine tool groups, retaining a usable settings viewport. Tool choice can cross categories correctly.
- Each slider value has a pencil and a48dp entry button. The dialog focuses/selects the current value, shows the unit and valid range, and supports Apply, Cancel and keyboard Done. Typing does not modify the edit. Valid Apply updates the model, commits one history step and synchronizes the slider. Invalid/out-of-range/nonfinite input cannot apply. Decimal-comma paste and Unicode minus are accepted; tempo/noise percentages convert correctly. Defaults, reset and Undo/Redo remain intact. Entry state is saveable; real-device rotation restoration remains unqualified.
- Long-press labels cover file/view/save/more, selection/zoom, comparison, play/pause/cancel/stop, Undo/Redo and slider reset icons. Long-press comparison shows its meaning without starting audio. Accessibility descriptions and explicit touch targets remain.
- Error messages and overflowing nonempty statuses have an information button. It opens a selectable, scrollable snapshot of the complete message. Short ordinary statuses need no extra button. It clears after the caption changes to a short message; no error dialog appears automatically.
- File metadata is kept on one line to avoid consuming the working area at larger text sizes. Help describes the new controls.

All original editing functions, DSP/cache, shared audio ownership, authorization, file contracts, export formats, native/SDK apps, voices and games are retained. No engine or codec change.

## Verification

Eight unique focused checks passed: five Android UI scenarios and three numeric-contract cases. The retained five-case run covers ordinary360×640 phone,640×360 landscape,320×640 at160% Compose workspace text,320×480 at160%, and long-press/details interaction. The two final revised cases additionally verify Android Back from focus mode, long normal-status overflow and the disappearing details affordance. Those two were rerun alone; the other passed cases were retained. The three parser/unit-format tests were unchanged after passing.

Strict raw/unclipped slider viewport bounds,48dp transport controls, selected-tool scroll, finite values/ranges/percent conversion, Apply/Cancel/IME Done, history/reset, precise selection, zoom/pan, file formats, Help and revocation were checked. The comparison label was long-pressed without entering playback. Full error text was checked against its original string and its scroll range was exercised. Final compiled Kotlin and runtime classes match byte-for-byte. Hash-current native/SDK/speech/game outputs remain reusable.

Native-rendered workspace, tiny focus/chooser, exact-pitch, tooltip and full-error screenshots were reviewed. Initial diagnostics are retained: an unsupported stock Fullscreen icon was replaced with the existing project vector, a nested layout receiver made explicit, and the status test changed from scrolling a nonexistent parent to scrolling the actual message. Failed runs are not passing checks. No broad DSP/emulator suites repeated.

## Remaining device acceptance

No physical Galaxy S21+ is connected to this workspace. Actual touch feel, keyboard resize/dismissal, rotation/configuration restoration, speaker/Bluetooth/call audio and handset performance still need phone acceptance. Host tests use API29 and explicit window constraints/local Compose text scaling; they are not Samsung/API35 qualification. No release-ready handset claim or new APK is made. Preserve the original signer and optimized packaging for the next requested APK.
