# OnKlav public release licensing review

The owner approved GPL-3.0-or-later for OnKlav-owned software in the
combined Android host, with the notice **Copyright (C) 2026 OnKlav**.
The [host notice](../android/app/src/main/assets/licenses/OnKlav-NOTICE.md)
and [GPL text](../android/app/src/main/assets/licenses/OnKlav-GPL-3.0.txt)
ship as Android assets. This review covers the reconstructed 0.101.49-rc1 candidate. Verify the exact release build before publishing.

The OnKlav-owned App SDK and Game SDK code, templates, examples, and
documentation use GPL-3.0-or-later. Their archives include the license
text and preserve upstream notices. The code license covers OnKlav-owned
materials. Third-party assets, data, and binaries have their own terms.

The full package preserves the APK, both SDKs, and bundled content.
Public distribution of the full package still requires rights records
for the items below whose terms remain unverified.

## Component review

| Component | Evidence in this checkpoint | Action before release |
| --- | --- | --- |
| NewPipe Extractor and vendored Java libraries | `third_party/newpipe/NOTICE.md`, pinned 0.26.4 source JAR, licenses and dependency hashes | Include notices and complete corresponding source for the covered binaries and combined distribution. Verify the source JAR and upstream build inputs against the exact release. |
| Native speech and English voice pack | `third_party/speech/sources.json`, pinned Sherpa, Piper and eSpeak archives, `docs/speech-dependencies.json` and voice provenance | Retain source and license files. Check the exact linked dependency and build inputs, including eSpeak data and model/voice notices. |
| Jump3r | `third_party/jump3r` contains the LGPL-2.1 text, notice, source JAR and recipe for the trimmed runtime JAR | Verify the distribution's applicable LGPL requirements, including the permitted replacement or relinking method, before publishing. |
| Patched Godot Web runtime and export template | `developer/games/licenses`, `docs/godot-web-engine.json` and `third_party/godot-web-fixes` | Retain Godot and bundled dependency notices. Publish the pinned build recipe and patch with the source. Verify the APK and Game SDK notices independently. |
| Other Maven and Rust dependencies | Gradle declarations, Cargo lockfiles, vendor hashes and `docs/licenses/rust` | Inventory the resolved release dependencies, preserve attribution, and check required source and notices against the actual APK. |
| Soundtracks, artwork, icons and fonts | Game/audio provenance, catalog artwork notes and VSCO/Groove credits | The Panda credits identify the original project, music and artwork, and the Shroom source identifies Below the Mycelium. The Neon Dominos notes identify its original Afterglow score. Keep these credits and record distribution terms for each final asset, including sample sources and any catalog art without a license record. |
| Offline catalogs and dictionary | Podcast Index MIT notice, radio import sources and the Write dictionary copyright file | The Maps asset is a compact GeoNames city and US ZIP derivative. GeoNames provides CC BY data and Maps includes attribution. Keep source and modification details with the bundled extract. OSM tiles are requested at runtime and carry ODbL attribution. The starter catalog contains 37,137 Radio Browser records. The API permits mirroring. The 610 Icecast records were excluded from the bundled snapshot pending redistribution terms. Live refresh can still load Icecast. Station metadata and audio-stream rights are separate. |

Authoring tools and test fixtures can have different terms from the final APK.
Publish an exact source tag and the applicable corresponding source together
with the APK. Include lockfiles, vendored source, patches and build scripts
required for that distribution. Keep signing keys and credentials private.

## APK preparation

The reconstruction starts with the full 0.101.36 source and applies later
OnKlav source updates through the Shroom audio and loader update. The APK
package is `app.onklav.home.prototype.rewrite`. The candidate uses version
0.101.49-rc1 and Android version code 228, above code 227 of the tested
0.101.48-rc1 APK. The recovered Android signing certificate matches that APK.
Signing material stays outside the source and distributions.

The newer source updates were incremental. The reconstruction reinstates the
shared board fitting helper and the Air Hockey difficulty setting, defaults
old Hockey saves to Normal, ports the approved Music Maker removal, and
rebuilds the Last Light and shared game packs from source. The Shroom source
and cartridge are packaged at the same version. All 103 checked embedded or
standalone game audio resources matched the tested APK.

Use JDK 17, Android SDK Platform 36, Build Tools 35, the original signer and
Godot 4.7.2 with the included export template. Run
`python3 scripts/build.py --variant optimized --online` and keep its verification
report with the exact source. Device update testing and public redistribution
clearance are separate release gates.

References:
- https://www.gnu.org/licenses/gpl-faq.en.html
- https://www.apache.org/licenses/GPL-compatibility.html
- https://developer.android.com/tools/sdkmanager
- https://source.android.com/docs/security/features/apksigning
- https://www.geonames.org/export/
- https://www.openstreetmap.org/copyright
- https://api.radio-browser.info/
