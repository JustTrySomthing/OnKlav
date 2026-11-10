# Offline EPUB text reader sample

This SDK app stores the synthetic `docs/fixtures/text-profile.epub` in its own
private blob store and opens it through `epub_ui::reader`. Real applications use
`blobs::import` and pass the selected private handle to the same component.

The scenario covers TOC/back navigation, cross-chapter search, Unicode match
positions, chapter/page persistence and process-session reopen through the actual
signed Rust broker. Run `python3 developer/onklav.py verify developer/examples/epub-reader`
from the source root. Its signer/package are disposable; no APK or phone install.
This is a bounded offline text-reflow profile, not full EPUB visual conformance.
