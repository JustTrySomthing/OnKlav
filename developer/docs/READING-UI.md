# Shared text reader (working source)

`reading_ui::reader(title,input,content_id,revision,text)` is a read-only SDK
screen using the existing host theme, controls, centered confirmations and
scroll memory. It reuses `reading::{page,find,bookmark,resume}`; it is not an
EPUB/PDF parser or a replacement for the editable Read Aloud app.

- Text is bounded to1MiB; each page contains at most4000 Unicode scalar values.
- Search is literal and case-sensitive, returning at most40 matches at a time.
  More matches continues after the last match. UTF-8 ranges are checked again
  before selecting a result; no partial code point is used as a reading offset.
- The parent supplies a stable1–64-character ASCII record key and an immutable
  content revision (for example a digest). A different revision resets position;
  never reuse a revision after changing the text.
- Page turns and selected matches save their starting byte offset through the
  app-private `reading_positions_v1` collection, with expected revision/schema.
  Position means page start, not an exact on-screen pixel or speech caret.
- On reopening, only a matching content/revision and valid Unicode boundary is
  resumed. No complete document is duplicated into component state or storage.
- Save failure retains the current page and offers explicit retry. A conflict
  reloads metadata and asks before replacing another session's saved position.
  No blind write retry occurs. Back confirms leaving an unsaved position.
- `storage.private` must be declared/granted for position storage. If unavailable,
  text remains readable and the UI reports that position saving is unavailable.

Reserve `reading_positions_v1` for this component. The parent can store source
text via its chosen file/collection service and pass it into the controller.
Standard app-data removal/retention policy applies to this app-private collection.
The public wrapper in `developer/examples/text-reader` is executable through the
real signed Rust broker; its scenario verifies persistent reopen, Unicode search
and result navigation. No Android service reply is simulated by that scenario.

This working component does not update a bundled signed app or produce an APK.
Actual phone layout, TalkBack, large text and source-matched full Android host
integration remain qualification tasks. EPUB/PDF integration is chunks29–30.
