# Call, message link and Files refinements

Files: moved Home/Create folder/List-grid into the main page header; removed duplicate breadcrumb title, diagonal parent arrow and Import action. Android storage uses Android Files as its header and its header back/Home buttons return to OnKlav Files. The protected system root is displayed as OnKlave Files (owner spelling), while native IDs/data remain unchanged.

Messages and Groups: HTTP/HTTPS and ordinary web-domain matches now receive underlined clickable Compose link ranges. Both feed routes hand off to the existing built-in browser overlay, preserving conversation return state and the separate YouTube preview route. Addresses are checked through the existing browser URL policy before callbacks are created.

Call end: teardown no longer takes a fresh snapshot from an already-destroyed Call. It uses the last published immutable snapshot, removes tracked callbacks/calls before best-effort unregister, and contains late platform notification/audio/video cleanup failures. Added a detached-details regression case. The owner's screenshot is an Android crash warning and provides no stack trace; these repairs do not establish the exact original crash cause or prove handset resolution.

Verification: inspected source wiring/labels and existing browser policy; 7 checkpoint tooling tests passed. Android SDK and prior build caches expired with the old workspace; Android compile, host regression execution and physical-device verification have NOT been performed for this refinement. Added link-range and call-teardown test cases for the next Android validation run. No APK was requested or produced.

Follow-up: source compiled and packaged successfully in 0.100.40-quick; package verification passed. Android host regressions and handset crash confirmation remain pending.
