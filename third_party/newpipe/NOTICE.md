# URL extraction dependencies

NewPipe Extractor v0.26.4, Team NewPipe, GPL-3.0-or-later.
Upstream: https://github.com/TeamNewPipe/NewPipeExtractor/tree/v0.26.4
The unmodified corresponding source JAR and GPL text accompany this checkpoint.
Combined builds including this library are subject to GPL-3.0-or-later; preserve
the license, notices and access to complete corresponding source when distributing.

Bundled runtime dependencies (unmodified upstream artifacts):

* NanoJSON e9d656ddb49a412a5a0a5d5ef20ca7ef09549996, The nanojson Authors, Apache-2.0.
  https://github.com/TeamNewPipe/nanojson/tree/e9d656ddb49a412a5a0a5d5ef20ca7ef09549996
* jsoup 1.22.2, Jonathan Hedley, MIT. https://jsoup.org/license
* Protobuf Java Lite 4.35.1, Google, BSD-3-Clause.
  https://github.com/protocolbuffers/protobuf/tree/v35.1
* Rhino 1.8.1, Mozilla contributors, MPL-2.0.
  https://github.com/mozilla/rhino/tree/Rhino1_8_1_Release

JAR hashes are recorded in dependencies.json. The host adapter is Kotlin and
uses OkHttp; it does not embed Python, launch another app or use a remote
download-conversion service. No WebView cookies or account credentials are forwarded. Extractor-generated
consent cookies remain local to the extraction request.

OnKlav uses Rhino directly through NewPipe JavaScript utilities. The unused JSR-223 rhino-engine adapter is not distributed.
