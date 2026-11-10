# eBooks

A separate signed OnKlav SDK app (`app.onklav.ebooks`) included in the only Android
host APK. Read Aloud and AudioBooks remain separate included apps. eBooks composes
the existing public EPUB/reading/collections/blob services with `ebooks.search`
and `ebooks.acquire`, scoped by the new revocable `ebooks.library` capability.
Imports use the shared OnKlav Files picker and copy the selected EPUB; originals are kept.

The text-first reader supports reflowable DRM-free EPUB2/3, chapters/contents,
whole-book literal search, selectable text, text size14–28sp, page/chapter resume
and bookmarks. Images are represented by their alternative descriptions; fixed
page layout, rich CSS, PDF/MOBI/KFX and DRM unlocking are not supported. The existing
bounded EPUB profile supports disk-backed archives and bounded chapter text; the former 20MiB acquisition cap is removed. It rejects unsafe paths, symlinks, scripts and encrypted/font-obfuscated
publications. These are format/resource limits, not a paid tier or usage quota.

The speaker is the same outline speaker and activity animation as Messages.
It uses the same offline speech controller and shared `reader` voice/rate settings.
Pressing it starts read-through across pages/chapters, saving page/chapter position
through the existing reader. Pause/Stop, leaving the reader, revocation or audio
focus loss stops speech. Built-in reading continues through the shared foreground media service when the screen sleeps. External SDK readers remain foreground-scoped. Voices come from the single included Nano model; no Android TTS setup is needed. A speaker completion callback
is bound to the live rendered speech node/session and executes as a non-gesture;
it cannot silently authorize new external/network/user-consent actions.

Project Gutenberg discovery uses its documented OPDS XML interface, one page per
user action,15-minute catalog cache and an identifying contact User-Agent. No
API signup/key, subscription, credentials, private backend or app listening quota.
Acquisition first fetches the selected numeric ID's current OPDS entry, then follows
its EPUB acquisition link; it does not guess permanent file URLs or scrape HTML.
Only Project Gutenberg HTTPS hosts, public DNS, checked redirects and bounded
responses are allowed. Download chunks commit to the app's own private blob; the
EPUB validates before success. Failed replies/closed sessions remove incomplete
transfers. Existing books are preserved. Provider availability/restrictions apply;
Project Gutenberg says its XML OPDS feed is expected to change in2027, so revisit
that integration then. Official sources:
https://www.gutenberg.org/ebooks/offline_catalogs.html
https://www.gutenberg.org/policy/terms_of_use.html

The library uses pages of40 records with title sorting/filtering on the visible
page. Bookmarks use pages of20. Saved positions are page-level rather than an
exact spoken-word offset. Collection revision conflicts retain the current page
and require explicit review/retry; no blind overwrite loop.
