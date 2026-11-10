# Docu View and screen-light color fade

Source changes, October 30, 2026. No APK built; phone/renderer/real-world document qualification is still pending.

Docu View is an included native host app, not a second APK or office editing suite. It opens explicitly selected files through OnKlav Files. Supported document opens in Files/SDK files.open route to the same viewer. Other media and unsupported files retain their existing opening behavior. No new permission or third-party upload service is added.

## Reading behavior

| Input | View |
| --- | --- |
| PDF | Original pages through the existing isolated Android PDF service; previous/next, page jump, pinch zoom, drag pan, double-tap fit, accessible zoom buttons. |
| DOCX | Existing Write importer and spans, split into lazily laid-out selectable reading blocks. |
| DOC | Apache POI HWPF, text reading view. |
| TXT | Existing BOM-aware text decoder; selectable reading blocks. |
| RTF | Bounded group/control-word reader; Unicode, escaped bytes, paragraphs and visible field results. No images, hidden destinations or instructions. |
| ODT / ODF | OpenDocument content; paragraphs/spacing or formula text. ODF formula notation is linear reading text, not mathematical typesetting. |
| ODS | Visible table values with sheet navigation and repeated-cell/row expansion bounds. |
| XLSX | Sheet relationships, shared/inline strings, sparse cell coordinates, cached values. Hidden sheets are omitted; dates can appear as stored serials. |
| XLS | Apache POI HSSF, displayed cached cell values; hidden sheets omitted. |
| CSV / TSV | Quoted delimiters, doubled quotes, multiline cells and line endings; a scrollable table. Formula-like strings remain inert text. |

Documents/spreadsheets provide Find, text size controls and selectable text. Sheets scroll both ways, have column letters/row numbers, sheet chips and full cell-value dialogs. Non-PDF documents are a reading view: complex layout, images and embedded objects can be omitted. Spreadsheets do not recalculate formulas, execute macros, fetch external data, or show charts. Notices/details explain these boundaries in the UI. Password-protected Office/PDF files need an unprotected copy; no password bypass is attempted.

PDF text/search use existing Android 15+ platform support. Earlier Android can view pages, but text tools are not enabled. Scans without text layers require OCR, which is not included. Search is incremental/cancellable and highlights the matching PDF bounds. Page text can be selected/copied separately.

## Bounds and access

PDFs retain SDK limits: 50 MiB, 2,000 pages, isolated worker deadline, bounded two-megapixel raster and response bytes. Other files: 32 MiB compressed input, 500,000 displayed characters, 64 sheets, 10,000 rows, 256 columns, 100,000 materialized cells. Package readers reject DTD/entities and external/traversing sheet targets, cap entry count and expanded bytes, and never extract archive paths to disk. Macro, field, link and formula content is not executed. No WebView/remote font/script is used.

Temporary selected input is private app cache, cleaned on close and expired after a day. A selected source is revalidated before PDF operations and on return to the foreground. This is an explicit reading snapshot, not an editor or permanent document import. The original is never changed. No hidden document history or plaintext personal-content backup is introduced.

## Reuse

- `DocumentReading` / `ReadingDocument` / `ReadingSheet` provide one host document model and bounded readers.
- `ReadingContent` provides the same native reading/table UI to host modules.
- `SdkPdfClient.selected` reuses the existing isolated SDK PDF service/protocol for selected host documents. Existing guest-private PDF authorization is unchanged.
- Native modules can use `OnKlavDocumentContracts.view(context, selectedContentUri, mime)`; viewer activity is not exported. Guest SDK developers use existing authorized `files.open`; no raw path/URI is returned to a guest and no new broad capability is created.
- Existing `pdf.info`, render, text/search/select operations retain their contracts.

## Flashlight

Screen light adds **Color fade**, a continuous 30-second hue cycle. White, Warm, Red and brightness controls remain available. Colors flow through equal-brightness pastel hues without strobing. Animation runs only while the screen-light dialog is resumed; closing restores the window's previous brightness/keep-screen-on state. Color choices wrap on narrow/large-text screens.

## Third party

Only binary Office reading adds dependencies: POI/POI Scratchpad 5.5.1 and transitive Apache-licensed components. OOXML/ODF use bounded host XML readers rather than a full office-rendering engine. Exact dependency hashes/notices are under `docs/licenses/docu-view`. The tiny empty DOC test fixture is from Apache POI tag REL_5_5_1, `test-data/document/empty.doc`, under Apache 2.0 (stored as base64 text, decoded before use). Tests create their own content in copies; no user document is included.

## Verification

Nineteen unique focused host checks passed in stages: eight document readers/routing checks, four UI/real Files-to-viewer/source-loss checks, and seven existing shared PDF protocol/authorization checks. Real binary DOC and XLS, generated DOCX/ODF/XLSX, Unicode RTF, CSV quoting/sparse cells and malformed bounds are exercised. A separate JVM loader blocks desktop classes unavailable on Android and executes the actual DOC/XLS reader paths. Production Kotlin compiles; duplicate-class checking, Android Java resource merging and dependency DEX merging passed. Third-party notices are included in the app assets and source. Screens were inspected at narrow/large-text and normal sizes, in light/dark themes. Shared PDF tests exercise the existing backend seam; they do not prove PDFium rendering on a physical phone. Physical device PDF gestures/rendering, large-workbook performance and complex Office corpus acceptance remain pending. No APK was built.

Packaged in original-signer optimized APK0.100.27-rc1/code136. Exact optional desktop/OSGi shrinker exceptions and stored-numeric fallback were verified during packaging; see RELEASE-0.100.27.md. Earlier source-only statements describe the feature implementation checkpoint.
