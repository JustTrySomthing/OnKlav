# Messages input and tail visibility

The state-based message input now occupies its entire visible surface, including when empty after Send. During carrier submission it becomes read-only rather than disabled, retaining the focus/input connection. Rich IME image imports remain blocked during submission; Unicode input and existing content receiver behavior are retained.

Feed tail following tracks list insertions, the visible tail's size and available viewport changes (keyboard and composer resizing). Reading older history stops following; a successful local send returns to the latest Feed and resumes following. Carrier delivery confirmation remains independent. Search-result positioning and older-history windows retain their explicit navigation.

Focused host regression checks cover tapping the empty field's right edge, two consecutive inputs without leaving, focus retention during submission, last-message visibility after insertion/viewport shrink and history scroll preservation. Check results are recorded separately. Real Gboard/handset and carrier acceptance are not implied.

No native/SDK identity changes or APK build.

Validation: all three selected host cases passed; runtime classes match compilation. Evidence and source hashes: `docs/message-compose-verification.json`. Initial test-only compile diagnostics retained; corrected selection passed. No broad test/lint repeat or native rebuild for this Kotlin-only change.
