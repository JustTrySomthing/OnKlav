# SMS and MP3 implementation

Transient negative SMS callbacks settle for eight seconds in the durable receipt journal. A successful same-part callback wins; confirmed errors remain actionable. No message is resent by settlement. Hard radio/service/permission failures retain immediate reporting. Real handset carrier acceptance remains required.

The shared native Android SDK exposes Mp3Codec.decode, encode, isMp3 and mime. Bundled LAME/MPGLib Java engine decodes MPEG-1/2/2.5 Layer III and creates MP3 without cloud services. Audio Editor imports, edits pitch/tempo and exports MP3. Video Editor normalizes imported MP3 soundtracks; framework playback remains primary with authorized local MP3 software fallback in shared SDK playback and message attachments. Existing Wasm media.play uses those playback paths; no new Wasm encoding effect is claimed.

Fallback conversion is bounded to two hours and available disk space. Protected, malformed or unsupported inputs report failure. Existing policy, revocation, opaque file handles and source retention apply.

Independent fixtures generated with ffmpeg sine=frequency=440:duration=0.4:sample_rate=RATE, libmp3lame: stereo44100 VBR quality4, mono22050 48k, mono11025 24k. Validation covers roundtrip, independent fixtures, pitch/export, SDK revocation and SMS receipts. Short-file decoder draining and a bounded MPEG-2.5 Xing metadata adapter correct upstream wrapper edge cases. Upstream source/license and relinking instructions are in third_party/jump3r.

Validation completed:28focused app checks plus14shared checks, full lint with no errors, and original-signature ARM64 APK verification. See sms-mp3-verification.json and evidence/v0.100.10.
