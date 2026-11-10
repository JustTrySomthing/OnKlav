# Jump3r 1.0.5 — bundled MP3 encoder/decoder

Upstream: https://git.iem.at/sciss/jump3r
Maven: https://repo.maven.apache.org/maven2/de/sciss/jump3r/1.0.5/
LAME project, Ken Händel, Vadym Vikulin, Hanns Holger Rutz and the authors named in the source headers. LGPL v2.1 or later; see COPYING.txt and the complete unmodified source archive here.

The Android runtime jar retains upstream classes byte-for-byte, excluding `de/sciss/jump3r/lowlevel/` desktop wrappers. In particular the desktop LameEncoder wrapper depends on javax.sound.sampled, which Android does not provide. OnKlav's Kotlin Mp3Codec wires the unmodified engine APIs directly, preserves PCM channels and bounds processing. No JNI/native binary or external codec/player is required for file encoding/decoding.

To reproduce: download jump3r-1.0.5.jar from the pinned Maven URL, copy ZIP entries other than de/sciss/jump3r/lowlevel/ to android/shared/libs/jump3r-1.0.5-core.jar. No class bytes are edited. Source archive and runtime hashes are in docs/vendor-dependencies.json. The library may be replaced/relinked and the application rebuilt with Gradle; no restrictions against debugging such modifications are added. Standard developer builds can use the developer's own signing identity; official updates retain the original project's signer.
