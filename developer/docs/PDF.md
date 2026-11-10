# Shared native PDF viewer (working source)

Use `pdf::viewer(widget_id,private_blob_id)` or the full `pdf::reader` controller.
The `pdf_viewer` widget requires `storage.private`, a single main-body placement
and a non-scrolling outer frame. It fills the reserved body and provides its own
bounded scroll/pan viewport. It never receives a caller-supplied Android URI/path.
A matching working host is required; this does not update any bundled signed app.

The Rust broker resolves only a visible enabled node and an immutable blob owned
by the current signed app/session. Kotlin rechecks that owner/version/file before
work and before accepting results. The Android worker receives only read-only
source and output-pipe descriptors. Its manifest service is non-exported and
`isolatedProcess=true`; it does not inherit app permissions or run guest code.
No PDF write, link launch, form submission or external viewer activity is called.

## Viewer behavior

- Read-only page rendering, previous/next,1x–2x zoom, pan and saved page position.
- Android15/API35+ provides native text search, page text and point-based selection.
  Older supported systems retain raster pages and clearly disable unavailable
  text tools. Scanned pages without a text layer cannot be searched without OCR;
  OCR is not implemented.
- Search returns at most40 results, scanning at most8 pages per batch. Continuation
  includes both page and match offset so dense pages do not lose later matches.
  Matches are highlighted on the chosen page. Matching follows the Android API.
- Long-press/drag on the image selects text; Page text is the accessible alternative
  using accessible native text with an explicit Copy action. Copy requires a fresh `clipboard.write`
  grant check; revocation never silently permits a clipboard write.
- Successful render stores the page under app, publisher and immutable blob digest.
  At most64 books per app are retained. Update/revocation/closing cancel owned
  jobs; data-retaining removal retains positions, delete-data removal clears them.

## Bounds and lifecycle

PDFs are at most50MiB and2000 pages. Page dimensions are1–20000 PDF points.
Raster output is at most2 million pixels,1600 pixels wide and4096 high; encoded
PNG output is capped at8MiB. Only one client operation runs at a time. Pages,
renderers, descriptors and output streams close lexically on success/failure.
The isolated worker has a12-second watchdog; the host waits at most15 seconds.
Backgrounding/disposal/removal cancels owner work and drops visible private data.
Native calls that cannot be interrupted are contained by the worker's own deadline.

Page text is limited to50000 UTF-16 units, selected text to20000 and highlight
geometry to128 rectangles (16 per match). Invalid geometry, indices, source paths
and stale/revoked owners fail closed. Password-protected/damaged/unsupported files
report failure; no password or document text is logged. This viewer is read-only,
not a PDF editor or a full-featured accessibility/OCR certification.

## Evidence limits

The synthetic two-page fixture is generated with ReportLab and visually checked
with Poppler. Isolated Android checks compile actual production viewer/service
code against the real shared controls; backend-seam tests verify protocol bounds,
resource cleanup, dense-search continuation, source ownership and saved positions.
They do not assert that Android PDFium ran on the host. The real Rust broker checks
node/owner/grant binding. Desktop preview labels the viewer as device-backed and
does not fabricate raster/search results. Actual Android rendering, worker IPC,
watchdog/process death, URI/provider/OEM behavior and TalkBack/layout acceptance
remain physical-device/integrated-host qualification tasks.

Platform references: [PdfRenderer](https://developer.android.com/reference/android/graphics/pdf/PdfRenderer)
and [PdfRenderer.Page](https://developer.android.com/reference/android/graphics/pdf/PdfRenderer.Page).
Current source evidence: `docs/pdf-verification.json`. No APK or SDK release.
