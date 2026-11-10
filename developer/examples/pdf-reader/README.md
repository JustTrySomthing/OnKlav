# Shared PDF reader sample

The app stores a synthetic PDF as an owned private blob and supplies its handle
to `pdf::reader`. A real app imports a selected file using the standard blob
picker. On Android, the native component provides pages, zoom, search, text view,
point selection and page-position storage inside OnKlav. Clipboard copying needs
the separate declared/user-approved `clipboard.write` grant.

`python3 developer/onklav.py verify developer/examples/pdf-reader` checks package,
private storage and widget/back integration through the real Rust broker. Desktop
verification does not render a PDF or fabricate native search/selection replies.
The fixture/key/package used by verify are disposable; no APK/phone installation.
A matching working Android host is required. Search and selection need Android15+;
older supported Android versions provide raster pages and explicit text-tool limits.
